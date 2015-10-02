package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import javax.security.auth.login.FailedLoginException;

import org.apache.log4j.Logger;
import org.datadog.jmxfetch.reporter.Reporter;

public class Instance {
    private final static Logger LOGGER = Logger.getLogger(Instance.class.getName());
    private final static List<String> SIMPLE_TYPES = Arrays.asList("long",
            "java.lang.String", "int", "double", "java.lang.Double", "java.lang.Integer", "java.lang.Long",
            "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
            "java.lang.Object", "java.lang.Boolean", "boolean", "java.lang.Number");
    private final static List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData", "java.util.HashMap");
    private final static int MAX_RETURNED_METRICS = 350;
    private final static int DEFAULT_REFRESH_BEANS_PERIOD = 600;

    private Set<ObjectName> beans;
    private LinkedList<String> beanScopes;
    private LinkedList<Configuration> configurationList = new LinkedList<Configuration>();
    private LinkedList<JMXAttribute> matchingAttributes;
    private HashSet<JMXAttribute> failingAttributes;
    private Integer refreshBeansPeriod;
    private long lastRefreshTime;
    private LinkedHashMap<String, Object> yaml;
    private LinkedHashMap<String, Object> initConfig;
    private String instanceName;
    private LinkedHashMap<String, String> tags;
    private String checkName;
    private int maxReturnedMetrics;
    private boolean limitReached;
    private Connection connection;
    private AppConfig appConfig;


    public Instance(Instance instance, AppConfig appConfig) {
        this(instance.getYaml() != null
                ? new LinkedHashMap<String, Object>(instance.getYaml())
                        : null,
                        instance.getInitConfig() != null
                        ? new LinkedHashMap<String, Object>(instance.getInitConfig())
                                : null,
                                instance.getCheckName(),
                                appConfig);
    }

    @SuppressWarnings("unchecked")
    public Instance(LinkedHashMap<String, Object> yamlInstance, LinkedHashMap<String, Object> initConfig,
            String checkName, AppConfig appConfig) {
        this.appConfig = appConfig;
        this.yaml = yamlInstance != null ? new LinkedHashMap<String, Object>(yamlInstance) : null;
        this.initConfig = initConfig != null ? new LinkedHashMap<String, Object>(initConfig) : null;
        this.instanceName = (String) yaml.get("name");
        this.tags = (LinkedHashMap<String, String>) yaml.get("tags");
        this.checkName = checkName;
        this.matchingAttributes = new LinkedList<JMXAttribute>();
        this.failingAttributes = new HashSet<JMXAttribute>();
        this.refreshBeansPeriod = (Integer) yaml.get("refresh_beans");
        if (this.refreshBeansPeriod == null) {
            this.refreshBeansPeriod = DEFAULT_REFRESH_BEANS_PERIOD; // Make sure to refresh the beans list every 10 minutes
            // Useful because sometimes if the application restarts, jmxfetch might read
            // a jmxtree that is not completely initialized and would be missing some attributes
        }
        this.lastRefreshTime = 0;
        this.limitReached = false;
        Object maxReturnedMetrics = this.yaml.get("max_returned_metrics");
        if (maxReturnedMetrics == null) {
            this.maxReturnedMetrics = MAX_RETURNED_METRICS;
        } else {
            this.maxReturnedMetrics = (Integer) maxReturnedMetrics;
        }

        // Generate an instance name that will be send as a tag with the metrics
        if (this.instanceName == null) {
            if (this.yaml.get("process_name_regex") == null) {
                this.instanceName = this.checkName + "-" + this.yaml.get("host") + "-" + this.yaml.get("port");
            } else {
                this.instanceName = this.checkName + "-" + this.yaml.get("process_name_regex");
            }
        }

        // In case the configuration to match beans is not specified in the "instance" parameter but in the initConfig one
        Object yamlConf = this.yaml.get("conf");
        if (yamlConf == null && this.initConfig != null) {
            yamlConf = this.initConfig.get("conf");
        }

        if (yamlConf == null) {
            LOGGER.warn("Cannot find a \"conf\" section in " + this.instanceName);
        } else {
            for (LinkedHashMap<String, Object> conf : (ArrayList<LinkedHashMap<String, Object>>) (yamlConf)) {
                configurationList.add(new Configuration(conf));
            }
        }

        // Add the configuration to get the default basic metrics from the JVM
        configurationList.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(this.getClass().getResourceAsStream("/jmx-1.yaml")).getParsedYaml()));
        configurationList.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(this.getClass().getResourceAsStream("/jmx-2.yaml")).getParsedYaml()));
    }

    public void init(boolean forceNewConnection) throws IOException, FailedLoginException, SecurityException {
        LOGGER.info("Trying to connect to JMX Server at " + this.toString());
        connection = ConnectionManager.getInstance().getConnection(yaml, forceNewConnection);
        LOGGER.info("Connected to JMX Server at " + this.toString());
        this.refreshBeansList();
        this.getMatchingAttributes();
    }

    @Override
    public String toString() {
        if (this.yaml.get("process_name_regex") != null) {
            return "process_regex: " + this.yaml.get("process_name_regex");
        } else {
            return this.yaml.get("host") + ":" + this.yaml.get("port");
        }
    }

    public LinkedList<HashMap<String, Object>> getMetrics() throws IOException {
        LOGGER.info("Getting metrics!");
        // We can force to refresh the bean list every x seconds in case of ephemeral beans
        // To enable this, a "refresh_beans" parameter must be specified in the yaml config file
        if (this.refreshBeansPeriod != null && (System.currentTimeMillis() - this.lastRefreshTime) / 1000 > this.refreshBeansPeriod) {
            LOGGER.info("Refreshing bean list");
            this.refreshBeansList();
            this.getMatchingAttributes();
        }
        LOGGER.info("No need to refresh bean list.");

        LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
        Iterator<JMXAttribute> it = matchingAttributes.iterator();

        LOGGER.info("Looping through matching JMX attributes");
        while (it.hasNext()) {
            JMXAttribute jmxAttr = it.next();
            LOGGER.info("Checking JMX attribute " + jmxAttr);
            try {
                LinkedList<HashMap<String, Object>> jmxAttrMetrics = jmxAttr.getMetrics();
                for (HashMap<String, Object> m : jmxAttrMetrics) {
                    LOGGER.info("Adding metric check " + this.checkName);
                    m.put("check_name", this.checkName);
                    metrics.add(m);
                }

                if (this.failingAttributes.contains(jmxAttr)) {
                    LOGGER.info(jmxAttr + " was a failing attribute. Removing from failing list...");
                    this.failingAttributes.remove(jmxAttr);
                }
            } catch (Exception e) {
                LOGGER.info("Cannot get metrics for attribute: " + jmxAttr, e);
                if (this.failingAttributes.contains(jmxAttr)) {
                    LOGGER.info("Cannot generate metrics for attribute: " + jmxAttr + " twice in a row. Removing it from the attribute list");
                    it.remove();
                } else {
                    this.failingAttributes.add(jmxAttr);
                }
            }
        }
        return metrics;
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
                LOGGER.info("Limit reached");
                if (action.equals(AppConfig.ACTION_COLLECT)) {
                    break;
                }
            }
            MBeanAttributeInfo[] attributeInfos;

            try {
                // Get all the attributes for bean_name
                LOGGER.info("Getting attributes for bean: " + beanName);
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
                    LOGGER.info("Attribute: " + beanName + " : " + attributeInfo + " has attributeInfo simple type");
                    jmxAttribute = new JMXSimpleAttribute(attributeInfo, beanName, instanceName, connection, tags);
                } else if (COMPOSED_TYPES.contains(attributeType)) {
                    LOGGER.info("Attribute: " + beanName + " : " + attributeInfo + " has attributeInfo complex type");
                    jmxAttribute = new JMXComplexAttribute(attributeInfo, beanName, instanceName, connection, tags);
                } else {
                    try {
                        LOGGER.info("Attribute: " + beanName + " : " + attributeInfo + " has an unsupported type: " + attributeType);
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
        Boolean limitQueryScopes = !action.equals(AppConfig.ACTION_LIST_EVERYTHING) && !action.equals(AppConfig.ACTION_LIST_EVERYTHING);

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
        if (this.yaml.get("host") != null) {
            tags.add("jmx_server:" + this.yaml.get("host"));
        }
        tags.add("instance:" + this.instanceName);
        return tags.toArray(new String[tags.size()]);
    }

    public String getName() {
        return this.instanceName;
    }

    LinkedHashMap<String, Object> getYaml() {
        return this.yaml;
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
