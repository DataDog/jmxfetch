package org.datadog.jmxfetch;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.reporter.Reporter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import javax.security.auth.login.FailedLoginException;

@Slf4j
public class Instance {
    private static final List<String> SIMPLE_TYPES =
            Arrays.asList(
                    "long",
                    "java.lang.String",
                    "int",
                    "float",
                    "double",
                    "java.lang.Double",
                    "java.lang.Float",
                    "java.lang.Integer",
                    "java.lang.Long",
                    "java.util.concurrent.atomic.AtomicInteger",
                    "java.util.concurrent.atomic.AtomicLong",
                    "java.lang.Object",
                    "java.lang.Boolean",
                    "boolean",
                    "java.lang.Number",
                    //Workaround for jasperserver, which returns attribute types as `class <type>`
                    "class java.lang.String",
                    "class java.lang.Double",
                    "class java.lang.Float",
                    "class java.lang.Integer",
                    "class java.lang.Long",
                    "class java.util.concurrent.atomic.AtomicInteger",
                    "class java.util.concurrent.atomic.AtomicLong",
                    "class java.lang.Object",
                    "class java.lang.Boolean",
                    "class java.lang.Number");
    private static final List<String> COMPOSED_TYPES =
            Arrays.asList(
                    "javax.management.openmbean.CompositeData",
                    "java.util.HashMap",
                    "java.util.Map");
    private static final List<String> MULTI_TYPES =
            Arrays.asList("javax.management.openmbean.TabularData");
    private static final int MAX_RETURNED_METRICS = 350;
    private static final int DEFAULT_REFRESH_BEANS_PERIOD = 600;
    public static final String PROCESS_NAME_REGEX = "process_name_regex";
    public static final String JVM_DIRECT = "jvm_direct";
    public static final String ATTRIBUTE = "Attribute: ";

    private static final ThreadLocal<Yaml> YAML =
        new ThreadLocal<Yaml>() {
            @Override
            public Yaml initialValue() {
                return new Yaml();
            }
        };

    private Set<ObjectName> beans;
    private List<String> beanScopes;
    private List<Configuration> configurationList = new ArrayList<Configuration>();
    private List<JmxAttribute> matchingAttributes;
    private HashSet<JmxAttribute> failingAttributes;
    private Integer refreshBeansPeriod;
    private long lastCollectionTime;
    private Integer minCollectionPeriod;
    private long lastRefreshTime;
    private Map<String, Object> instanceMap;
    private Map<String, Object> initConfig;
    private String instanceName;
    private String service;
    private Map<String, String> tags;
    private String checkName;
    private int maxReturnedMetrics;
    private boolean limitReached;
    private Connection connection;
    private AppConfig appConfig;
    private Boolean cassandraAliasing;
    private boolean emptyDefaultHostname;

    /** Constructor, instantiates Instance based of a previous instance and appConfig. */
    public Instance(Instance instance, AppConfig appConfig) {
        this(
                instance.getInstanceMap() != null
                        ? new HashMap<String, Object>(instance.getInstanceMap())
                        : null,
                instance.getInitConfig() != null
                        ? new HashMap<String, Object>(instance.getInitConfig())
                        : null,
                instance.getCheckName(),
                appConfig);
    }

    /** Default constructor, builds an Instance from the provided instance map and init configs. */
    @SuppressWarnings("unchecked")
    public Instance(
            Map<String, Object> instanceMap,
            Map<String, Object> initConfig,
            String checkName,
            AppConfig appConfig) {
        this.appConfig = appConfig;
        this.instanceMap =
                instanceMap != null ? new HashMap<String, Object>(instanceMap) : null;
        this.initConfig = initConfig != null ? new HashMap<String, Object>(initConfig) : null;
        this.instanceName = (String) instanceMap.get("name");
        this.tags = getTagsMap(instanceMap.get("tags"), appConfig);
        this.checkName = checkName;
        this.matchingAttributes = new ArrayList<JmxAttribute>();
        this.failingAttributes = new HashSet<JmxAttribute>();
        if (appConfig.getRefreshBeansPeriod() == null) {
            this.refreshBeansPeriod = (Integer) instanceMap.get("refresh_beans");
            if (this.refreshBeansPeriod == null) {
                this.refreshBeansPeriod =
                        DEFAULT_REFRESH_BEANS_PERIOD; // Make sure to refresh the beans list every
                // 10 minutes
                // Useful because sometimes if the application restarts, jmxfetch might read
                // a jmxtree that is not completely initialized and would be missing some attributes
            }
        } else {
            // Allow global overrides
            this.refreshBeansPeriod = appConfig.getRefreshBeansPeriod();
        }

        this.service = (String) instanceMap.get("service");
        if ((this.service == null || this.service.isEmpty()) && initConfig != null) {
            this.service = (String) initConfig.get("service");
        }
        if (this.service != null && !this.service.isEmpty()) {
            this.tags.put("service", this.service);
        }

        this.minCollectionPeriod = (Integer) instanceMap.get("min_collection_interval");
        if (this.minCollectionPeriod == null && initConfig != null) {
            this.minCollectionPeriod = (Integer) initConfig.get("min_collection_interval");
        }

        Object emptyDefaultHostnameObj = this.instanceMap.get("empty_default_hostname");
        this.emptyDefaultHostname =
                emptyDefaultHostnameObj != null ? (Boolean) emptyDefaultHostnameObj : false;

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
                this.instanceName =
                        this.checkName
                                + "-"
                                + this.instanceMap.get("host")
                                + "-"
                                + this.instanceMap.get("port");
            } else {
                log.warn(
                        "Cannot determine a unique instance name. "
                                + "Please define a name in your instance configuration");
                this.instanceName = this.checkName;
            }
        }

        // Alternative aliasing for CASSANDRA-4009 metrics
        // More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
        this.cassandraAliasing = (Boolean) instanceMap.get("cassandra_aliasing");
        if (this.cassandraAliasing == null) {
            this.cassandraAliasing = false;
        }

        // In case the configuration to match beans is not specified in the "instance" parameter but
        // in the initConfig one
        Object instanceConf = this.instanceMap.get("conf");
        if (instanceConf == null && this.initConfig != null) {
            instanceConf = this.initConfig.get("conf");
        }

        if (instanceConf == null) {
            log.warn("Cannot find a \"conf\" section in " + this.instanceName);
        } else {
            for (Map<String, Object> conf :
                    (List<Map<String, Object>>) (instanceConf)) {
                configurationList.add(new Configuration(conf));
            }
        }

        loadMetricConfigFiles(appConfig, configurationList);
        loadMetricConfigResources(appConfig, configurationList);

        String gcMetricConfig = "old-gc-default-jmx-metrics.yaml";

        if (this.initConfig != null) {
            Boolean newGcMetrics = (Boolean) this.initConfig.get("new_gc_metrics");
            if (newGcMetrics != null && newGcMetrics) {
                gcMetricConfig = "new-gc-default-jmx-metrics.yaml";
            }
        }

        loadDefaultConfig("default-jmx-metrics.yaml");
        loadDefaultConfig(gcMetricConfig);
    }

    public static boolean isDirectInstance(Map<String, Object> configInstance) {
        Object directInstance = configInstance.get(JVM_DIRECT);
        return directInstance instanceof Boolean && (Boolean) directInstance;
    }

    private void loadDefaultConfig(String configResourcePath) {
        List<Map<String, Object>> defaultConf =
                (List<Map<String, Object>>)
                        YAML.get().load(this.getClass().getResourceAsStream(configResourcePath));
        for (Map<String, Object> conf : defaultConf) {
            configurationList.add(new Configuration(conf));
        }
    }

    @VisibleForTesting
    static void loadMetricConfigFiles(
            AppConfig appConfig, List<Configuration> configurationList) {
        List<String> metricConfigFiles = appConfig.getMetricConfigFiles();
        if (metricConfigFiles != null && !metricConfigFiles.isEmpty()) {
            log.warn("Loading files via metricConfigFiles setting is deprecated.  Please "
                    + "migrate to using standard agent config files in the conf.d directory.");
            for (String fileName : metricConfigFiles) {
                String yamlPath = new File(fileName).getAbsolutePath();
                FileInputStream yamlInputStream = null;
                log.info("Reading metric config file " + yamlPath);
                try {
                    yamlInputStream = new FileInputStream(yamlPath);
                    List<Map<String, Object>> confs =
                            (List<Map<String, Object>>)
                                    YAML.get().load(yamlInputStream);
                    for (Map<String, Object> conf : confs) {
                        configurationList.add(new Configuration(conf));
                    }
                } catch (FileNotFoundException e) {
                    log.warn("Cannot find metric config file " + yamlPath);
                } catch (Exception e) {
                    log.warn("Cannot parse yaml file " + yamlPath, e);
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

    @VisibleForTesting
    static void loadMetricConfigResources(
            AppConfig config, List<Configuration> configurationList) {
        List<String> resourceConfigList = config.getMetricConfigResources();
        if (resourceConfigList != null) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            for (String resourceName : resourceConfigList) {
                log.info("Reading metric config resource " + resourceName);
                InputStream inputStream = classLoader.getResourceAsStream(resourceName);
                if (inputStream == null) {
                    log.warn("Cannot find metric config resource" + resourceName);
                } else {
                    try {
                        Map<String, List<Map<String, Object>>> topYaml =
                                (Map<String, List<Map<String, Object>>>)
                                        YAML.get().load(inputStream);
                        List<Map<String, Object>> jmxConf =
                                topYaml.get("jmx_metrics");
                        if (jmxConf != null) {
                            for (Map<String, Object> conf : jmxConf) {
                                configurationList.add(new Configuration(conf));
                            }
                        } else {
                            log.warn("jmx_metrics block not found in resource " + resourceName);
                        }
                    } catch (Exception e) {
                        log.warn("Cannot parse yaml resource " + resourceName, e);
                    } finally {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    /**
     * Format the instance tags defined in the YAML configuration file to a `HashMap`.
     * Supported inputs: `List`, `Map`.
     */
    private static Map<String, String> getTagsMap(Object tagsMap, AppConfig appConfig) {
        Map<String, String> tags = new HashMap<String, String>();
        if (appConfig.getGlobalTags() != null) {
            tags.putAll(appConfig.getGlobalTags());
        }
        if (tagsMap != null) {
            if (tagsMap instanceof Map) {
                tags.putAll((Map<String, String>) tagsMap);
            } else if (tagsMap instanceof List) {
                for (String tag : (List<String>) tagsMap) {
                    tags.put(tag, null);
                }
            } else {
                log.warn("Unsupported type for tagsMap: " + tagsMap.getClass().getCanonicalName());
            }
        }
        return tags;
    }

    /** Returns a boolean describing if the canonical rate config is enabled. */
    public boolean getCanonicalRateConfig() {
        Object canonical = null;
        if (this.initConfig != null) {
            canonical = this.initConfig.get("canonical_rate");
        }

        if (canonical == null) {
            return false;
        }

        if (canonical instanceof Boolean) {
            return ((Boolean) canonical).booleanValue();
        }

        return false;
    }

    /** Returns the instance connection, creates one if not already connected. */
    public Connection getConnection(
            Map<String, Object> connectionParams, boolean forceNewConnection)
            throws IOException {
        if (connection == null || !connection.isAlive()) {
            log.info(
                    "Connection closed or does not exist. "
                    + "Attempting to create a new connection...");
            return ConnectionFactory.createConnection(connectionParams);
        } else if (forceNewConnection) {
            log.info("Forcing a new connection, attempting to create...");
            connection.closeConnector();
            return ConnectionFactory.createConnection(connectionParams);
        }
        return connection;
    }

    /** Initializes the instance. May force a new connection.. */
    public void init(boolean forceNewConnection)
            throws IOException, FailedLoginException, SecurityException {
        log.info("Trying to connect to JMX Server at " + this.toString());
        connection = getConnection(instanceMap, forceNewConnection);
        log.info(
                "Trying to collect bean list for the first time for JMX Server at "
                        + this.toString());
        this.refreshBeansList();
        log.info("Connected to JMX Server at " + this.toString());
        this.getMatchingAttributes();
        log.info("Done initializing JMX Server at " + this.toString());
    }

    /** Returns a string representation for the instance. */
    @Override
    public String toString() {
        if (isDirectInstance(instanceMap)) {
            return "jvm_direct";
        } else if (this.instanceMap.get(PROCESS_NAME_REGEX) != null) {
            return "process_regex: `" + this.instanceMap.get(PROCESS_NAME_REGEX) + "`";
        } else if (this.instanceMap.get("name") != null) {
            return (String) this.instanceMap.get("name");
        } else if (this.instanceMap.get("jmx_url") != null) {
            return (String) this.instanceMap.get("jmx_url");
        } else {
            return this.instanceMap.get("host") + ":" + this.instanceMap.get("port");
        }
    }

    /** Returns a map of metrics collected. */
    public List<Metric> getMetrics() throws IOException {

        // We can force to refresh the bean list every x seconds in case of ephemeral beans
        // To enable this, a "refresh_beans" parameter must be specified in the yaml/json config
        if (this.refreshBeansPeriod != null
                && (System.currentTimeMillis() - this.lastRefreshTime) / 1000
                        > this.refreshBeansPeriod) {
            log.info("Refreshing bean list");
            this.refreshBeansList();
            this.getMatchingAttributes();
        }

        List<Metric> metrics = new ArrayList<Metric>();
        Iterator<JmxAttribute> it = matchingAttributes.iterator();

        // increment the lastCollectionTime
        this.lastCollectionTime = System.currentTimeMillis();

        while (it.hasNext()) {
            JmxAttribute jmxAttr = it.next();
            try {
                List<Metric> jmxAttrMetrics = jmxAttr.getMetrics();
                metrics.addAll(jmxAttrMetrics);
                this.failingAttributes.remove(jmxAttr);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                log.debug("Cannot get metrics for attribute: " + jmxAttr, e);
                if (this.failingAttributes.contains(jmxAttr)) {
                    log.debug(
                            "Cannot generate metrics for attribute: "
                                    + jmxAttr
                                    + " twice in a row. Removing it from the attribute list");
                    it.remove();
                } else {
                    this.failingAttributes.add(jmxAttr);
                }
            }
        }
        return metrics;
    }

    /** Returns whather or not its time to collect metrics for the instance. */
    public boolean timeToCollect() {
        if (this.minCollectionPeriod == null) {
            return true;
        } else if ((System.currentTimeMillis() - this.lastCollectionTime) / 1000
                < this.minCollectionPeriod) {
            return false;
        } else {
            return true;
        }
    }

    private void getMatchingAttributes() throws IOException {
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
                log.debug("Limit reached");
                if (action.equals(AppConfig.ACTION_COLLECT)) {
                    break;
                }
            }
            String className;
            MBeanAttributeInfo[] attributeInfos;
            try {
                log.debug("Getting class name for bean: " + beanName);
                className = connection.getClassNameForBean(beanName);

                // Get all the attributes for bean_name
                log.debug("Getting attributes for bean: " + beanName);
                attributeInfos = connection.getAttributesForBean(beanName);
            } catch (IOException e) {
                // we should not continue
                log.warn("Cannot get bean attributes or class name: " + e.getMessage());
                if (e.getMessage() == connection.CLOSED_CLIENT_CAUSE) {
                    throw e;
                }
                continue;
            } catch (Exception e) {
                log.warn("Cannot get bean attributes or class name: " + e.getMessage());
                continue;
            }

            for (MBeanAttributeInfo attributeInfo : attributeInfos) {

                if (metricsCount >= maxReturnedMetrics) {
                    limitReached = true;
                    if (action.equals(AppConfig.ACTION_COLLECT)) {
                        log.warn("Maximum number of metrics reached.");
                        break;
                    } else if (!metricReachedDisplayed
                            && !action.equals(AppConfig.ACTION_LIST_COLLECTED)
                            && !action.equals(AppConfig.ACTION_LIST_NOT_MATCHING)) {
                        reporter.displayMetricReached();
                        metricReachedDisplayed = true;
                    }
                }
                JmxAttribute jmxAttribute;
                String attributeType = attributeInfo.getType();
                if (SIMPLE_TYPES.contains(attributeType)) {
                    log.debug(
                            ATTRIBUTE
                                    + beanName
                                    + " : "
                                    + attributeInfo
                                    + " has attributeInfo simple type");
                    jmxAttribute =
                            new JmxSimpleAttribute(
                                    attributeInfo,
                                    beanName,
                                    className,
                                    instanceName,
                                    checkName,
                                    connection,
                                    tags,
                                    cassandraAliasing,
                                    emptyDefaultHostname);
                } else if (COMPOSED_TYPES.contains(attributeType)) {
                    log.debug(
                            ATTRIBUTE
                                    + beanName
                                    + " : "
                                    + attributeInfo
                                    + " has attributeInfo composite type");
                    jmxAttribute =
                            new JmxComplexAttribute(
                                    attributeInfo,
                                    beanName,
                                    className,
                                    instanceName,
                                    checkName,
                                    connection,
                                    tags,
                                    emptyDefaultHostname);
                } else if (MULTI_TYPES.contains(attributeType)) {
                    log.debug(
                            ATTRIBUTE
                                    + beanName
                                    + " : "
                                    + attributeInfo
                                    + " has attributeInfo tabular type");
                    jmxAttribute =
                            new JmxTabularAttribute(
                                    attributeInfo,
                                    beanName,
                                    className,
                                    instanceName,
                                    checkName,
                                    connection,
                                    tags,
                                    emptyDefaultHostname);
                } else {
                    try {
                        log.debug(
                                ATTRIBUTE
                                        + beanName
                                        + " : "
                                        + attributeInfo
                                        + " has an unsupported type: "
                                        + attributeType);
                    } catch (NullPointerException e) {
                        log.warn("Caught unexpected NullPointerException");
                    }
                    continue;
                }

                // For each attribute we try it with each configuration to see if there is one that
                // matches
                // If so, we store the attribute so metrics will be collected from it. Otherwise we
                // discard it.
                for (Configuration conf : configurationList) {
                    try {
                        if (jmxAttribute.match(conf)) {
                            jmxAttribute.setMatchingConf(conf);
                            metricsCount += jmxAttribute.getMetricsCount();
                            this.matchingAttributes.add(jmxAttribute);

                            if (action.equals(AppConfig.ACTION_LIST_EVERYTHING)
                                    || action.equals(AppConfig.ACTION_LIST_MATCHING)
                                    || action.equals(AppConfig.ACTION_LIST_COLLECTED)
                                            && !limitReached
                                    || action.equals(AppConfig.ACTION_LIST_LIMITED)
                                            && limitReached) {
                                reporter.displayMatchingAttributeName(
                                        jmxAttribute, metricsCount, maxReturnedMetrics);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        log.error(
                                "Error while trying to match attributeInfo configuration "
                                        + "with the Attribute: "
                                        + beanName
                                        + " : "
                                        + attributeInfo,
                                e);
                    }
                }
                if (jmxAttribute.getMatchingConf() == null
                        && (action.equals(AppConfig.ACTION_LIST_EVERYTHING)
                                || action.equals(AppConfig.ACTION_LIST_NOT_MATCHING))) {
                    reporter.displayNonMatchingAttributeName(jmxAttribute);
                }
            }
        }
        log.info("Found " + matchingAttributes.size() + " matching attributes");
    }

    /** Returns a list of strings listing the bean scopes. */
    public List<String> getBeansScopes() {
        if (this.beanScopes == null) {
            this.beanScopes = Configuration.getGreatestCommonScopes(configurationList);
        }
        return this.beanScopes;
    }

    /**
     * Query and refresh the instance's list of beans. Limit the query scope when possible on
     * certain actions, and fallback if necessary.
     */
    private void refreshBeansList() throws IOException {
        this.beans = new HashSet<ObjectName>();
        String action = appConfig.getAction();
        boolean limitQueryScopes =
                !action.equals(AppConfig.ACTION_LIST_EVERYTHING)
                        && !action.equals(AppConfig.ACTION_LIST_NOT_MATCHING);

        if (limitQueryScopes) {
            try {
                List<String> beanScopes = getBeansScopes();
                for (String scope : beanScopes) {
                    ObjectName name = new ObjectName(scope);
                    this.beans.addAll(connection.queryNames(name));
                }
            } catch (Exception e) {
                log.error(
                        "Unable to compute a common bean scope, querying all beans as a fallback",
                        e);
            }
        }

        this.beans = (this.beans.isEmpty()) ? connection.queryNames(null) : this.beans;
        this.lastRefreshTime = System.currentTimeMillis();
    }

    /** Returns a string array listing the service check tags. */
    public String[] getServiceCheckTags() {
        List<String> tags = new ArrayList<String>();
        if (this.instanceMap.get("host") != null) {
            tags.add("jmx_server:" + this.instanceMap.get("host"));
        }
        if (this.tags != null) {
            for (Entry<String, String> e : this.tags.entrySet()) {
                if (e.getValue() != null) {
                    tags.add(e.getKey() + ":" + e.getValue());
                } else {
                    tags.add(e.getKey());
                }
            }
        }
        tags.add("instance:" + this.instanceName);

        if (this.service != null && !this.service.isEmpty()) {
            tags.add("service:" + this.service);
        }

        if (this.emptyDefaultHostname) {
            tags.add("host:");
        }
        return tags.toArray(new String[tags.size()]);
    }

    /** Returns the instance name. */
    public String getName() {
        return this.instanceName;
    }

    Map<String, Object> getInstanceMap() {
        return this.instanceMap;
    }

    Map<String, Object> getInitConfig() {
        return this.initConfig;
    }

    /** Returns the check name. */
    public String getCheckName() {
        return this.checkName;
    }

    /** Returns the maximum number of metrics an instance may collect. */
    public int getMaxNumberOfMetrics() {
        return this.maxReturnedMetrics;
    }

    /** Returns whether or not the instance has reached the maximum bean collection limit. */
    public boolean isLimitReached() {
        return this.limitReached;
    }

    /** Clean up config and close connection. */
    public void cleanUp() {
        this.appConfig = null;
        if (connection != null) {
            connection.closeConnector();
        }
    }

    /**
     * Asynchronoush cleanup of instance, including connection.
     * */
    public void cleanUpAsync() {
        class AsyncCleaner implements Runnable {
            Instance instance;

            AsyncCleaner(Instance instance) {
                this.instance = instance;
            }

            @Override
            public void run() {
                instance.appConfig = null;
                if (instance.connection != null) {
                    instance.connection.closeConnector();
                }
            }
        }

        new Thread(new AsyncCleaner(this)).start();
    }
}
