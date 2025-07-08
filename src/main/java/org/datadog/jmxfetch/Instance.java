package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.service.ConfigServiceNameProvider;
import org.datadog.jmxfetch.service.ServiceNameProvider;
import org.datadog.jmxfetch.util.InstanceTelemetry;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.security.auth.login.FailedLoginException;

@Slf4j
public class Instance {
    private static final int MAX_RETURNED_METRICS = 350;
    private static final int DEFAULT_REFRESH_BEANS_PERIOD = 600;
    public static final String PROCESS_NAME_REGEX = "process_name_regex";
    public static final String JVM_DIRECT = "jvm_direct";
    public static final String ATTRIBUTE = "Attribute: ";

    private static final ThreadLocal<Load> YAML =
        new ThreadLocal<Load>() {
            @Override
            public Load initialValue() {
                LoadSettings settings = LoadSettings.builder().build();
                return new Load(settings);
            }
        };

    private Set<ObjectName> beans;
    private List<String> beanScopes;
    private List<Configuration> configurationList = new ArrayList<Configuration>();
    private List<JmxAttribute> matchingAttributes;
    private HashSet<JmxAttribute> failingAttributes;
    private Integer initialRefreshBeansPeriod;
    private Integer refreshBeansPeriod;
    private long lastCollectionTime;
    private Integer minCollectionPeriod;
    private long initialRefreshTime;
    private long lastRefreshTime;
    private Map<String, Object> instanceMap;
    private Map<String, Object> initConfig;
    private String instanceName;
    private ServiceNameProvider serviceNameProvider;
    private Map<String, String> tags;
    private String checkName;
    private String serviceCheckPrefix;
    private int maxReturnedMetrics;
    private boolean limitReached;
    private Connection connection;
    private AppConfig appConfig;
    private Boolean cassandraAliasing;
    private boolean emptyDefaultHostname;
    private InstanceTelemetry instanceTelemetryBean;
    private ObjectName instanceTelemetryBeanName;
    private MBeanServer mbs;
    private Boolean normalizeBeanParamTags;

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
                appConfig,
                instance.serviceNameProvider);
    }

    /** Default constructor, builds an Instance from the provided instance map and init configs. */
    @SuppressWarnings("unchecked")
    public Instance(
            Map<String, Object> instanceMap,
            Map<String, Object> initConfig,
            String checkName,
            AppConfig appConfig,
            ServiceNameProvider serviceNameProvider) {
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
                // Make sure to refresh the beans list every 10 minutes
                // Useful because sometimes if the application restarts, jmxfetch might read
                // a jmxtree that is not completely initialized and would be missing some attributes
                this.refreshBeansPeriod = DEFAULT_REFRESH_BEANS_PERIOD;
            }
        } else {
            // Allow global overrides
            this.refreshBeansPeriod = appConfig.getRefreshBeansPeriod();
        }
        if (appConfig.getInitialRefreshBeansPeriod() == null) {
            this.initialRefreshBeansPeriod = (Integer) instanceMap.get("refresh_beans_initial");
            if (this.initialRefreshBeansPeriod == null) {
                // First bean refresh after initialization. Succeeding refresh controlled
                // by refresh_beans
                // Useful for Java applications that are lazy loaded and may take some time after
                // application startup before actually being exposed
                this.initialRefreshBeansPeriod = this.refreshBeansPeriod;
            }
        } else {
            // Allow global overrides
            this.initialRefreshBeansPeriod = appConfig.getInitialRefreshBeansPeriod();
        }
        if (this.initialRefreshBeansPeriod > this.refreshBeansPeriod) {
            // Set maximum equal to refresh_beans
            this.initialRefreshBeansPeriod = this.refreshBeansPeriod;
        }

        this.serviceNameProvider = new ConfigServiceNameProvider(
                instanceMap, initConfig, appConfig.getServiceNameProvider());

        this.minCollectionPeriod = (Integer) instanceMap.get("min_collection_interval");
        if (this.minCollectionPeriod == null && initConfig != null) {
            this.minCollectionPeriod = (Integer) initConfig.get("min_collection_interval");
        }

        Object emptyDefaultHostnameObj = this.instanceMap.get("empty_default_hostname");
        this.emptyDefaultHostname =
                emptyDefaultHostnameObj != null ? (Boolean) emptyDefaultHostnameObj : false;

        this.lastCollectionTime = 0;
        this.initialRefreshTime = 0;
        this.lastRefreshTime = 0;
        this.limitReached = false;
        Object maxReturnedMetrics = this.instanceMap.get("max_returned_metrics");
        if (maxReturnedMetrics == null) {
            this.maxReturnedMetrics = MAX_RETURNED_METRICS;
        } else if (maxReturnedMetrics instanceof String) {
            try {
                this.maxReturnedMetrics = Integer.parseInt((String) maxReturnedMetrics);
            } catch (NumberFormatException e) {
                log.warn(
                    "Cannot convert max_returned_metrics to integer in your instance configuration."
                    + " Defaulting to {}.", MAX_RETURNED_METRICS);
                this.maxReturnedMetrics = MAX_RETURNED_METRICS;
            }
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

        if (initConfig != null) {
            this.serviceCheckPrefix = (String) initConfig.get("service_check_prefix");
        }

        this.normalizeBeanParamTags = (Boolean) instanceMap.get("normalize_bean_param_tags");
        if (this.normalizeBeanParamTags == null) {
            this.normalizeBeanParamTags = false;
        }


        // Alternative aliasing for CASSANDRA-4009 metrics
        // More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
        this.cassandraAliasing = (Boolean) instanceMap.get("cassandra_aliasing");
        if (this.cassandraAliasing == null) {
            if (this.checkName.startsWith("cassandra")) {
                this.cassandraAliasing = true;
            } else {
                this.cassandraAliasing = false;
            }
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

        Boolean collectDefaultJvmMetrics = (Boolean) instanceMap.get("collect_default_jvm_metrics");
        if (collectDefaultJvmMetrics == null || collectDefaultJvmMetrics) {
            loadDefaultConfig("default-jmx-metrics.yaml");
            loadDefaultConfig(gcMetricConfig);
        } else {
            log.info("collect_default_jvm_metrics is false - not collecting default JVM metrics");
        }

        instanceTelemetryBean = new InstanceTelemetry();
        if (appConfig.getJmxfetchTelemetry()) {
            registerTelemetryBean(instanceTelemetryBean);
        }
    }

    private ObjectName getObjName(String domain,String instance)
            throws MalformedObjectNameException {
        return new ObjectName(domain + ":target_instance=" + ObjectName.quote(instance));
    }

    private InstanceTelemetry registerTelemetryBean(InstanceTelemetry bean) {
        mbs = ManagementFactory.getPlatformMBeanServer();
        log.debug("Created jmx bean for instance: {}", this.getCheckName());

        try {
            instanceTelemetryBeanName = getObjName(appConfig.getJmxfetchTelemetryDomain(),
                     this.getName());
        } catch (MalformedObjectNameException e) {
            log.warn(
                "Could not construct bean name for jmxfetch_telemetry_domain '{}' and name '{}'",
                appConfig.getJmxfetchTelemetryDomain(), this.getName());
            return bean;
        }

        try {
            mbs.registerMBean(bean,instanceTelemetryBeanName);
            log.debug("Successfully registered jmx bean for instance {} with ObjectName = {}",
                this.getName(), instanceTelemetryBeanName);
        } catch (InstanceAlreadyExistsException
         | MBeanRegistrationException
         | NotCompliantMBeanException e) {
            log.warn("Could not register bean named '{}' for instance: ",
                instanceTelemetryBeanName.toString(), e);
        }

        return bean;
    }


    public static boolean isDirectInstance(Map<String, Object> configInstance) {
        Object directInstance = configInstance.get(JVM_DIRECT);
        return directInstance instanceof Boolean && (Boolean) directInstance;
    }

    private void loadDefaultConfig(String configResourcePath) {
        InputStream is = this.getClass().getResourceAsStream(configResourcePath);
        List<Map<String, Object>> defaultConf = (List<Map<String, Object>>)
                YAML.get().loadFromInputStream(is);
        for (Map<String, Object> conf : defaultConf) {
            configurationList.add(new Configuration(conf));
        }
    }

    /**
     * Note that this method is only visible for testing and should not be used
     * from outside of this class.
     */
    static void loadMetricConfigFiles(
            AppConfig appConfig, List<Configuration> configurationList) {
        List<String> metricConfigFiles = appConfig.getMetricConfigFiles();
        if (metricConfigFiles != null && !metricConfigFiles.isEmpty()) {
            log.warn("Loading files via metricConfigFiles setting is deprecated.  Please "
                    + "migrate to using standard agent config files in the conf.d directory.");
            for (String fileName : metricConfigFiles) {
                String yamlPath = new File(fileName).getAbsolutePath();

                log.info("Reading metric config file " + yamlPath);
                try (BufferedInputStream yamlInputStream = new BufferedInputStream(new FileInputStream(yamlPath))){
                    List<Map<String, Object>> confs =
                            (List<Map<String, Object>>)
                                    YAML.get().loadFromInputStream(yamlInputStream);
                    for (Map<String, Object> conf : confs) {
                        configurationList.add(new Configuration(conf));
                    }
                } catch (FileNotFoundException e) {
                    log.warn("Cannot find metric config file " + yamlPath);
                } catch (Exception e) {
                    log.warn("Cannot parse yaml file " + yamlPath, e);
                }
            }
        }
    }

    /**
     * Note that this method is only visible for testing and should not be used
     * from outside of this class.
     */
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
                                        YAML.get().loadFromInputStream(inputStream);
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
            return appConfig.getConnectionFactory().createConnection(connectionParams);
        } else if (forceNewConnection) {
            log.info("Forcing a new connection, attempting to create...");
            connection.closeConnector();
            return appConfig.getConnectionFactory().createConnection(connectionParams);
        }
        return connection;
    }

    /** Initializes the instance. May force a new connection.. */
    public void init(boolean forceNewConnection)
            throws IOException, FailedLoginException, SecurityException {
        log.info("Trying to connect to JMX Server at " + this.toString());
        connection = getConnection(instanceMap, forceNewConnection);
        log.info(
                "Trying to collect bean list for the first time for JMX Server at {}", this);
        this.refreshBeansList();
        this.initialRefreshTime = this.lastRefreshTime;
        log.info("Connected to JMX Server at {} with {} beans", this, this.beans.size());
        this.getMatchingAttributes();
        log.info("Done initializing JMX Server at {}", this);
    }

    /** Returns a string representation for the instance. */
    @Override
    public String toString() {
        if (isDirectInstance(instanceMap)) {
            if (this.instanceMap.get("name") != null) {
                return "jvm_direct - name: `" + (String) this.instanceMap.get("name") + "`";
            } else {
                return "jvm_direct";
            }
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

        // In case of ephemeral beans, we can force to refresh the bean list x seconds
        // post initialization and every x seconds thereafter.
        // To enable this, a "refresh_beans_initial" and/or "refresh_beans" parameters must be
        // specified in the yaml/json config
        Integer period = (this.initialRefreshTime == this.lastRefreshTime)
            ? this.initialRefreshBeansPeriod : this.refreshBeansPeriod;

        if (isPeriodDue(this.lastRefreshTime, period)) {
            log.info("Refreshing bean list for " + this.getCheckName());
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
        if (instanceTelemetryBean != null) {
            instanceTelemetryBean.setBeansFetched(beans.size());
            instanceTelemetryBean.setTopLevelAttributeCount(matchingAttributes.size());
            instanceTelemetryBean.setMetricCount(metrics.size());
            log.debug("Updated jmx bean for instance: " + this.getCheckName()
                    + " With beans fetched = " + instanceTelemetryBean.getBeansFetched()
                    + " top attributes = " + instanceTelemetryBean.getTopLevelAttributeCount()
                    + " metrics = " + instanceTelemetryBean.getMetricCount()
                    + " wildcard domain query count = "
                    + instanceTelemetryBean.getWildcardDomainQueryCount()
                    + " bean match ratio = " + instanceTelemetryBean.getBeanMatchRatio());
        }
        return metrics;
    }

    /** Returns whether or not the given period has elapsed since reference time. */
    public boolean isPeriodDue(long refTime, Integer refPeriod) {
        if ((System.currentTimeMillis() - refTime) / 1000 < refPeriod) {
            return false;
        } else {
            return true;
        }
    }

    /** Returns whether or not its time to collect metrics for the instance. */
    public boolean timeToCollect() {
        if (this.minCollectionPeriod == null) {
            return true;
        } else {
            return isPeriodDue(this.lastCollectionTime, this.minCollectionPeriod);
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

        int beansWithAttributeMatch = 0;

        if (!action.equals(AppConfig.ACTION_COLLECT)) {
            reporter.displayInstanceName(this);
        }

        for (ObjectName beanName : this.beans) {
            boolean attributeMatched = false;
            if (limitReached) {
                log.debug("Limit reached");
                if (action.equals(AppConfig.ACTION_COLLECT)) {
                    break;
                }
            }
            String className;
            MBeanAttributeInfo[] attributeInfos;
            try {
                log.debug("Getting bean info for bean: {}", beanName);
                MBeanInfo info = connection.getMBeanInfo(beanName);

                log.debug("Getting class name for bean: {}", beanName);
                className = info.getClassName();
                log.debug("Getting attributes for bean: {}", beanName);
                attributeInfos = info.getAttributes();
            } catch (IOException e) {
                // we should not continue
                throw e;
            } catch (Exception e) {
                log.warn("Cannot get attributes or class name for bean {}: ", beanName, e);
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

                if (JmxSimpleAttribute.matchAttributeType(attributeType)) {
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
                                serviceNameProvider,
                                tags,
                                cassandraAliasing,
                                emptyDefaultHostname,
                                normalizeBeanParamTags);
                } else if (JmxComplexAttribute.matchAttributeType(attributeType)) {
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
                                serviceNameProvider,
                                tags,
                                emptyDefaultHostname,
                                normalizeBeanParamTags);
                } else if (JmxTabularAttribute.matchAttributeType(attributeType)) {
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
                                serviceNameProvider,
                                tags,
                                emptyDefaultHostname,
                                normalizeBeanParamTags);
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
                if (jmxAttribute.getMatchingConf() != null) {
                    attributeMatched = true;
                }
            }
            if (attributeMatched) {
                beansWithAttributeMatch += 1;
            }
        }
        if (instanceTelemetryBean != null) {
            instanceTelemetryBean.setBeanMatchRatio((double)
                                  beansWithAttributeMatch / beans.size());
        }
        log.info("Found {} matching attributes", matchingAttributes.size());
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
            } catch (MalformedObjectNameException e) {
                log.error("Unable to create ObjectName", e);
            } catch (IOException e) {
                log.error(
                        "Unable to query mbean server", e);
            }
        }

        if (this.beans.isEmpty()) {
            this.beans = connection.queryNames(null);
            if (instanceTelemetryBean != null) {
                int wildcardQueryCount = instanceTelemetryBean.getWildcardDomainQueryCount();
                instanceTelemetryBean.setWildcardDomainQueryCount(wildcardQueryCount + 1);
            }
        }
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

        Iterable<String> services  = this.serviceNameProvider.getServiceNames();
        if (services != null) {
            for (String service : services) {
                tags.add("service:" + service);
            }
        }

        tags.add("instance:" + this.instanceName);

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

    /** Returns the check prefix. */
    public String getServiceCheckPrefix() {
        return this.serviceCheckPrefix;
    }

    /** Returns the maximum number of metrics an instance may collect. */
    public int getMaxNumberOfMetrics() {
        return this.maxReturnedMetrics;
    }

    public InstanceTelemetry getInstanceTelemetryBean() {
        return this.instanceTelemetryBean;
    }

    /** Returns whether or not the instance has reached the maximum bean collection limit. */
    public boolean isLimitReached() {
        return this.limitReached;
    }

    private void cleanupTelemetryBean() {
        if (!appConfig.getJmxfetchTelemetry()) {
            // If telemetry is not enabled, no need to unregister the bean
            return;
        }
        try {
            mbs.unregisterMBean(instanceTelemetryBeanName);
            log.debug("Successfully unregistered bean for instance: {}", this.getCheckName());
        } catch (MBeanRegistrationException | InstanceNotFoundException e) {
            log.debug("Unable to unregister bean for instance: {}", this.getCheckName());
        }
    }

    /** Clean up config and close connection. */
    public void cleanUp() {
        cleanupTelemetryBean();
        if (connection != null) {
            connection.closeConnector();
            connection = null;
        }
    }

    /**
     * Asynchronous cleanup of instance, including connection.
     * */
    public synchronized void cleanUpAsync() {
        cleanupTelemetryBean();
        class AsyncCleaner implements Runnable {
            Connection conn;

            AsyncCleaner(Connection conn) {
                this.conn = conn;
            }

            @Override
            public void run() {
                conn.closeConnector();
            }
        }

        if (connection != null) {
            new Thread(new AsyncCleaner(connection), "jmx-closer").start();
            connection = null;
        }
    }
}
