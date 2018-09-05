package org.datadog.jmxfetch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ClassCastException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import javax.security.auth.login.FailedLoginException;

import org.apache.log4j.Logger;
import org.datadog.jmxfetch.reporter.Reporter;
import org.yaml.snakeyaml.Yaml;

public class Instance {
    private final static Logger LOGGER = Logger.getLogger(Instance.class.getName());
    private final static List<String> SIMPLE_TYPES = Arrays.asList("long",
            "java.lang.String", "int", "float", "double", "java.lang.Double","java.lang.Float", "java.lang.Integer", "java.lang.Long",
            "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
            "java.lang.Object", "java.lang.Boolean", "boolean", "java.lang.Number");
    private final static List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData", "java.util.HashMap", "java.util.Map");
    private final static List<String> MULTI_TYPES = Arrays.asList("javax.management.openmbean.TabularData");
    private final static int MAX_RETURNED_METRICS = 350;
    private final static int DEFAULT_REFRESH_BEANS_PERIOD = 600;
    public static final String PROCESS_NAME_REGEX = "process_name_regex";
    public static final String ATTRIBUTE = "Attribute: ";

    private Set<ObjectName> beans;
    private LinkedList<String> beanScopes;
    private LinkedList<Configuration> configurationList = new LinkedList<Configuration>();
    private LinkedList<JMXAttribute> matchingAttributes;
    private HashSet<JMXAttribute> failingAttributes;
    private Integer refreshBeansPeriod;
    private long lastCollectionTime;
    private Integer minCollectionPeriod;
    private long lastRefreshTime;
    private LinkedHashMap<String, Object> instanceMap;
    private LinkedHashMap<String, Object> initConfig;
    private String instanceName;
    private LinkedHashMap<String, String> tags;
    private String checkName;
    private int maxReturnedMetrics;
    private boolean limitReached;
    private Connection connection;
    private AppConfig appConfig;
    private Boolean cassandraAliasing;


    public Instance(Instance instance, AppConfig appConfig) {
        this(instance.getInstanceMap() != null
                ? new LinkedHashMap<String, Object>(instance.getInstanceMap())
                        : null,
                        instance.getInitConfig() != null
                        ? new LinkedHashMap<String, Object>(instance.getInitConfig())
                                : null,
                                instance.getCheckName(),
                                appConfig);
    }

    @SuppressWarnings("unchecked")
    public Instance(LinkedHashMap<String, Object> instanceMap, LinkedHashMap<String, Object> initConfig,
            String checkName, AppConfig appConfig) {
        this.appConfig = appConfig;
        this.instanceMap = instanceMap != null ? new LinkedHashMap<String, Object>(instanceMap) : null;
        this.initConfig = initConfig != null ? new LinkedHashMap<String, Object>(initConfig) : null;
        this.instanceName = (String) instanceMap.get("name");
        this.tags = getTagsMap(instanceMap.get("tags"));
        this.checkName = checkName;
        this.matchingAttributes = new LinkedList<JMXAttribute>();
        this.failingAttributes = new HashSet<JMXAttribute>();
        if (appConfig.getRefreshBeansPeriod() == null) {
            this.refreshBeansPeriod = (Integer) instanceMap.get("refresh_beans");
            if (this.refreshBeansPeriod == null) {
                this.refreshBeansPeriod = DEFAULT_REFRESH_BEANS_PERIOD; // Make sure to refresh the beans list every 10 minutes
                // Useful because sometimes if the application restarts, jmxfetch might read
                // a jmxtree that is not completely initialized and would be missing some attributes
            }
        } else {
            // Allow global overrides
            this.refreshBeansPeriod = appConfig.getRefreshBeansPeriod();
        }

        this.minCollectionPeriod = (Integer) instanceMap.get("min_collection_interval");
        if (this.minCollectionPeriod == null && initConfig != null) {
        	this.minCollectionPeriod = (Integer) initConfig.get("min_collection_interval");
        }
        this.lastCollectionTime = 0;
        this.lastRefreshTime = 0;
        this.limitReached = false;
        Object maxReturnedMetrics = this.instanceMap.get("max_returned_metrics");
        if (maxReturnedMetrics == null) {
            this.maxReturnedMetrics = MAX_RETURNED_METRICS;
        } else {
            this.maxReturnedMetrics = (Integer) maxReturnedMetrics;
        }

        // Generate an instance name that will be send as a tag with the metrics
        if (this.instanceName == null) {
            if (this.instanceMap.get(PROCESS_NAME_REGEX) != null) {
                this.instanceName = this.checkName + "-" + this.instanceMap.get(PROCESS_NAME_REGEX);
            } else if (this.instanceMap.get("host") != null) {
                this.instanceName = this.checkName + "-" + this.instanceMap.get("host") + "-" + this.instanceMap.get("port");
            } else {
                LOGGER.warn("Cannot determine a unique instance name. Please define a name in your instance configuration");
                this.instanceName = this.checkName;
            }
        }

        // Alternative aliasing for CASSANDRA-4009 metrics
        // More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
        this.cassandraAliasing = (Boolean) instanceMap.get("cassandra_aliasing");
        if (this.cassandraAliasing == null){
            this.cassandraAliasing = false;
        }

        // In case the configuration to match beans is not specified in the "instance" parameter but in the initConfig one
        Object instanceConf = this.instanceMap.get("conf");
        if (instanceConf == null && this.initConfig != null) {
            instanceConf = this.initConfig.get("conf");
        }

        if (instanceConf == null) {
            LOGGER.warn("Cannot find a \"conf\" section in " + this.instanceName);
        } else {
            for (LinkedHashMap<String, Object> conf : (ArrayList<LinkedHashMap<String, Object>>) (instanceConf)) {
                configurationList.add(new Configuration(conf));
            }
        }

        loadMetricConfigFiles(appConfig, configurationList);

        ArrayList<LinkedHashMap<String, Object>> defaultConf = (ArrayList<LinkedHashMap<String, Object>>) new Yaml().load(this.getClass().getResourceAsStream("default-jmx-metrics.yaml"));
        for (LinkedHashMap<String, Object> conf : defaultConf) {
            configurationList.add(new Configuration(conf));
        }
    }

    private void loadMetricConfigFiles(AppConfig appConfig, LinkedList<Configuration> configurationList) {
        if (appConfig.getMetricConfigFiles() != null) {
            for (String fileName : appConfig.getMetricConfigFiles()) {
                String yamlPath = new File(fileName).getAbsolutePath();
                FileInputStream yamlInputStream = null;
                LOGGER.info("Reading metric config file " + yamlPath);
                try {
                    yamlInputStream = new FileInputStream(yamlPath);
                    ArrayList<LinkedHashMap<String, Object>> confs = (ArrayList<LinkedHashMap<String, Object>>) new Yaml().load(yamlInputStream);
                    for (LinkedHashMap<String, Object> conf : confs) {
                        configurationList.add(new Configuration(conf));
                    }
                } catch (FileNotFoundException e) {
                    LOGGER.warn("Cannot find metric config file " + yamlPath);
                } catch (Exception e) {
                    LOGGER.warn("Cannot parse yaml file " + yamlPath, e);
                } finally {
                    if (yamlInputStream != null) {
                        try {
                            yamlInputStream.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    /**
     * Format the instance tags defined in the YAML configuration file to a `LinkedHashMap`.
     * Supported inputs: `List`, `Map`.
     */
    private static LinkedHashMap<String, String> getTagsMap(Object tagsMap){
        try {
            // Input has `Map` format
            return (LinkedHashMap<String, String>) tagsMap;
        }
        catch (ClassCastException e){
            // Input has `List` format
            LinkedHashMap<String, String> tags = new LinkedHashMap<String, String>();

            for (String tag: (List<String>)tagsMap) {
                tags.put(tag, null);
            }

            return tags;
        }
    }

    public boolean getCanonicalRateConfig() {
        Object canonical = null;
        if (this.initConfig != null) {
            canonical = this.initConfig.get("canonical_rate");
        }

        if (canonical == null) {
            return false;
        }

        if (canonical instanceof Boolean) {
            return ((Boolean)canonical).booleanValue();
        }

        return false;
    }

    public Connection getConnection(LinkedHashMap<String, Object> connectionParams, boolean forceNewConnection) throws IOException {
        if (connection == null || !connection.isAlive()) {
            LOGGER.info("Connection closed or does not exist. Creating a new connection!");
            return ConnectionFactory.createConnection(connectionParams);
        } else if (forceNewConnection) {
                LOGGER.info("Forcing the creation of a new connection");
                connection.closeConnector();
                return ConnectionFactory.createConnection(connectionParams);
        }
        return connection;
    }

    public void init(boolean forceNewConnection) throws IOException, FailedLoginException, SecurityException {
        LOGGER.info("Trying to connect to JMX Server at " + this.toString());
        connection = getConnection(instanceMap, forceNewConnection);
        LOGGER.info("Connected to JMX Server at " + this.toString());
        this.refreshBeansList();
        this.getMatchingAttributes();
    }

    @Override
    public String toString() {
        if (this.instanceMap.get(PROCESS_NAME_REGEX) != null) {
            return "process_regex: `" + this.instanceMap.get(PROCESS_NAME_REGEX) + "`";
        } else if (this.instanceMap.get("jmx_url") != null) {
            return (String) this.instanceMap.get("jmx_url");
        } else {
            return this.instanceMap.get("host") + ":" + this.instanceMap.get("port");
        }
    }

    public LinkedList<HashMap<String, Object>> getMetrics() throws IOException {

        // We can force to refresh the bean list every x seconds in case of ephemeral beans
        // To enable this, a "refresh_beans" parameter must be specified in the yaml/json config
        if (this.refreshBeansPeriod != null && (System.currentTimeMillis() - this.lastRefreshTime) / 1000 > this.refreshBeansPeriod) {
            LOGGER.info("Refreshing bean list");
            this.refreshBeansList();
            this.getMatchingAttributes();
        }

        LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
        Iterator<JMXAttribute> it = matchingAttributes.iterator();

        // increment the lastCollectionTime
        this.lastCollectionTime = System.currentTimeMillis();

        while (it.hasNext()) {
            JMXAttribute jmxAttr = it.next();
            try {
                LinkedList<HashMap<String, Object>> jmxAttrMetrics = jmxAttr.getMetrics();
                for (HashMap<String, Object> m : jmxAttrMetrics) {
                    m.put("check_name", this.checkName);
                    metrics.add(m);
                }

                if (this.failingAttributes.contains(jmxAttr)) {
                    this.failingAttributes.remove(jmxAttr);
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.debug("Cannot get metrics for attribute: " + jmxAttr, e);
                if (this.failingAttributes.contains(jmxAttr)) {
                    LOGGER.debug("Cannot generate metrics for attribute: " + jmxAttr + " twice in a row. Removing it from the attribute list");
                    it.remove();
                } else {
                    this.failingAttributes.add(jmxAttr);
                }
            }
        }
        return metrics;
    }
    
    public boolean timeToCollect() {
    	if (this.minCollectionPeriod == null) {
    		return true;
    	} else if ((System.currentTimeMillis() - this.lastCollectionTime) / 1000 < this.minCollectionPeriod) {
    		return false;
    	} else {
    		return true;
    	}
    }

    private void getMatchingAttributes() {
        limitReached = false;
        Reporter reporter = appConfig.getReporter();
        String action = appConfig.getAction();
        boolean metricReachedDisplayed = false;

        this.matchingAttributes.clear();
        this.failingAttributes.clear();
        int metricsCount = 0;

        if (!action.equals(AppConfig.ACTION_COLLECT)) {
            reporter.displayInstanceName(this);
        }

        for (ObjectName beanName : beans) {
            if (limitReached) {
                LOGGER.debug("Limit reached");
                if (action.equals(AppConfig.ACTION_COLLECT)) {
                    break;
                }
            }
            MBeanAttributeInfo[] attributeInfos;

            try {
                // Get all the attributes for bean_name
                LOGGER.debug("Getting attributes for bean: " + beanName);
                attributeInfos = connection.getAttributesForBean(beanName);
            } catch (Exception e) {
                LOGGER.warn("Cannot get bean attributes " + e.getMessage());
                continue;
            }

            for (MBeanAttributeInfo attributeInfo : attributeInfos) {

                if (metricsCount >= maxReturnedMetrics) {
                    limitReached = true;
                    if (action.equals(AppConfig.ACTION_COLLECT)) {
                        LOGGER.warn("Maximum number of metrics reached.");
                        break;
                    } else if (!metricReachedDisplayed &&
                            !action.equals(AppConfig.ACTION_LIST_COLLECTED) &&
                            !action.equals(AppConfig.ACTION_LIST_NOT_MATCHING)) {
                        reporter.displayMetricReached();
                        metricReachedDisplayed = true;
                    }
                }
                JMXAttribute jmxAttribute;
                String attributeType = attributeInfo.getType();
                if (SIMPLE_TYPES.contains(attributeType)) {
                    LOGGER.debug(ATTRIBUTE + beanName + " : " + attributeInfo + " has attributeInfo simple type");
                    jmxAttribute = new JMXSimpleAttribute(attributeInfo, beanName, instanceName, connection, tags, cassandraAliasing);
                } else if (COMPOSED_TYPES.contains(attributeType)) {
                    LOGGER.debug(ATTRIBUTE + beanName + " : " + attributeInfo + " has attributeInfo composite type");
                    jmxAttribute = new JMXComplexAttribute(attributeInfo, beanName, instanceName, connection, tags);
                } else if (MULTI_TYPES.contains(attributeType)) {
                    LOGGER.debug(ATTRIBUTE + beanName + " : " + attributeInfo + " has attributeInfo tabular type");
                    jmxAttribute = new JMXTabularAttribute(attributeInfo, beanName, instanceName, connection, tags);
                } else {
                    try {
                        LOGGER.debug(ATTRIBUTE + beanName + " : " + attributeInfo + " has an unsupported type: " + attributeType);
                    } catch (NullPointerException e) {
                        LOGGER.warn("Caught unexpected NullPointerException");
                    }
                    continue;
                }

                // For each attribute we try it with each configuration to see if there is one that matches
                // If so, we store the attribute so metrics will be collected from it. Otherwise we discard it.
                for (Configuration conf : configurationList) {
                    try {
                        if (jmxAttribute.match(conf)) {
                            jmxAttribute.setMatchingConf(conf);
                            metricsCount += jmxAttribute.getMetricsCount();
                            this.matchingAttributes.add(jmxAttribute);

                            if (action.equals(AppConfig.ACTION_LIST_EVERYTHING) ||
                                    action.equals(AppConfig.ACTION_LIST_MATCHING) ||
                                    action.equals(AppConfig.ACTION_LIST_COLLECTED) && !limitReached ||
                                    action.equals(AppConfig.ACTION_LIST_LIMITED) && limitReached) {
                                reporter.displayMatchingAttributeName(jmxAttribute, metricsCount, maxReturnedMetrics);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while trying to match attributeInfo configuration with the Attribute: " + beanName + " : " + attributeInfo, e);
                    }
                }
                if (jmxAttribute.getMatchingConf() == null
                        && (action.equals(AppConfig.ACTION_LIST_EVERYTHING)
                                || action.equals(AppConfig.ACTION_LIST_NOT_MATCHING))) {
                    reporter.displayNonMatchingAttributeName(jmxAttribute);
                }

            }
        }
        LOGGER.info("Found " + matchingAttributes.size() + " matching attributes");
    }

    public LinkedList<String> getBeansScopes(){
        if(this.beanScopes == null){
            this.beanScopes = Configuration.getGreatestCommonScopes(configurationList);
        }
        return this.beanScopes;
    }


    /**
     * Query and refresh the instance's list of beans.
     * Limit the query scope when possible on certain actions, and fallback if necessary.
     */
    private void refreshBeansList() throws IOException {
        this.beans = new HashSet<ObjectName>();
        String action = appConfig.getAction();
        Boolean limitQueryScopes = !action.equals(AppConfig.ACTION_LIST_EVERYTHING) && !action.equals(AppConfig.ACTION_LIST_NOT_MATCHING);

        if (limitQueryScopes) {
            try {
                LinkedList<String> beanScopes = getBeansScopes();
                for (String scope : beanScopes) {
                    ObjectName name = new ObjectName(scope);
                    this.beans.addAll(connection.queryNames(name));
                }
            }
            catch (Exception e) {
                LOGGER.error("Unable to compute a common bean scope, querying all beans as a fallback", e);
            }
        }

        this.beans = (this.beans.isEmpty()) ? connection.queryNames(null): this.beans;
        this.lastRefreshTime = System.currentTimeMillis();
    }

    public String[] getServiceCheckTags() {
        List<String> tags = new ArrayList<String>();
        if (this.instanceMap.get("host") != null) {
            tags.add("jmx_server:" + this.instanceMap.get("host"));
        }
        if (this.tags != null) {
            for (Entry<String, String> e : this.tags.entrySet()) {
                if (e.getValue()!=null){
                    tags.add(e.getKey() + ":" + e.getValue());
                } else {
                    tags.add(e.getKey());
                }
            }
        }
        tags.add("instance:" + this.instanceName);
        return tags.toArray(new String[tags.size()]);
    }

    public String getName() {
        return this.instanceName;
    }

    LinkedHashMap<String, Object> getInstanceMap() {
        return this.instanceMap;
    }

    LinkedHashMap<String, Object> getInitConfig() {
        return this.initConfig;
    }

    public String getCheckName() {
        return this.checkName;
    }

    public int getMaxNumberOfMetrics() {
        return this.maxReturnedMetrics;
    }

    public boolean isLimitReached() {
        return this.limitReached;
    }

    public void cleanUp() {
        this.appConfig = null;
        if (connection != null) {
            connection.closeConnector();
        }
    }
}
