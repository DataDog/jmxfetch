package org.datadog.jmxfetch;

import static org.datadog.jmxfetch.Instance.isDirectInstance;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.tasks.TaskMethod;
import org.datadog.jmxfetch.tasks.TaskProcessException;
import org.datadog.jmxfetch.tasks.TaskProcessor;
import org.datadog.jmxfetch.tasks.TaskStatusHandler;
import org.datadog.jmxfetch.util.ByteArraySearcher;
import org.datadog.jmxfetch.util.CustomLogger;
import org.datadog.jmxfetch.util.FileHelper;
import org.datadog.jmxfetch.util.ServiceCheckHelper;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
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
    private Map<String, YamlParser> adPipeConfigs = new ConcurrentHashMap<String, YamlParser>();
    private List<Instance> instances = new ArrayList<Instance>();
    private Map<String, Instance> brokenInstanceMap = new ConcurrentHashMap<String, Instance>();
    private AtomicBoolean reinit = new AtomicBoolean(false);

    private TaskProcessor collectionProcessor;
    private TaskProcessor recoveryProcessor;

    private AppConfig appConfig;
    private HttpClient client;

    /** Application constructor. */
    public App(AppConfig appConfig) {
        this.appConfig = appConfig;

        ExecutorService collectionThreadPool = null;
        ExecutorService recoveryThreadPool = null;
        if (!appConfig.isEmbedded()) { // Creates executors in standalone mode only
            collectionThreadPool = buildExecutorService(appConfig.getThreadPoolSize(),
                    COLLECTION_POOL_NAME);
            recoveryThreadPool = buildExecutorService(appConfig.getReconnectionThreadPoolSize(),
                    RECOVERY_POOL_NAME);
        }
        recoveryProcessor = new TaskProcessor(recoveryThreadPool, appConfig.getReporter());
        collectionProcessor = new TaskProcessor(collectionThreadPool, appConfig.getReporter());
        // setup client
        if (appConfig.remoteEnabled()) {
            client = new HttpClient(appConfig.getIpcHost(), appConfig.getIpcPort(), false);
        }
        this.configs = getConfigs(appConfig);
    }

    /**
     * Main entry of JMXFetch.
     *
     * <p>See AppConfig class for more details on the args
     */
    public static void main(String[] args) {

        // Load the config from the args
        AppConfig config = AppConfig.builder().build();
        JCommander commander = null;
        try {
            // Try to parse the args using JCommander
            commander = new JCommander(config, args);
        } catch (ParameterException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        // Display the version and quit
        if (config.isVersion() || AppConfig.ACTION_VERSION.equals(config.getAction())) {
            JCommander.getConsole().println("JMX Fetch " + getVersion());
            System.exit(0);
        }

        // Display the help and quit
        if (config.isHelp() || AppConfig.ACTION_HELP.equals(config.getAction())) {
            commander.usage();
            System.exit(0);
        }

        {
            // Running these commands here because they are logging specific,
            // not needed in dd-java-agent, which calls run directly.

            // Set up the logger to add file handler
            CustomLogger.setup(Level.toLevel(config.getLogLevel()),
                    config.getLogLocation(),
                    config.isLogFormatRfc3339());

            // Set up the shutdown hook to properly close resources
            attachShutdownHook();
        }
        System.exit(run(config));
    }

    /**  Returns our own version number. */
    public static String getVersion() {
        try {
            final Properties properties = new Properties();
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            properties.load(classLoader.getResourceAsStream("project.properties"));
            return properties.getProperty("version");
        } catch (IOException e) {
            e.printStackTrace();
            return "?.?.?";
        }
    }

    /**
     * Main entry point of JMXFetch that returns integer on exit instead of calling {@code
     * System#exit}.
     */
    public static int run(AppConfig config) {
        Marker fatal = MarkerFactory.getMarker("FATAL");
        String action = config.getAction();

        // The specified action is unknown
        if (!AppConfig.ACTIONS.contains(action)) {
            log.error(fatal,
                    action + " is not in " + AppConfig.ACTIONS + ". Exiting.");
            return 1;
        }

        if (!action.equals(AppConfig.ACTION_COLLECT)
            && !(config.isConsoleReporter() || config.isJsonReporter())) {
            // The "list_*" actions can not be used with the statsd reporter
            log.error(fatal,
                      action
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

        log.info("JMX Fetch " + getVersion() + " has started");

        // set up the config status
        config.updateStatus();

        App app = new App(config);

        // Adding another shutdown hook for App related tasks
        Runtime.getRuntime().addShutdownHook(new AppShutdownHook(app));

        // Get config from the ipc endpoint for "list_*" actions
        if (!action.equals(AppConfig.ACTION_COLLECT)) {
            app.getJsonConfigs();
        }

        // Initiate JMX Connections, get attributes that match the yaml configuration
        app.init(false);

        // We don't want to loop if the action is list_* as it's just used for display information
        // about what will be collected
        if (action.equals(AppConfig.ACTION_COLLECT)) {
            // Start the main loop
            app.start();
        }
        if (action.equals(AppConfig.ACTION_LIST_WITH_METRICS)) {
            app.displayMetrics();
        }
        if (action.equals(AppConfig.ACTION_LIST_WITH_RATE_METRICS)) {
            app.displayRateMetrics();
        }
        return 0;
    }

    /** Attach a Shutdown Hook that will be called when SIGTERM is sent to JMXFetch. */
    private static void attachShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            new Thread() {
                @Override
                public void run() {
                    log.info("JMXFetch is closing");
                    // Properly close log handlers
                    LogManager.shutdown();
                }
            }
        );
    }

    /** Sets reinitialization flag. */
    public void setReinit(boolean reinit) {
        this.reinit.set(reinit);
    }

    /** Returns loop counter with number of iterations performed on instances. */
    public static int getLoopCounter() {
        return loopCounter;
    }

    private void clearInstances(Collection<Instance> instances) {
        List<InstanceTask<Void>> cleanupInstanceTasks =
                new ArrayList<InstanceTask<Void>>(instances.size());
        for (Instance instance : instances) {
            // create the cleanup task
            cleanupInstanceTasks.add(new InstanceCleanupTask(instance));
        }

        try {
            if (!recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                recoveryProcessor.stop();
                recoveryProcessor.setThreadPoolExecutor(
                        buildExecutorService(appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            List<TaskStatusHandler> statuses =
                    recoveryProcessor.processTasks(
                            cleanupInstanceTasks,
                            appConfig.getReconnectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        Instance instance, Future<Void> future, Reporter reporter) {
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

    /**
     * Builds an {@link ExecutorService} of the specified fixed size. Threads will be created
     * and executed as daemons if {@link AppConfig#isDaemon()} is true. Defaults to false.
     *
     * @param size The thread pool size
     * @return The create executor
     */
    private ExecutorService buildExecutorService(int size, final String poolName) {
        return Executors.newFixedThreadPool(size, new ThreadFactory() {

            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                String threadName = poolName + "-" + counter.incrementAndGet();
                Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(appConfig.isDaemon());
                return thread;
            }
        });
    }

    private String getAutoDiscoveryName(String config) {
        String[] splitted = config.split(System.getProperty("line.separator"), 2);

        return AUTO_DISCOVERY_PREFIX + splitted[0].substring(2, splitted[0].length());
    }

    private FileInputStream newAutoDiscoveryPipe() {
        FileInputStream adPipe = null;
        String pipeName = appConfig.getAutoDiscoveryPipe();
        try {
            adPipe = new FileInputStream(pipeName);
            log.info("Named pipe for Auto-Discovery opened: " + pipeName);
        } catch (FileNotFoundException e) {
            log.info("Unable to open named pipe for Auto-Discovery: " + pipeName);
        }

        return adPipe;
    }

    /** Processes auto-discovery byte buffer (deprecated). */
    public boolean processAutoDiscovery(byte[] buffer) {
        boolean reinit = false;
        String[] discovered;

        String configs = new String(buffer, UTF_8);
        String separator = App.AD_CONFIG_SEP;

        if (configs.indexOf(App.AD_LEGACY_CONFIG_SEP) != -1) {
            separator = App.AD_LEGACY_CONFIG_SEP;
        }
        discovered = configs.split(separator + System.getProperty("line.separator"));

        for (String config : discovered) {
            if (config == null || config.isEmpty()) {
                continue;
            }

            String name = getAutoDiscoveryName(config);
            log.debug("Attempting to apply config. Name: " + name);
            InputStream stream = new ByteArrayInputStream(config.getBytes(UTF_8));
            YamlParser yaml = new YamlParser(stream);

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
        doIteration();
    }

    /* Display metrics on the console report, including rate metrics */
    void displayRateMetrics() {
        doIteration();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.warn(e.getMessage(), e);
        }
        doIteration();
    }

    void start() {
        // Main Loop that will periodically collect metrics from the JMX Server
        FileInputStream adPipe = null;

        if (appConfig.getAutoDiscoveryPipeEnabled()) {
            log.info("Auto Discovery enabled");
            adPipe = newAutoDiscoveryPipe();
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
            if (appConfig.getExitWatcher().shouldExit()) {
                log.info("Exit file detected: stopping JMXFetch.");
                return;
            }

            if (adPipe == null && appConfig.getAutoDiscoveryPipeEnabled()) {
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
                    setReinit(processAutoDiscovery(buffer));
                }

                if (appConfig.remoteEnabled()) {
                    setReinit(getJsonConfigs());
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
                doIteration();
            } else {
                log.warn("No instance could be initiated. Retrying initialization.");
                lastJsonConfigTs = 0; // reset TS to get AC instances
                appConfig.getStatus().flush();
                configs = getConfigs(appConfig);
                init(true);
            }
            long duration = System.currentTimeMillis() - start;
            log.debug("Iteration ran in " + duration + " ms");
            // Sleep until next collection
            try {
                long loopPeriod = appConfig.getCheckPeriod();
                long sleepPeriod = loopPeriod - duration;
                if (sleepPeriod <= 0) {
                    log.debug("The collection cycle took longer that the configured check period, the next cycle will start immediatly");
                }
                else {
                    log.debug("Sleeping for " + sleepPeriod + " ms.");
                    Thread.sleep(sleepPeriod);    
                }
            } catch (InterruptedException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    void stop() {
        collectionProcessor.stop();
        recoveryProcessor.stop();
    }

    /**
     * Iterates enabled instances collecting JMX metrics from them. Also attempts to fix any broken
     * instances.
     */
    public void doIteration() {
        Reporter reporter = appConfig.getReporter();
        loopCounter++;

        try {
            List<InstanceTask<List<Metric>>> getMetricsTasks =
                    new ArrayList<InstanceTask<List<Metric>>>(instances.size());

            for (Instance instance : instances) {
                getMetricsTasks.add(new MetricCollectionTask(instance));
            }

            if (!collectionProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for collection processor, "
                        + "previous one hogging threads");
                collectionProcessor.stop();
                collectionProcessor.setThreadPoolExecutor(
                        buildExecutorService(appConfig.getThreadPoolSize(), COLLECTION_POOL_NAME));
            }

            List<TaskStatusHandler> statuses =
                    collectionProcessor.processTasks(
                            getMetricsTasks,
                            appConfig.getCollectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<List<Metric>>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        Instance instance,
                                        Future<List<Metric>> future,
                                        Reporter reporter) {
                                    return App.processCollectionResults(instance, future, reporter);
                                }
                            });

            processCollectionStatus(getMetricsTasks, statuses);

        } catch (Exception e) {
            // INTERNAL ERROR
            // This might also have a place in the status processor

            String instanceMessage;
            String instanceStatus = Status.STATUS_ERROR;
            String scStatus = Status.STATUS_ERROR;

            log.warn("JMXFetch internal error invoking concurrent tasks: ", e);

            for (Instance instance : instances) {
                // don't add instances to broken instances, issue was internal
                instanceMessage =
                        "Internal JMXFetch error refreshing bean list for instance " + instance;
                this.reportStatus(
                        appConfig, reporter, instance, 0, instanceMessage, instanceStatus);
                this.sendServiceCheck(reporter, instance, instanceMessage, scStatus);
            }
        }

        // Attempt to fix broken instances
        fixBrokenInstances(reporter);

        try {
            appConfig.getStatus().flush();
        } catch (Exception e) {
            log.error("Unable to flush stats.", e);
        }
    }

    private void fixBrokenInstances(Reporter reporter) {
        if (brokenInstanceMap.isEmpty()) {
            return;
        }

        log.debug("Trying to recover broken instances...");
        List<InstanceTask<Void>> fixInstanceTasks =
                new ArrayList<InstanceTask<Void>>(brokenInstanceMap.values().size());

        for (Instance instance : brokenInstanceMap.values()) {
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
            instances.remove(instance);

            // Resetting the instance
            Instance newInstance = new Instance(instance, appConfig);

            // create the initializing task
            fixInstanceTasks.add(new InstanceInitializingTask(newInstance, true));
        }

        try {
            if (!recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                recoveryProcessor.stop();
                recoveryProcessor.setThreadPoolExecutor(
                        buildExecutorService(appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            Collections.shuffle(fixInstanceTasks);
            List<TaskStatusHandler> statuses =
                    recoveryProcessor.processTasks(
                            fixInstanceTasks,
                            appConfig.getReconnectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        Instance instance, Future<Void> future, Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

            processFixedStatus(fixInstanceTasks, statuses);

            // update with statuses
            processStatus(fixInstanceTasks, statuses);

        } catch (Exception e) {
            // NADA
        }

        log.debug("Done trying to recover broken instances.");
    }

    /**
     * Adds a configuration to the auto-discovery pipe-collected configuration list. This method is
     * deprecated.
     */
    public boolean addConfig(String name, YamlParser config) {
        // named groups not supported with Java6:
        //
        // "AUTO_DISCOVERY_PREFIX(?<check>.{1,80})_(?<version>\\d{0,AD_MAX_MAG_INSTANCES})"
        // + 2 cause of underscores.
        if (name.length()
                > AUTO_DISCOVERY_PREFIX.length() + AD_MAX_NAME_LEN + AD_MAX_MAG_INSTANCES + 2) {
            log.debug("Name too long - skipping: " + name);
            return false;
        }
        String patternText =
                AUTO_DISCOVERY_PREFIX
                        + "(.{1,"
                        + AD_MAX_NAME_LEN
                        + "})_(\\d{0,"
                        + AD_MAX_MAG_INSTANCES
                        + "})";

        Pattern pattern = Pattern.compile(patternText);

        Matcher matcher = pattern.matcher(name);
        if (!matcher.find()) {
            // bad name.
            log.debug("Cannot match instance name: " + name);
            return false;
        }

        // Java 6 doesn't allow name matching - group 1 is "check"
        String check = matcher.group(1);
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
    public boolean addJsonConfig(String name, String json) {
        return false;
    }

    private Map<String, YamlParser> getConfigs(AppConfig config) {
        Map<String, YamlParser> configs = new ConcurrentHashMap<String, YamlParser>();

        loadFileConfigs(config, configs);
        loadResourceConfigs(config, configs);

        log.info("Found " + configs.size() + " config files");
        return configs;
    }

    private void loadFileConfigs(AppConfig config, Map<String, YamlParser> configs) {
        List<String> fileList = config.getYamlFileList();
        if (fileList != null) {
            for (String fileName : fileList) {
                File file = new File(config.getConfdDirectory(), fileName);
                String name = file.getName().replace(".yaml", "");
                String yamlPath = file.getAbsolutePath();
                FileInputStream yamlInputStream = null;
                log.info("Reading " + yamlPath);
                try {
                    yamlInputStream = new FileInputStream(yamlPath);
                    configs.put(name, new YamlParser(yamlInputStream));
                } catch (FileNotFoundException e) {
                    log.warn("Cannot find " + yamlPath);
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

    private void loadResourceConfigs(
            AppConfig config, Map<String, YamlParser> configs) {
        List<String> resourceConfigList = config.getInstanceConfigResources();
        if (resourceConfigList != null) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            for (String resourceName : resourceConfigList) {
                String name = resourceName.replace(".yaml", "");
                log.info("Reading " + resourceName);
                InputStream inputStream = classLoader.getResourceAsStream(resourceName);
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
            int timestamp = ((Integer) parser.getJsonTimestamp()).intValue();
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
            AppConfig appConfig,
            Reporter reporter,
            Instance instance,
            int metricCount,
            String message,
            String status) {
        String checkName = instance.getCheckName();

        appConfig
                .getStatus()
                .addInstanceStats(
                        checkName, instance.getName(),
                        metricCount, reporter.getServiceCheckCount(checkName),
                        message, status);
    }

    private void sendServiceCheck(
            Reporter reporter, Instance instance, String message, String status) {
        String checkName = instance.getCheckName();

        if (instance.getServiceCheckPrefix() != null) {
            this.sendCanConnectServiceCheck(reporter, checkName, instance.getServiceCheckPrefix(),
                    status, message, instance.getServiceCheckTags());
        } else {
            this.sendCanConnectServiceCheck(reporter, checkName, checkName,
                    status, message, instance.getServiceCheckTags());

            // Service check with formatted name is kept for backward compatibility
            String formattedCheckName = ServiceCheckHelper.formatServiceCheckPrefix(checkName);
            if (!formattedCheckName.equals(checkName)) {
                this.sendCanConnectServiceCheck(reporter, checkName, formattedCheckName,
                        status, message, instance.getServiceCheckTags());
            }
        }

        reporter.resetServiceCheckCount(checkName);
    }

    private void sendCanConnectServiceCheck(Reporter reporter, String checkName,
        String serviceCheckPrefix, String status, String message, String[] tags) {
        String serviceCheckName = String.format("%s.can_connect", serviceCheckPrefix);
        reporter.sendServiceCheck(
                checkName, serviceCheckName, status, message, tags);
    }

    private Instance instantiate(
            Map<String, Object> instanceMap,
            Map<String, Object> initConfig,
            String checkName,
            AppConfig appConfig) {

        Instance instance;
        Reporter reporter = appConfig.getReporter();

        try {
            instance = new Instance(instanceMap, initConfig, checkName, appConfig);
        } catch (Exception e) {
            String warning = "Unable to create instance. Please check your yaml file";
            appConfig.getStatus().addInitFailedCheck(checkName, warning, Status.STATUS_ERROR);
            log.error(warning, e);
            return null;
        }

        return instance;
    }

    /** Initializes instances and metric collection. */
    public void init(boolean forceNewConnection) {
        log.info("Cleaning up instances...");
        clearInstances(instances);
        clearInstances(brokenInstanceMap.values());
        brokenInstanceMap.clear();

        List<Instance> newInstances = new ArrayList<Instance>();

        log.info("Dealing with YAML config instances...");
        Iterator<Entry<String, YamlParser>> it = configs.entrySet().iterator();
        Iterator<Entry<String, YamlParser>> itPipeConfigs = adPipeConfigs.entrySet().iterator();
        while (it.hasNext() || itPipeConfigs.hasNext()) {
            Map.Entry<String, YamlParser> entry;
            boolean fromPipeIterator = false;
            if (it.hasNext()) {
                entry = it.next();
            } else {
                entry = itPipeConfigs.next();
                fromPipeIterator = true;
            }

            String name = entry.getKey();
            YamlParser yamlConfig = entry.getValue();
            // AD config cache doesn't remove configs - it just overwrites.
            if (!fromPipeIterator) {
                it.remove();
            }

            List<Map<String, Object>> configInstances =
                    ((List<Map<String, Object>>) yamlConfig.getYamlInstances());
            if (configInstances == null || configInstances.size() == 0) {
                String warning = "No instance found in :" + name;
                log.warn(warning);
                appConfig.getStatus().addInitFailedCheck(name, warning, Status.STATUS_ERROR);
                continue;
            }

            for (Map<String, Object> configInstance : configInstances) {
                if (appConfig.isTargetDirectInstances() != isDirectInstance(configInstance)) {
                    log.info("Skipping instance '{}'. targetDirectInstances={} != jvm_direct={}",
                            name,
                            appConfig.isTargetDirectInstances(),
                            isDirectInstance(configInstance));
                    continue;
                }

                // Create a new Instance object
                log.info("Instantiating instance for: {}", name);
                Instance instance =
                        instantiate(
                                configInstance,
                                (Map<String, Object>) yamlConfig.getInitConfig(),
                                name,
                                appConfig);
                newInstances.add(instance);
            }
        }

        // Process JSON configurations
        log.info("Dealing with Auto-Config instances collected...");
        if (adJsonConfigs != null) {
            for (String check : adJsonConfigs.keySet()) {
                Map<String, Object> checkConfig =
                        (Map<String, Object>) adJsonConfigs.get(check);
                Map<String, Object> initConfig =
                        (Map<String, Object>) checkConfig.get("init_config");
                List<Map<String, Object>> configInstances =
                        (List<Map<String, Object>>) checkConfig.get("instances");
                String checkName = (String) checkConfig.get("check_name");
                for (Map<String, Object> configInstance : configInstances) {
                    log.info("Instantiating instance for: " + checkName);
                    Instance instance =
                            instantiate(configInstance, initConfig, checkName, appConfig);
                    newInstances.add(instance);
                }
            }
        }

        List<InstanceTask<Void>> instanceInitTasks =
                new ArrayList<InstanceTask<Void>>(newInstances.size());
        for (Instance instance : newInstances) {
            // create the initializing tasks
            instanceInitTasks.add(new InstanceInitializingTask(instance, forceNewConnection));
        }

        // Initialize the instances
        log.info("Started instance initialization...");

        try {
            if (!recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                recoveryProcessor.stop();
                recoveryProcessor.setThreadPoolExecutor(
                        buildExecutorService(appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            List<TaskStatusHandler> statuses =
                    recoveryProcessor.processTasks(
                            instanceInitTasks,
                            appConfig.getCollectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        Instance instance, Future<Void> future, Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

            log.info("Completed instance initialization...");

            processInstantiationStatus(instanceInitTasks, statuses);

            // update with statuses
            processStatus(instanceInitTasks, statuses);
        } catch (Exception e) {
            // NADA
            log.warn("Critical issue initializing instances: " + e);
        }
    }

    static TaskStatusHandler processRecoveryResults(
            Instance instance, Future<Void> future, Reporter reporter) {

        TaskStatusHandler status = new TaskStatusHandler();
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
            Instance instance,
            Future<List<Metric>> future,
            Reporter reporter) {

        TaskStatusHandler status = new TaskStatusHandler();
        Throwable exc = null;

        try {
            int numberOfMetrics = 0;

            if (future.isDone()) {
                List<Metric> metrics = future.get();
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
            List<InstanceTask<T>> tasks, List<TaskStatusHandler> statuses) {

        // cleanup fixed brokenInstances - matching indices in fixedInstanceIndices List
        ListIterator<TaskStatusHandler> sit = statuses.listIterator(statuses.size());
        int idx = statuses.size();
        while (sit.hasPrevious()) {
            idx--;

            Instance instance = tasks.get(idx).getInstance();

            try {
                TaskStatusHandler status = sit.previous();
                status.raiseForStatus();

                // All was good, add instance
                instances.add(instance);
                log.info("Successfully initialized instance: " + instance.getName());
            } catch (Throwable e) {
                log.info(
                    "Could not initialize instance: " + instance.getName()
                    + ": " + e.toString());
                instance.cleanUpAsync();
                brokenInstanceMap.put(instance.toString(), instance);
            }
        }
    }

    private <T> void processFixedStatus(
            List<InstanceTask<T>> tasks, List<TaskStatusHandler> statuses) {
        // cleanup fixed broken instances - matching indices between statuses and tasks
        ListIterator<TaskStatusHandler> it = statuses.listIterator();
        int idx = 0;
        while (it.hasNext()) {
            TaskStatusHandler status = it.next();

            try {
                status.raiseForStatus();

                Instance instance = tasks.get(idx).getInstance();
                brokenInstanceMap.remove(instance.toString());
                this.instances.add(instance);

            } catch (Throwable e) {
                // Not much to do here, instance didn't recover
            } finally {
                idx++;
            }
        }
    }

    private <T> void processStatus(List<InstanceTask<T>> tasks, List<TaskStatusHandler> statuses) {
        for (int i = 0; i < statuses.size(); i++) {

            InstanceTask task = tasks.get(i);
            TaskStatusHandler status = statuses.get(i);
            Instance instance = task.getInstance();
            Reporter reporter = appConfig.getReporter();
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
                            appConfig, reporter, instance, 0, warning, Status.STATUS_ERROR);
                    this.sendServiceCheck(reporter, instance, warning, Status.STATUS_ERROR);
                }
            }
        }
    }

    private <T> void processCollectionStatus(
            List<InstanceTask<T>> tasks, List<TaskStatusHandler> statuses) {
        for (int i = 0; i < statuses.size(); i++) {
            String instanceMessage = null;
            String instanceStatus = Status.STATUS_OK;
            String scStatus = Status.STATUS_OK;
            List<Metric> metrics;

            int numberOfMetrics = 0;

            InstanceTask<T> task = tasks.get(i);
            TaskStatusHandler status = statuses.get(i);
            Instance instance = task.getInstance();
            Reporter reporter = appConfig.getReporter();

            try {
                status.raiseForStatus();

                // If we get here all was good - metric count  available
                metrics = (List<Metric>) status.getData();
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
                    CustomLogger.laconic(log, Level.WARN, instanceMessage, 0);
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

                brokenInstanceMap.put(instance.toString(), instance);
                log.debug("Adding broken instance to list: " + instance.getName());

                log.warn(instanceMessage, ee.getCause());
            } catch (Throwable t) {
                // Legit exception during task - eviction necessary
                log.debug("Adding broken instance to list: " + instance.getName());
                brokenInstanceMap.put(instance.toString(), instance);

                instanceStatus = Status.STATUS_ERROR;
                instanceMessage = task.getWarning() + ": " + t.toString();

                log.warn(instanceMessage);

            } finally {

                if (instanceStatus == Status.STATUS_ERROR) {
                    scStatus = Status.STATUS_ERROR;
                }

                this.reportStatus(
                        appConfig,
                        reporter,
                        instance,
                        numberOfMetrics,
                        instanceMessage,
                        instanceStatus);
                this.sendServiceCheck(reporter, instance, instanceMessage, scStatus);
            }
        }
    }
}
