package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.security.auth.login.FailedLoginException;

import org.apache.log4j.Logger;

public class Instance {
    private final static Logger LOGGER = Logger.getLogger(Instance.class.getName());
    private final static List<String> SIMPLE_TYPES = Arrays.asList("long", 
            "java.lang.String", "int", "double", "java.lang.Double", "java.lang.Integer", "java.lang.Long", 
            "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong", 
            "java.lang.Object", "java.lang.Boolean", "boolean", "java.lang.Number"); 
    private final static List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData", "java.util.HashMap");
    private final static int MAX_RETURNED_METRICS = 100;

    private Set<ObjectInstance> _beans;
    private LinkedList<Configuration> _configurationList = new LinkedList<Configuration>();
    private LinkedList<JMXAttribute> _matchingAttributes;
    private LinkedList<JMXAttribute> _failingAttributes;
    private Integer _refreshBeansPeriod;
    private long _lastRefreshTime;  
    private LinkedHashMap<String, Object> _yaml;
    private LinkedHashMap<String, Object> _initConfig;
    private String _instanceName;
    private String _checkName;
    private int _maxReturnedMetrics;
    private boolean _limitReached;
    private Connection _connection;
    private AppConfig config;


    @SuppressWarnings("unchecked")
    public Instance(LinkedHashMap<String, Object> yamlInstance, LinkedHashMap<String, Object> init_config, String checkName, AppConfig config) 
    {
        this.config = config;
        this._yaml = yamlInstance;
        this._initConfig = init_config;
        this._instanceName = (String) this._yaml.get("name");
        this._checkName = checkName;
        this._failingAttributes = new LinkedList<JMXAttribute>();
        this._refreshBeansPeriod = (Integer)this._yaml.get("refresh_beans");
        if (this._refreshBeansPeriod == null) {
            this._refreshBeansPeriod = 600; // Make sure to refresh the beans list every 10 minutes 
            // Useful because sometimes if the application restarts, jmxfetch might read 
            // a jmxtree that is not completely initialized and would be missing some attributes
        }
        this._lastRefreshTime = 0;
        this._limitReached = false;
        Object maxReturnedMetrics = this._yaml.get("max_returned_metrics");
        if (maxReturnedMetrics == null) {
            _maxReturnedMetrics = MAX_RETURNED_METRICS;
        } else {
            _maxReturnedMetrics = (Integer) maxReturnedMetrics;
        }

        // Generate an instance name that will be send as a tag with the metrics
        if (this._instanceName == null) {
            this._instanceName = this._checkName + "-" + this._yaml.get("host") + "-" + this._yaml.get("port");
        }

        // In case the configuration to match beans is not specified in the "instance" parameter but in the init_config one
        Object yaml_conf = this._yaml.get("conf");
        if (yaml_conf == null && this._initConfig != null) {
            yaml_conf = this._initConfig.get("conf");
        }

        if (yaml_conf == null) {
            LOGGER.warn("Cannot find a \"conf\" section in " + this._instanceName);
        } else {
            for ( LinkedHashMap<String, Object> conf : (ArrayList<LinkedHashMap<String, Object>>)(yaml_conf) ) {
                _configurationList.add(new Configuration(conf));
            }
        }

        // Add the configuration to get the default basic metrics from the JVM
        _configurationList.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(this.getClass().getResourceAsStream("/jmx-1.yaml")).getParsedYaml()));
        _configurationList.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(this.getClass().getResourceAsStream("/jmx-2.yaml")).getParsedYaml()));
    }

    public void init(boolean forceNewConnection) throws IOException, FailedLoginException, SecurityException {
        LOGGER.info("Trying to connect to JMX Server at " + this.toString());
        this._connection = ConnectionManager.getInstance().getConnection(this._yaml, forceNewConnection);
        LOGGER.info("Connected to JMX Server at " + this.toString());
        this._refreshBeansList();
        this._getMatchingAttributes();
    }

    public void init() throws IOException, FailedLoginException, SecurityException {
        init(false);
    }

    @Override
    public String toString() {
        return this._yaml.get("host") + ":" + this._yaml.get("port");
    }

    public LinkedList<HashMap<String, Object>> getMetrics() throws IOException {

        // We can force to refresh the bean list every x seconds in case of ephemeral beans
        // To enable this, a "refresh_beans" parameter must be specified in the yaml config file
        if (this._refreshBeansPeriod != null && (System.currentTimeMillis() - this._lastRefreshTime) / 1000 > this._refreshBeansPeriod) {
            LOGGER.info("Refreshing bean list");
            this._refreshBeansList();
            this._getMatchingAttributes();
        }

        LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
        Iterator<JMXAttribute> it = _matchingAttributes.iterator();

        while(it.hasNext()) {
            JMXAttribute jmxAttr = it.next();
            try {
                LinkedList<HashMap<String, Object>> attribute_metrics = jmxAttr.getMetrics();
                for (HashMap<String, Object> m : attribute_metrics) {
                    m.put("check_name", this._checkName);
                    metrics.add(m);
                }

                if(this._failingAttributes.contains(jmxAttr)) {
                    this._failingAttributes.remove(jmxAttr);
                }
            } catch (Exception e) {
                LOGGER.debug("Cannot get metrics for attribute: " + jmxAttr, e);
                if (this._failingAttributes.contains(jmxAttr)) {
                    LOGGER.debug("Cannot generate metrics for attribute: " + jmxAttr + " twice in a row. Removing it from the attribute list");
                    it.remove();
                } else {
                    this._failingAttributes.add(jmxAttr);
                }
                continue;
            }
        }
        return metrics;
    }

    private void _getMatchingAttributes() {
        Reporter reporter = config.reporter;
        String action = config.getAction();
        boolean metricReachedDisplayed = false;

        this._matchingAttributes = new LinkedList<JMXAttribute>();
        int metricsCount = 0;

        if ( !action.equals(AppConfig.ACTION_COLLECT)) {
            reporter.displayInstanceName(this);
        }

        for( ObjectInstance bean : this._beans) {
            if ( this._limitReached) {
                LOGGER.debug("Limit reached");
                if(action.equals(AppConfig.ACTION_COLLECT)){
                    break;
                }
            }
            ObjectName beanName = bean.getObjectName();    
            MBeanAttributeInfo[] atr;

            try {
                // Get all the attributes for bean_name
                LOGGER.debug("Getting attributes for bean: " + beanName);
                atr = this._connection.getAttributesForBean( beanName );
            } catch (Exception e) {
                LOGGER.warn("Cannot get bean attributes " + e.getMessage());
                continue;
            }

            for ( MBeanAttributeInfo a : atr) {

                if (  metricsCount >= this._maxReturnedMetrics ) {
                    this._limitReached = true;
                    if(action.equals(AppConfig.ACTION_COLLECT)){
                        LOGGER.warn("Maximum number of metrics reached.");
                        break;
                    } else if(!metricReachedDisplayed &&
                            !action.equals(AppConfig.ACTION_LIST_COLLECTED) &&
                            !action.equals(AppConfig.ACTION_LIST_NOT_MATCHING)) {
                        reporter.displayMetricReached();
                        metricReachedDisplayed = true;
                    }
                }
                JMXAttribute jmxAttribute;
                String attributeType = a.getType();
                if( SIMPLE_TYPES.contains(attributeType) ) {
                    LOGGER.debug("Attribute: " + beanName + " : " + a + " has a simple type");
                    jmxAttribute = new JMXSimpleAttribute(a, bean, this._instanceName, this._connection);
                } else if (COMPOSED_TYPES.contains(attributeType)) {
                    LOGGER.debug("Attribute: " + beanName + " : " + a + " has a complex type");
                    jmxAttribute = new JMXComplexAttribute(a, bean, this._instanceName, this._connection);
                } else {
                    LOGGER.debug("Attribute: " + beanName + " : " + a + " has an unsupported type: " + attributeType);
                    continue;
                }

                // For each attribute we try it with each configuration to see if there is one that matches
                // If so, we store the attribute so metrics will be collected from it. Otherwise we discard it.
                for ( Configuration conf : this._configurationList) {
                    try {
                        if ( jmxAttribute.match(conf) ) {
                            jmxAttribute.matching_conf = conf;
                            metricsCount += jmxAttribute.getMetricsCount(); 
                            this._matchingAttributes.add(jmxAttribute);

                            if (action.equals(AppConfig.ACTION_LIST_EVERYTHING) || 
                                    action.equals(AppConfig.ACTION_LIST_MATCHING) || 
                                    action.equals(AppConfig.ACTION_LIST_COLLECTED) && !this._limitReached ||
                                    action.equals(AppConfig.ACTION_LIST_LIMITED) && this._limitReached) {
                                reporter.displayMatchingAttributeName(jmxAttribute, metricsCount, this._maxReturnedMetrics);
                            }
                            break;
                        }       
                    } catch (Exception e) {
                        LOGGER.error("Error while trying to match a configuration with the Attribute: " + beanName + " : " + a, e);
                    }
                }
                if (action.equals(AppConfig.ACTION_LIST_EVERYTHING) ||
                        action.equals(AppConfig.ACTION_LIST_NOT_MATCHING)){
                    reporter.displayNonMatchingAttributeName(jmxAttribute);
                }

            }
        }
        LOGGER.info("Found " + _matchingAttributes.size() + " matching attributes");
    }

    private void _refreshBeansList() throws IOException {
        this._beans = this._connection.queryMBeans();
        this._lastRefreshTime = System.currentTimeMillis();
    }

    public String getName() {
        return this._instanceName;
    }

    public LinkedHashMap<String, Object> getYaml() {
        return this._yaml;
    }

    public LinkedHashMap<String, Object> getInitConfig() {
        return this._initConfig;
    }

    public String getCheckName() {
        return this._checkName;
    }

    public int getMaxNumberOfMetrics() {
        return this._maxReturnedMetrics;
    }

    public boolean isLimitReached() {
        return this._limitReached;
    }

}
