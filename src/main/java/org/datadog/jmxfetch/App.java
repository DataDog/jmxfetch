package org.datadog.jmxfetch;

import static org.datadog.jmxfetch.Instance.isDirectInstance;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.tasks.TaskMethod;
import org.datadog.jmxfetch.tasks.TaskProcessException;
import org.datadog.jmxfetch.tasks.TaskProcessor;
import org.datadog.jmxfetch.tasks.TaskStatusHandler;
import org.datadog.jmxfetch.util.AppTelemetry;
import org.datadog.jmxfetch.util.ByteArraySearcher;
import org.datadog.jmxfetch.util.CustomLogger;
import org.datadog.jmxfetch.util.FileHelper;
import org.datadog.jmxfetch.util.LogLevel;
import org.datadog.jmxfetch.util.MetadataHelper;
import org.datadog.jmxfetch.util.ServiceCheckHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import javax.security.auth.login.FailedLoginException;



@SuppressWarnings("unchecked")
@Slf4j
public class App {
    private static final String AUTO_DISCOVERY_PREFIX = "AD-";
    private static final String AD_CONFIG_SEP = "#### AUTO-DISCOVERY ####";
    private static final String AD_LEGACY_CONFIG_SEP = "#### SERVICE-DISCOVERY ####";
    private static final String AD_CONFIG_TERM = "#### AUTO-DISCOVERY TERM ####";
    private static final String AD_LEGACY_CONFIG_TERM = "#### SERVICE-DISCOVERY TERM ####";
    private static final int AD_MAX_NAME_LEN = 80;
    private static final int AD_MAX_MAG_INSTANCES =
            4; // 1000 instances ought to be enough for anyone
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String COLLECTION_POOL_NAME = "jmxfetch-collectionPool";
    private static final String RECOVERY_POOL_NAME = "jmxfetch-recoveryPool";

    private static final ByteArraySearcher CONFIG_TERM_SEARCHER
            = new ByteArraySearcher(App.AD_CONFIG_TERM.getBytes());
    private static final ByteArraySearcher LEGACY_CONFIG_TERM_SEARCHER
            = new ByteArraySearcher(App.AD_LEGACY_CONFIG_TERM.getBytes());

    private static int loopCounter;
    private int lastJsonConfigTs;
    private Map<String, Object> adJsonConfigs;
    private Map<String, YamlParser> configs;
    private Map<String, YamlParser> adPipeConfigs = new ConcurrentHashMap<>();
    private List<Instance> instances = new ArrayList<>();
    private Map<String, Instance> brokenInstanceMap = new ConcurrentHashMap<>();
    private AtomicBoolean reinit = new AtomicBoolean(false);

    private TaskProcessor collectionProcessor;
    private TaskProcessor recoveryProcessor;

    private final AppConfig appConfig;
    private HttpClient client;

    private AppTelemetry appTelemetry;

    /**
     * Main method for backwards compatibility in case someone is launching process by class
     * instead of by jar IE: java -classpath jmxfetch.jar org.datadog.jmxfetch.App
     */
    public static void main(final String[] args) {
        JmxFetch.main(args);
    }

    /** Application constructor. */
    public App(final AppConfig appConfig) {
        this.appConfig = appConfig;

        ExecutorService collectionThreadPool = null;
        ExecutorService recoveryThreadPool = null;
        if (!this.appConfig.isEmbedded()) { // Creates executors in standalone mode only
            collectionThreadPool = this.buildExecutorService(
                    this.appConfig.getThreadPoolSize(),
                    COLLECTION_POOL_NAME);
            recoveryThreadPool = this.buildExecutorService(
                    this.appConfig.getReconnectionThreadPoolSize(),
                    RECOVERY_POOL_NAME);
        }
        this.recoveryProcessor = new TaskProcessor(
                recoveryThreadPool, this.appConfig.getReporter());
        this.collectionProcessor = new TaskProcessor(
                collectionThreadPool, this.appConfig.getReporter());
        // setup client
        if (this.appConfig.remoteEnabled()) {
            this.client = new HttpClient(
                    this.appConfig.getIpcHost(), this.appConfig.getIpcPort(), false);
        }
        this.configs = getConfigs(this.appConfig);

        if (this.appConfig.getJmxfetchTelemetry()) {
            log.info("Enabling JMX Fetch Telemetry");
            this.initTelemetryBean();
        }
    }

    private ObjectName getAppTelemetryBeanName() {
        ObjectName appTelemetryBeanName;

        try {
            appTelemetryBeanName = new ObjectName(
                appConfig.getJmxfetchTelemetryDomain() + ":name=jmxfetch_app");
        } catch (MalformedObjectNameException e) {
            log.warn(
                "Could not construct bean name for jmxfetch_telemetry_domain"
                + " '{}' and name 'jmxfetch_app'",
                appConfig.getJmxfetchTelemetryDomain());
            return null;
        }

        return appTelemetryBeanName;
    }

    private void initTelemetryBean() {
        MBeanServer mbs =  ManagementFactory.getPlatformMBeanServer();
        AppTelemetry bean = new AppTelemetry();
        ObjectName appTelemetryBeanName = getAppTelemetryBeanName();
        if (appTelemetryBeanName == null) {
            return;
        }

        try {
            mbs.registerMBean(bean, appTelemetryBeanName);
            log.debug("Succesfully registered app telemetry bean");
        } catch (InstanceAlreadyExistsException
         | MBeanRegistrationException
         | NotCompliantMBeanException e) {
            log.warn("Could not register bean named '{}' for instance: ",
                appTelemetryBeanName, e);
        }

        this.appTelemetry = bean;
        return;
    }

    private void teardownTelemetry() {
        if (!this.appConfig.getJmxfetchTelemetry()) {
            log.debug("Skipping teardown telemetry as not enabled");
            return;
        }

        MBeanServer mbs =  ManagementFactory.getPlatformMBeanServer();
        ObjectName appTelemetryBeanName = getAppTelemetryBeanName();
        if (appTelemetryBeanName == null) {
            return;
        }

        try {
            mbs.unregisterMBean(appTelemetryBeanName);
            log.debug("Succesfully unregistered app telemetry bean");
        } catch (MBeanRegistrationException | InstanceNotFoundException e) {
            log.warn("Could not unregister bean named '{}' for instance: ",
                appTelemetryBeanName, e);
        }
    }


    /**
     * Main entry point of JMXFetch that returns integer on exit instead of calling {@code
     * System#exit}.
     */
    public int run() {
        final String action = this.appConfig.getAction();

        // The specified action is unknown
        if (!AppConfig.ACTIONS.contains(action)) {
            log.error(action + " is not in " + AppConfig.ACTIONS + ". Exiting.");
            return 1;
        }

        if (!action.equals(AppConfig.ACTION_COLLECT)
            && !(this.appConfig.isConsoleReporter() || this.appConfig.isJsonReporter())) {
            // The "list_*" actions can not be used with the statsd reporter
            log.error(action
                      + " argument can only be used with the console or json reporter. Exiting.");
            return 1;
        }

        if (action.equals(AppConfig.ACTION_LIST_JVMS)) {
            List<com.sun.tools.attach.VirtualMachineDescriptor> descriptors =
                    com.sun.tools.attach.VirtualMachine.list();

            System.out.println("List of JVMs for user " + System.getProperty("user.name"));
            for (com.sun.tools.attach.VirtualMachineDescriptor descriptor : descriptors) {
                System.out.println(
                        "\tJVM id " + descriptor.id() + ": '" + descriptor.displayName() + "'");
            }
            return 0;
        }

        log.info("JMX Fetch " + this.appConfig.getVersion() + " has started");

        // set up the config status
        this.appConfig.updateStatus();

        try {
            // Adding another shutdown hook for App related tasks
            Runtime.getRuntime().addShutdownHook(new AppShutdownHook(this));
        } catch (IllegalStateException illegalStateException) {
            // this exception is thrown if shutdown is already happening when the hook is added
            return 0;
        }

        // Get config from the ipc endpoint for "list_*" actions
        if (!action.equals(AppConfig.ACTION_COLLECT)) {
            this.getJsonConfigs();
        }

        // Initiate JMX Connections, get attributes that match the yaml configuration
        this.init(false);

        // We don't want to loop if the action is list_* as it's just used for display information
        // about what will be collected
        if (action.equals(AppConfig.ACTION_COLLECT)) {
            // Start the main loop
            this.start();
        }
        if (action.equals(AppConfig.ACTION_LIST_WITH_METRICS)) {
            this.displayMetrics();
        }
        if (action.equals(AppConfig.ACTION_LIST_WITH_RATE_METRICS)) {
            this.displayRateMetrics();
        }
        return 0;
    }

    /** Sets reinitialization flag. */
    public void setReinit(final boolean reinit) {
        this.reinit.set(reinit);
    }

    /** Returns loop counter with number of iterations performed on instances. */
    public static int getLoopCounter() {
        return loopCounter;
    }

    private void clearInstances(final Collection<Instance> instances) {
        final List<InstanceTask<Void>> cleanupInstanceTasks =
                new ArrayList<>(instances.size());
        for (final Instance instance : instances) {
            // create the cleanup task
            cleanupInstanceTasks.add(new InstanceCleanupTask(instance));
        }

        try {
            if (!this.recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                this.recoveryProcessor.stop();
                this.recoveryProcessor.setThreadPoolExecutor(
                        this.buildExecutorService(this.appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            List<TaskStatusHandler> statuses =
                    this.recoveryProcessor.processTasks(
                            cleanupInstanceTasks,
                            this.appConfig.getReconnectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<Void> future,
                                        final Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

        } catch (Exception e) {
            log.warn(
                    "Unable to terminate all connections gracefully "
                    + "- possible network connectivity issues.");
        } finally {
            // This is a best effort thing, we always clear the list - eventually 'orphaned'
            // instances should get GC'd anyhow.
            instances.clear();
        }
    }

    protected void clearAllInstances() {
        this.clearInstances(this.instances);
    }

    /**
     * Builds an {@link ExecutorService} of the specified fixed size. Threads will be created
     * and executed as daemons if {@link AppConfig#isDaemon()} is true. Defaults to false.
     *
     * @param size The thread pool size
     * @return The create executor
     */
    private ExecutorService buildExecutorService(final int size, final String poolName) {
        return Executors.newFixedThreadPool(size, new ThreadFactory() {

            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                final String threadName = poolName + "-" + counter.incrementAndGet();
                final Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(appConfig.isDaemon());
                return thread;
            }
        });
    }

    private String getAutoDiscoveryName(final String config) {
        final String[] splitted = config.split(System.getProperty("line.separator"), 2);

        return AUTO_DISCOVERY_PREFIX + splitted[0].substring(2);
    }

    private FileInputStream newAutoDiscoveryPipe() {
        FileInputStream adPipe = null;
        final String pipeName = this.appConfig.getAutoDiscoveryPipe();
        try {
            adPipe = new FileInputStream(pipeName);
            log.info("Named pipe for Auto-Discovery opened: {}", pipeName);
        } catch (FileNotFoundException e) {
            log.info("Unable to open named pipe for Auto-Discovery: {}", pipeName);
        }

        return adPipe;
    }

    /** Processes auto-discovery byte buffer (deprecated). */
    public boolean processAutoDiscovery(byte[] buffer) {
        boolean reinit = false;
        String[] discovered;

        final String configs = new String(buffer, UTF_8);
        String separator = App.AD_CONFIG_SEP;

        if (configs.contains(App.AD_LEGACY_CONFIG_SEP)) {
            separator = App.AD_LEGACY_CONFIG_SEP;
        }
        discovered = configs.split(separator + System.getProperty("line.separator"));

        for (String config : discovered) {
            if (config == null || config.isEmpty()) {
                continue;
            }

            final String name = getAutoDiscoveryName(config);
            log.debug("Attempting to apply config. Name: " + name);
            final InputStream stream = new ByteArrayInputStream(config.getBytes(UTF_8));
            final YamlParser yaml = new YamlParser(stream);

            if (this.addConfig(name, yaml)) {
                reinit = true;
                log.debug("Configuration added succesfully reinit in order");
            } else {
                log.debug("Unable to apply configuration.");
            }
        }

        return reinit;
    }

    protected List<Instance> getInstances() {
        return this.instances;
    }

    /* Display metrics on the console report */
    void displayMetrics() {
        this.doIteration();
    }

    /* Display metrics on the console report, including rate metrics */
    void displayRateMetrics() {
        this.doIteration();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.warn(e.getMessage(), e);
        }
        this.doIteration();
    }

    void start() {
        // Main Loop that will periodically collect metrics from the JMX Server
        FileInputStream adPipe = null;

        if (this.appConfig.getAutoDiscoveryPipeEnabled()) {
            log.info("Auto Discovery enabled");
            adPipe = this.newAutoDiscoveryPipe();
            try {
                FileHelper.touch(new File(appConfig.getJmxLaunchFile()));
            } catch (IOException e) {
                log.warn(
                        "Unable to create launch file"
                        + " - Auto-Discovery configs will not be automatically resubmitted.");
            }
        }

        while (true) {
            // Exit on exit file trigger...
            if (this.appConfig.getExitWatcher().shouldExit()) {
                log.info("Exit file detected: stopping JMXFetch.");
                return;
            }

            if (adPipe == null && this.appConfig.getAutoDiscoveryPipeEnabled()) {
                // If SD is enabled and the pipe is not open, retry opening pipe
                adPipe = newAutoDiscoveryPipe();
            }
            // any AutoDiscovery configs waiting?
            try {
                if (adPipe != null && adPipe.available() > 0) {
                    byte[] buffer = new byte[0];
                    boolean terminated = false;
                    while (!terminated) {
                        int len = adPipe.available();
                        if (len > 0) {
                            byte[] minibuff = new byte[len];
                            adPipe.read(minibuff);

                            // The separator always comes in its own atomic write() from the agent
                            // side -
                            // so it will never be chopped.
                            if (CONFIG_TERM_SEARCHER.matches(minibuff)
                                    || LEGACY_CONFIG_TERM_SEARCHER.matches(minibuff)) {
                                terminated = true;
                            }

                            // make room for read chunk
                            int oldLen = buffer.length;
                            buffer = Arrays.copyOf(buffer, buffer.length + len);
                            System.arraycopy(minibuff, 0, buffer, oldLen, len);
                        }
                    }
                    boolean result = processAutoDiscovery(buffer);
                    this.setReinit(result);
                }

                if (this.appConfig.remoteEnabled()) {
                    boolean result = getJsonConfigs();
                    this.setReinit(result);
                }
            } catch (IOException e) {
                log.warn(
                        "Unable to read from pipe"
                                + "- Service Discovery configuration may have been skipped.");
            } catch (Exception e) {
                log.warn("Problem parsing auto-discovery configuration: " + e);
            }

            long start = System.currentTimeMillis();
            if (this.reinit.get()) {
                log.info("Reinitializing...");
                init(true);
            }

            if (instances.size() > 0) {
                this.doIteration();
            } else {
                log.warn("No instance could be initiated. Retrying initialization.");
                this.lastJsonConfigTs = 0; // reset TS to get AC instances
                this.appConfig.getStatus().flush();
                this.configs = getConfigs(appConfig);
                this.init(true);
            }
            final long duration = System.currentTimeMillis() - start;
            log.debug("Iteration ran in " + duration + " ms");
            // Sleep until next collection
            try {
                final long loopPeriod = this.appConfig.getCheckPeriod();
                long sleepPeriod = loopPeriod - duration;
                if (sleepPeriod < loopPeriod / 2) {
                    log.debug(
                        "The collection cycle took longer that the configured check period,"
                        + " the next cycle will be delayed");
                    sleepPeriod = loopPeriod / 2;
                } else {
                    log.debug("Sleeping for {} ms.", sleepPeriod);
                }
                Thread.sleep(sleepPeriod);
            } catch (InterruptedException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    void stop() {
        this.teardownTelemetry();
        this.collectionProcessor.stop();
        this.recoveryProcessor.stop();
    }

    /**
     * Iterates enabled instances collecting JMX metrics from them. Also attempts to fix any broken
     * instances.
     */
    public void doIteration() {
        final Reporter reporter = this.appConfig.getReporter();
        loopCounter++;

        try {
            List<InstanceTask<List<Metric>>> getMetricsTasks =
                    new ArrayList<>(this.instances.size());

            for (Instance instance : this.instances) {
                getMetricsTasks.add(new MetricCollectionTask(instance));
            }
            if (this.appTelemetry != null && this.appConfig.getJmxfetchTelemetry()) {
                this.appTelemetry.setRunningInstanceCount(this.instances.size());
            }

            if (!this.collectionProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for collection processor, "
                        + "previous one hogging threads");
                this.collectionProcessor.stop();
                this.collectionProcessor.setThreadPoolExecutor(
                        this.buildExecutorService(
                                this.appConfig.getThreadPoolSize(), COLLECTION_POOL_NAME));
            }

            final List<TaskStatusHandler> statuses =
                    this.collectionProcessor.processTasks(
                            getMetricsTasks,
                            this.appConfig.getCollectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<List<Metric>>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<List<Metric>> future,
                                        final Reporter reporter) {
                                    return App.processCollectionResults(instance, future, reporter);
                                }
                            });

            this.processCollectionStatus(getMetricsTasks, statuses);

        } catch (Exception e) {
            // INTERNAL ERROR
            // This might also have a place in the status processor

            String instanceMessage;
            final String instanceStatus = Status.STATUS_ERROR;
            final String scStatus = Status.STATUS_ERROR;

            log.warn("JMXFetch internal error invoking concurrent tasks: ", e);

            for (Instance instance : this.instances) {
                // don't add instances to broken instances, issue was internal
                instanceMessage =
                        "Internal JMXFetch error refreshing bean list for instance " + instance;
                this.reportStatus(
                        this.appConfig, reporter, instance, 0, instanceMessage, instanceStatus);
                this.sendServiceCheck(reporter, instance, instanceMessage, scStatus);
            }
        }

        // Attempt to fix broken instances
        this.fixBrokenInstances(reporter);

        try {
            this.appConfig.getStatus().flush();
        } catch (Exception e) {
            log.error("Unable to flush stats.", e);
        }
    }

    private void fixBrokenInstances(final Reporter reporter) {
        if (this.brokenInstanceMap.isEmpty()) {
            return;
        }

        log.debug("Trying to recover broken instances...");
        final List<InstanceTask<Void>> fixInstanceTasks =
                new ArrayList<>(this.brokenInstanceMap.values().size());

        for (final Instance instance : this.brokenInstanceMap.values()) {
            // Clearing rates aggregator so we won't compute wrong rates if we can reconnect
            reporter.clearRatesAggregator(instance.getName());
            reporter.clearCountersAggregator(instance.getName());

            log.warn(
                    "Instance "
                            + instance
                            + " didn't return any metrics. "
                            + "Maybe the server got disconnected ? Trying to reconnect.");

            // Remove the broken instance from the good instance list so jmxfetch won't try to
            // collect metrics from this broken instance during next collection and close
            // ongoing connections (do so asynchronously to avoid locking on network timeout).
            instance.cleanUpAsync();
            this.instances.remove(instance);

            // Resetting the instance
            final Instance newInstance = new Instance(instance, this.appConfig);

            // create the initializing task
            fixInstanceTasks.add(new InstanceInitializingTask(newInstance, true));
        }

        try {
            if (!this.recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                this.recoveryProcessor.stop();
                this.recoveryProcessor.setThreadPoolExecutor(
                        this.buildExecutorService(this.appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            Collections.shuffle(fixInstanceTasks);
            final List<TaskStatusHandler> statuses =
                    this.recoveryProcessor.processTasks(
                            fixInstanceTasks,
                            this.appConfig.getReconnectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<Void> future,
                                        final Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

            this.processFixedStatus(fixInstanceTasks, statuses);

            // update with statuses
            this.processStatus(fixInstanceTasks, statuses);

        } catch (Exception e) {
            // NADA
        }

        log.debug("Done trying to recover broken instances.");
    }

    /**
     * Adds a configuration to the auto-discovery pipe-collected configuration list. This method is
     * deprecated.
     */
    public boolean addConfig(final String name, final YamlParser config) {
        // named groups not supported with Java6:
        //
        // "AUTO_DISCOVERY_PREFIX(?<check>.{1,80})_(?<version>\\d{0,AD_MAX_MAG_INSTANCES})"
        // + 2 cause of underscores.
        if (name.length()
                > AUTO_DISCOVERY_PREFIX.length() + AD_MAX_NAME_LEN + AD_MAX_MAG_INSTANCES + 2) {
            log.debug("Name too long - skipping: " + name);
            return false;
        }
        final String patternText =
                AUTO_DISCOVERY_PREFIX
                        + "(.{1,"
                        + AD_MAX_NAME_LEN
                        + "})_(\\d{0,"
                        + AD_MAX_MAG_INSTANCES
                        + "})";

        final Pattern pattern = Pattern.compile(patternText);

        final Matcher matcher = pattern.matcher(name);
        if (!matcher.find()) {
            // bad name.
            log.debug("Cannot match instance name: " + name);
            return false;
        }

        // Java 6 doesn't allow name matching - group 1 is "check"
        final String check = matcher.group(1);
        if (this.configs.containsKey(check)) {
            // there was already a file config for the check.
            log.debug("Key already present - skipping: " + name);
            return false;
        }

        this.adPipeConfigs.put(name, config);
        this.setReinit(true);

        return true;
    }

    /** Adds a configuration to the auto-discovery HTTP collected configuration list (JSON). */
    public boolean addJsonConfig(final String name, final String json) {
        return false;
    }

    private Map<String, YamlParser> getConfigs(final AppConfig config) {
        final Map<String, YamlParser> configs = new ConcurrentHashMap<>();

        this.loadFileConfigs(config, configs);
        this.loadResourceConfigs(config, configs);

        log.info("Found {} config files", configs.size());
        return configs;
    }

    private void loadFileConfigs(final AppConfig config, final Map<String, YamlParser> configs) {
        final List<String> fileList = config.getYamlFileList();
        if (fileList != null) {
            for (final String fileName : fileList) {
                final File file = new File(config.getConfdDirectory(), fileName);
                final String name = file.getName().replace(".yaml", "");
                final String yamlPath = file.getAbsolutePath();
                log.info("Reading {}", yamlPath);
                try (FileInputStream yamlInputStream = new FileInputStream(yamlPath)) {
                    configs.put(name, new YamlParser(yamlInputStream));
                } catch (FileNotFoundException e) {
                    log.warn("Cannot find " + yamlPath);
                } catch (Exception e) {
                    log.warn("Cannot parse yaml file " + yamlPath, e);
                }
            }
        }
    }

    private void loadResourceConfigs(
            final AppConfig config, final Map<String, YamlParser> configs) {
        final List<String> resourceConfigList = config.getInstanceConfigResources();
        if (resourceConfigList != null) {
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            for (final String resourceName : resourceConfigList) {
                final String name = resourceName.replace(".yaml", "");
                log.info("Reading {}", resourceName);
                final InputStream inputStream = classLoader.getResourceAsStream(resourceName);
                if (inputStream == null) {
                    log.warn("Cannot find " + resourceName);
                } else {
                    try {
                        configs.put(name, new YamlParser(inputStream));
                    } catch (Exception e) {
                        log.warn("Cannot parse yaml file " + resourceName, e);
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

    private boolean getJsonConfigs() {
        HttpClient.HttpResponse response;
        boolean update = false;

        if (this.client == null) {
            return update;
        }

        try {
            String uripath = "agent/jmx/configs?timestamp=" + lastJsonConfigTs;
            response = client.request("GET", "", uripath);
            if (!response.isResponse2xx()) {
                log.warn(
                        "Failed collecting JSON configs: ["
                                + response.getResponseCode()
                                + "] "
                                + response.getResponseBody());
                return update;
            } else if (response.getResponseCode() == 204) {
                log.debug("No configuration changes...");
                return update;
            }
            byte[] utf8 = response.getResponseBody().getBytes(UTF_8);
            InputStream jsonInputStream = new ByteArrayInputStream(utf8);
            JsonParser parser = new JsonParser(jsonInputStream);
            int timestamp = (Integer) parser.getJsonTimestamp();
            if (timestamp > lastJsonConfigTs) {
                adJsonConfigs = (Map<String, Object>) parser.getJsonConfigs();
                lastJsonConfigTs = timestamp;
                update = true;
                log.info("update is in order - updating timestamp: " + lastJsonConfigTs);
                for (String checkName : adJsonConfigs.keySet()) {
                    log.debug("received config for check '" + checkName + "'");
                }
            }
        } catch (JsonProcessingException e) {
            log.error("error processing JSON response: " + e);
        } catch (IOException e) {
            log.error("unable to collect remote JMX configs: " + e);
        }

        return update;
    }

    private void reportStatus(
            final AppConfig appConfig,
            final Reporter reporter,
            final Instance instance,
            final int metricCount,
            final String message,
            final String status) {
        final String checkName = instance.getCheckName();

        final Status stats = appConfig.getStatus();
        stats.addInstanceStats(
                checkName, instance.getName(),
                metricCount, reporter.getServiceCheckCount(checkName),
                message, status, instance.getInstanceTelemetryBean());
        if (reporter.getHandler() != null) {
            stats.addErrorStats(reporter.getHandler().getErrors());
        }
    }

    private void sendServiceCheck(
            final Reporter reporter,
            final Instance instance,
            final String message,
            final String status) {
        final String checkName = instance.getCheckName();

        if (instance.getServiceCheckPrefix() != null) {
            this.sendCanConnectServiceCheck(reporter, checkName, instance.getServiceCheckPrefix(),
                    status, message, instance.getServiceCheckTags());
        } else {
            this.sendCanConnectServiceCheck(reporter, checkName, checkName,
                    status, message, instance.getServiceCheckTags());

            // Service check with formatted name is kept for backward compatibility
            final String formattedCheckName = ServiceCheckHelper
                    .formatServiceCheckPrefix(checkName);
            if (!formattedCheckName.equals(checkName)) {
                this.sendCanConnectServiceCheck(reporter, checkName, formattedCheckName,
                        status, message, instance.getServiceCheckTags());
            }
        }

        reporter.resetServiceCheckCount(checkName);
    }

    private void sendCanConnectServiceCheck(
            final Reporter reporter,
            final String checkName,
            final String serviceCheckPrefix,
            final String status,
            String message,
            final String[] tags) {
        final String serviceCheckName = String.format("%s.can_connect", serviceCheckPrefix);

        if (!status.equals(Status.STATUS_ERROR)) {
            message = null;
        }

        reporter.sendServiceCheck(
                checkName, serviceCheckName, status, message, tags);
    }

    private Instance instantiate(
            final Map<String, Object> instanceMap,
            final Map<String, Object> initConfig,
            final String checkName,
            final AppConfig appConfig) {

        try {
            return new Instance(instanceMap, initConfig, checkName, appConfig, null);
        } catch (Exception e) {
            String warning = "Unable to create instance. Please check your yaml file";
            appConfig.getStatus().addInitFailedCheck(checkName, warning, Status.STATUS_ERROR);
            log.error(warning, e);
        }
        return null;
    }

    /** Initializes instances and metric collection. */
    public void init(final boolean forceNewConnection) {
        log.info("Cleaning up instances...");
        this.clearInstances(this.instances);
        this.instances.clear();
        this.clearInstances(this.brokenInstanceMap.values());
        this.brokenInstanceMap.clear();

        final List<Instance> newInstances = new ArrayList<>();
        final Set<String> instanceNamesSeen = new HashSet<>();

        log.info("Dealing with YAML config instances...");
        final Iterator<Entry<String, YamlParser>> it = this.configs.entrySet().iterator();
        final Iterator<Entry<String, YamlParser>> itPipeConfigs = this.adPipeConfigs
                        .entrySet().iterator();
        while (it.hasNext() || itPipeConfigs.hasNext()) {
            Map.Entry<String, YamlParser> entry;
            boolean fromPipeIterator = false;
            if (it.hasNext()) {
                entry = it.next();
            } else {
                entry = itPipeConfigs.next();
                fromPipeIterator = true;
            }

            final String name = entry.getKey();
            final YamlParser yamlConfig = entry.getValue();
            // AD config cache doesn't remove configs - it just overwrites.
            if (!fromPipeIterator) {
                it.remove();
            }

            final List<Map<String, Object>> configInstances =
                    ((List<Map<String, Object>>) yamlConfig.getYamlInstances());
            if (configInstances == null || configInstances.size() == 0) {
                final String warning = "No instance found in :" + name;
                log.warn(warning);
                this.appConfig.getStatus().addInitFailedCheck(name, warning, Status.STATUS_ERROR);
                continue;
            }

            for (final Map<String, Object> configInstance : configInstances) {
                if (appConfig.isTargetDirectInstances() != isDirectInstance(configInstance)) {
                    log.info("Skipping instance '{}'. targetDirectInstances={} != jvm_direct={}",
                            name,
                            this.appConfig.isTargetDirectInstances(),
                            isDirectInstance(configInstance));
                    continue;
                }
                final String instanceName = (String) configInstance.get("name");
                if (instanceName != null) {
                    if (instanceNamesSeen.contains(instanceName)) {
                        log.warn("Found multiple instances with name: '{}'. "
                            + "Instance names should be unique, "
                            + "update the 'name' field on your instances to be unique.",
                            instanceName);
                    }
                    instanceNamesSeen.add(instanceName);
                }
                // Create a new Instance object
                log.info("Instantiating instance for: {}", name);
                final Instance instance =
                        instantiate(
                                configInstance,
                                (Map<String, Object>) yamlConfig.getInitConfig(),
                                name,
                                this.appConfig);
                newInstances.add(instance);
            }
        }

        // Process JSON configurations
        log.info("Dealing with Auto-Config instances collected...");
        if (this.adJsonConfigs != null) {
            for (String check : this.adJsonConfigs.keySet()) {
                final Map<String, Object> checkConfig =
                        (Map<String, Object>) this.adJsonConfigs.get(check);
                final Map<String, Object> initConfig =
                        (Map<String, Object>) checkConfig.get("init_config");
                final List<Map<String, Object>> configInstances =
                        (List<Map<String, Object>>) checkConfig.get("instances");
                final String checkName = (String) checkConfig.get("check_name");
                for (Map<String, Object> configInstance : configInstances) {
                    log.info("Instantiating instance for: " + checkName);
                    final String instanceName = (String) configInstance.get("name");
                    if (instanceName != null) {
                        if (instanceNamesSeen.contains(instanceName)) {
                            log.warn("Found multiple instances with name: '{}'. "
                                + "Instance names should be unique, "
                                + "update the 'name' field on your instances to be unique.",
                                instanceName);
                        }
                        instanceNamesSeen.add(instanceName);
                    }
                    final Instance instance =
                            instantiate(configInstance, initConfig, checkName, this.appConfig);
                    newInstances.add(instance);
                }
            }
        }

        // Enables jmxfetch telemetry if there are other checks active and it's been enabled
        if (appConfig.getJmxfetchTelemetry() && newInstances.size() >= 1) {
            log.info("Adding jmxfetch telemetry check");
            final Instance instance = instantiate(getTelemetryInstanceConfig(),
                        getTelemetryInitConfig(), "jmxfetch_telemetry_check",
                        this.appConfig);
            newInstances.add(instance);
        }

        final List<InstanceTask<Void>> instanceInitTasks =
                new ArrayList<>(newInstances.size());
        for (Instance instance : newInstances) {
            // create the initializing tasks
            instanceInitTasks.add(new InstanceInitializingTask(instance, forceNewConnection));
        }

        // Initialize the instances
        log.info("Started instance initialization...");

        try {
            if (!this.recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                this.recoveryProcessor.stop();
                this.recoveryProcessor.setThreadPoolExecutor(
                        this.buildExecutorService(this.appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            final List<TaskStatusHandler> statuses =
                    this.recoveryProcessor.processTasks(
                            instanceInitTasks,
                            this.appConfig.getCollectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<Void> future,
                                        final Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

            log.info("Completed instance initialization...");

            this.processInstantiationStatus(instanceInitTasks, statuses);

            // update with statuses
            this.processStatus(instanceInitTasks, statuses);
        } catch (Exception e) {
            // NADA
            log.warn("Critical issue initializing instances: " + e);
        }
    }

    private Map<String,Object> getTelemetryInitConfig() {
        Map<String,Object> config = new HashMap<String,Object>();
        config.put("is_jmx",true);
        return config;
    }

    private Map<String,Object> getTelemetryInstanceConfig() {
        Map<String,Object> config = new HashMap<String,Object>();
        config.put("name","jmxfetch_telemetry_instance");
        config.put("collect_default_jvm_metrics",true);
        config.put("new_gc_metrics",true);
        config.put("jvm_direct",true);
        config.put("normalize_bean_param_tags",true);

        List<Object> conf = new ArrayList<Object>();
        Map<String,Object> confMap = new HashMap<String,Object>();
        Map<String,Object> includeMap = new HashMap<String,Object>();
        includeMap.put("domain",appConfig.getJmxfetchTelemetryDomain());
        confMap.put("include", includeMap);
        conf.add(confMap);
        config.put("conf",conf);

        List<String> tags = new ArrayList<String>();
        tags.add("version:" + this.appConfig.getVersion());
        config.put("tags", tags);

        return config;
    }

    static TaskStatusHandler processRecoveryResults(
            final Instance instance,
            final Future<Void> future,
            final Reporter reporter) {

        final TaskStatusHandler status = new TaskStatusHandler();
        Throwable exc = null;

        try {
            if (future.isDone()) {
                future.get();
            } else if (future.isCancelled()) {
                // Build custom exception
                exc = new TaskProcessException("could not schedule reconnect for instance.");
            }
        } catch (Exception e) {
            exc = e;
        }

        if (exc != null) {
            status.setThrowableStatus(exc);
        }

        return status;
    }

    static TaskStatusHandler processCollectionResults(
            final Instance instance,
            final Future<List<Metric>> future,
            final Reporter reporter) {

        final TaskStatusHandler status = new TaskStatusHandler();
        Throwable exc = null;

        try {
            int numberOfMetrics = 0;

            if (future.isDone()) {
                final List<Metric> metrics = future.get();
                numberOfMetrics = metrics.size();

                status.setData(metrics);

                if (numberOfMetrics == 0) {
                    // Build custom throwable
                    exc =
                            new TaskProcessException(
                                    "Instance " + instance + " didn't return any metrics");
                }

            } else if (future.isCancelled()) {
                // Build custom exception
                exc =
                        new TaskProcessException(
                                "metric collection could not be scheduled in time for: "
                                        + instance);
            }
        } catch (Exception e) {
            // Exception running task
            exc = e;
        }

        if (exc != null) {
            status.setThrowableStatus(exc);
        }

        return status;
    }

    private <T> void processInstantiationStatus(
            final List<InstanceTask<T>> tasks,
            final List<TaskStatusHandler> statuses) {

        // cleanup fixed brokenInstances - matching indices in fixedInstanceIndices List
        final ListIterator<TaskStatusHandler> sit = statuses.listIterator(statuses.size());
        int idx = statuses.size();
        while (sit.hasPrevious()) {
            idx--;

            final Instance instance = tasks.get(idx).getInstance();

            try {
                final TaskStatusHandler status = sit.previous();
                status.raiseForStatus();

                // All was good, add instance
                this.instances.add(instance);
                log.info("Successfully initialized instance: {}", instance.getName());
            } catch (Throwable e) {
                log.warn(
                    "Could not initialize instance: {}:", instance.getName(), e);
                instance.cleanUpAsync();
                this.brokenInstanceMap.put(instance.toString(), instance);
                if (this.appTelemetry != null) {
                    this.appTelemetry.setBrokenInstanceCount(this.brokenInstanceMap.size());
                    this.appTelemetry.incrementBrokenInstanceEventCount();
                }
            }
        }
    }

    private <T> void processFixedStatus(
            final List<InstanceTask<T>> tasks,
            final List<TaskStatusHandler> statuses) {
        // cleanup fixed broken instances - matching indices between statuses and tasks
        final ListIterator<TaskStatusHandler> it = statuses.listIterator();
        int idx = 0;
        while (it.hasNext()) {
            final TaskStatusHandler status = it.next();

            try {
                status.raiseForStatus();

                final Instance instance = tasks.get(idx).getInstance();
                this.brokenInstanceMap.remove(instance.toString());
                this.instances.add(instance);

                if (this.appTelemetry != null && this.appConfig.getJmxfetchTelemetry()) {
                    this.appTelemetry.setBrokenInstanceCount(this.brokenInstanceMap.size());
                    this.appTelemetry.setRunningInstanceCount(this.instances.size());
                }

            } catch (Throwable e) {
                // Not much to do here, instance didn't recover
            } finally {
                idx++;
            }
        }
    }

    private <T> void processStatus(
            final List<InstanceTask<T>> tasks,
            final List<TaskStatusHandler> statuses) {
        for (int i = 0; i < statuses.size(); i++) {

            InstanceTask<T> task = tasks.get(i);
            TaskStatusHandler status = statuses.get(i);
            Instance instance = task.getInstance();
            Reporter reporter = this.appConfig.getReporter();
            String warning = task.getWarning();

            try {
                status.raiseForStatus();
                warning = null;
            } catch (TaskProcessException e) {
                // NOTHING serious
            } catch (ExecutionException ee) {
                Throwable exc = ee.getCause();

                if (exc instanceof IOException) {
                    warning += ". Is the target JMX Server or JVM running? ";
                    warning += exc.getMessage();
                } else if (exc instanceof SecurityException) {
                    warning += " because of bad credentials. Please check your credentials";
                } else if (exc instanceof FailedLoginException) {
                    warning += " because of bad credentials. Please check your credentials";
                } else {
                    warning += " for an unknown reason." + exc.getMessage();
                }

            } catch (CancellationException ce) {
                warning +=
                        " because connection timed out and was canceled. "
                        + "Please check your network.";
            } catch (InterruptedException ie) {
                warning += " attempt interrupted waiting on IO";
            } catch (Throwable e) {
                warning += " There was an unexpected exception: " + e.getMessage();
            } finally {
                if (warning != null) {
                    log.warn(warning);

                    this.reportStatus(
                            this.appConfig, reporter, instance, 0, warning, Status.STATUS_ERROR);
                    this.sendServiceCheck(reporter, instance, warning, Status.STATUS_ERROR);
                }
            }
        }
    }

    private <T> void processCollectionStatus(
            final List<InstanceTask<T>> tasks, final List<TaskStatusHandler> statuses) {
        for (int i = 0; i < statuses.size(); i++) {
            String instanceMessage = null;
            String instanceStatus = Status.STATUS_OK;
            String scStatus = Status.STATUS_OK;

            int numberOfMetrics = 0;

            final InstanceTask<T> task = tasks.get(i);
            final TaskStatusHandler status = statuses.get(i);
            final Instance instance = task.getInstance();
            final Reporter reporter = this.appConfig.getReporter();

            try {
                status.raiseForStatus();

                // If we get here all was good - metric count  available
                final List<Metric> metrics = (List<Metric>) status.getData();
                numberOfMetrics = metrics.size();

                if (instance.isLimitReached()) {
                    instanceMessage =
                            "Number of returned metrics is too high for instance: "
                                    + instance.getName()
                                    + ". Please read http://docs.datadoghq.com/integrations/java/ or get in touch with Datadog "
                                    + "Support for more details. Truncating to "
                                    + instance.getMaxNumberOfMetrics()
                                    + " metrics.";

                    instanceStatus = Status.STATUS_WARNING;
                    CustomLogger.laconic(log, LogLevel.WARN, instanceMessage, 0);
                }

                if (numberOfMetrics > 0) {
                    reporter.sendMetrics(
                            metrics, instance.getName(), instance.getCanonicalRateConfig());
                }

            } catch (TaskProcessException te) {
                // This would be "fine" - no need to evict
                instanceStatus = Status.STATUS_WARNING;
                scStatus = Status.STATUS_WARNING;

                instanceMessage = te.toString();

                log.warn(instanceMessage);
            } catch (ExecutionException ee) {
                instanceMessage = task.getWarning();
                instanceStatus = Status.STATUS_ERROR;

                this.brokenInstanceMap.put(instance.toString(), instance);
                log.debug("Adding broken instance to list: " + instance.getName());
                if (this.appTelemetry != null) {
                    this.appTelemetry.setBrokenInstanceCount(this.brokenInstanceMap.size());
                    this.appTelemetry.incrementBrokenInstanceEventCount();
                }

                log.warn(instanceMessage, ee.getCause());
            } catch (Throwable t) {
                // Legit exception during task - eviction necessary
                log.debug("Adding broken instance to list: " + instance.getName());
                this.brokenInstanceMap.put(instance.toString(), instance);

                if (this.appTelemetry != null) {
                    this.appTelemetry.setBrokenInstanceCount(this.brokenInstanceMap.size());
                    this.appTelemetry.incrementBrokenInstanceEventCount();
                }

                instanceStatus = Status.STATUS_ERROR;
                instanceMessage = task.getWarning() + ": " + t.toString();

                log.warn(instanceMessage);

            } finally {

                if (Status.STATUS_ERROR.equals(instanceStatus)) {
                    scStatus = Status.STATUS_ERROR;
                }

                this.reportStatus(
                        this.appConfig,
                        reporter,
                        instance,
                        numberOfMetrics,
                        instanceMessage,
                        instanceStatus);
                this.sendServiceCheck(reporter, instance, instanceMessage, scStatus);
            }
        }
    }

    public AppTelemetry getAppTelemetryBean() {
        return this.appTelemetry;
    }
}
