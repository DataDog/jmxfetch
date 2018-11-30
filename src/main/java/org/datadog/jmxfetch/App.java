package org.datadog.jmxfetch;

import com.google.common.primitives.Bytes;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.util.CustomLogger;
import org.datadog.jmxfetch.util.FileHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.lang.InterruptedException;

import javax.security.auth.login.FailedLoginException;


@SuppressWarnings("unchecked")
public class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static final String AUTO_DISCOVERY_PREFIX = "AD-";
    public static final String CANNOT_CONNECT_TO_INSTANCE = "Cannot connect to instance ";
    private static final String AD_CONFIG_SEP = "#### AUTO-DISCOVERY ####";
    private static final String AD_LEGACY_CONFIG_SEP = "#### SERVICE-DISCOVERY ####";
    private static final String AD_CONFIG_TERM = "#### AUTO-DISCOVERY TERM ####";
    private static final String AD_LEGACY_CONFIG_TERM = "#### SERVICE-DISCOVERY TERM ####";
    private static final int AD_MAX_NAME_LEN = 80;
    private static final int AD_MAX_MAG_INSTANCES =
            4; // 1000 instances ought to be enough for anyone
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static int loopCounter;
    private int lastJsonConfigTs;
    private HashMap<String, Object> adJsonConfigs;
    private ConcurrentHashMap<String, YamlParser> configs;
    private ConcurrentHashMap<String, YamlParser> adPipeConfigs =
            new ConcurrentHashMap<String, YamlParser>();
    private ArrayList<Instance> instances = new ArrayList<Instance>();
    private LinkedList<Instance> brokenInstances = new LinkedList<Instance>();
    private AtomicBoolean reinit = new AtomicBoolean(false);
    private ExecutorService threadPoolExecutor;

    private AppConfig appConfig;
    private HttpClient client;

    class MetricCollectionTask implements Callable {
        Instance instance;

         MetricCollectionTask(Instance instance) {
            this.instance = instance;
        }

        @Override
        public LinkedList<HashMap<String, Object>> call() throws Exception {

            if (!instance.timeToCollect()) {
                LOGGER.debug("it is not time to collect, skipping run for instance: " + instance.getName());

                // Maybe raise an exception here instead...
                return new LinkedList<HashMap<String, Object>>();
            }

            return instance.getMetrics();
        }
    }

    class InstanceInitializingTask implements Callable {
            Instance instance;

            InstanceInitializingTask(Instance instance) {
                this.instance = instance;
            }

            @Override
            public Void call() throws Exception {
                // Try to reinit the connection and force to renew it
                LOGGER.info("Trying to reconnect to: " + instance);

                instance.init(true);
                return null;
            }
    };

    /**
     * Application constructor.
     * */
    public App(AppConfig appConfig) {
        this.appConfig = appConfig;

        threadPoolExecutor = Executors.newFixedThreadPool(appConfig.getThreadPoolSize());

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
        AppConfig config = new AppConfig();
        JCommander commander = null;
        try {
            // Try to parse the args using JCommander
            commander = new JCommander(config, args);
        } catch (ParameterException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        // Display the help and quit
        if (config.isHelp() || config.getAction().equals(AppConfig.ACTION_HELP)) {
            commander.usage();
            System.exit(0);
        }

        System.exit(run(config));
    }

    /**
     * Main entry point of JMXFetch that returns integer on exit instead of calling {@code
     * System#exit}.
     */
    public static int run(AppConfig config) {
        // Set up the logger to add file handler
        CustomLogger.setup(Level.toLevel(config.getLogLevel()), config.getLogLocation());

        // The specified action is unknown
        if (!AppConfig.ACTIONS.contains(config.getAction())) {
            LOGGER.fatal(config.getAction() + " is not in " + AppConfig.ACTIONS + ". Exiting.");
            return 1;
        }

        // The "list_*" actions can only be used with the reporter
        if (!config.getAction().equals(AppConfig.ACTION_COLLECT) && !config.isConsoleReporter()) {
            LOGGER.fatal(
                    config.getAction()
                            + " argument can only be used with the console reporter. Exiting.");
            return 1;
        }

        if (config.getAction().equals(AppConfig.ACTION_LIST_JVMS)) {
            List<com.sun.tools.attach.VirtualMachineDescriptor> descriptors =
                    com.sun.tools.attach.VirtualMachine.list();

            System.out.println("List of JVMs for user " + System.getProperty("user.name"));
            for (com.sun.tools.attach.VirtualMachineDescriptor descriptor : descriptors) {
                System.out.println(
                        "\tJVM id " + descriptor.id() + ": '" + descriptor.displayName() + "'");
            }
            return 0;
        }

        // Set up the shutdown hook to properly close resources
        attachShutdownHook();

        LOGGER.info("JMX Fetch has started");

        // set up the config status
        config.updateStatus();

        App app = new App(config);

        // Adding another shutdown hook for App related tasks
        Runtime.getRuntime().addShutdownHook(new AppHook(app));

        // Get config from the ipc endpoint for "list_*" actions
        if (!config.getAction().equals(AppConfig.ACTION_COLLECT)) {
            app.getJsonConfigs();
        }

        // Initiate JMX Connections, get attributes that match the yaml configuration
        app.init(false);

        // We don't want to loop if the action is list_* as it's just used for display information
        // about what will be collected
        if (config.getAction().equals(AppConfig.ACTION_COLLECT)) {
            // Start the main loop
            app.start();
        }
        return 0;
    }

    /** Attach a Shutdown Hook that will be called when SIGTERM is sent to JMXFetch. */
    private static void attachShutdownHook() {
        class ShutdownHook {
            public void attachShutDownHook() {
                Runtime.getRuntime()
                        .addShutdownHook(
                                new Thread() {
                                    @Override
                                    public void run() {
                                        LOGGER.info("JMXFetch is closing");
                                        // Properly close log handlers
                                        Enumeration<Appender> enume =
                                                (Enumeration<Appender>) LOGGER.getAllAppenders();
                                        while (enume.hasMoreElements()) {
                                            Appender appender = enume.nextElement();
                                            appender.close();
                                        }
                                    }
                                });
            }
        }

        new ShutdownHook().attachShutDownHook();
    }

    /** Sets reinitialization flag. */
    public void setReinit(boolean reinit) {
        this.reinit.set(reinit);
    }

    /** Returns loop counter with number of iterations performed on instances. */
    public static int getLoopCounter() {
        return loopCounter;
    }

    private static void clearInstances(List<Instance> instances) {
        Iterator<Instance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            instance.cleanUp();
            iterator.remove();
        }
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
            LOGGER.info("Named pipe for Auto-Discovery opened: " + pipeName);
        } catch (FileNotFoundException e) {
            LOGGER.info("Unable to open named pipe for Auto-Discovery: " + pipeName);
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
            LOGGER.debug("Attempting to apply config. Name: " + name + "\nconfig: \n" + config);
            InputStream stream = new ByteArrayInputStream(config.getBytes(UTF_8));
            YamlParser yaml = new YamlParser(stream);

            if (this.addConfig(name, yaml)) {
                reinit = true;
                LOGGER.debug("Configuration added succesfully reinit in order");
            } else {
                LOGGER.debug("Unable to apply configuration.");
            }
        }

        return reinit;
    }

    protected ArrayList<Instance> getInstances() {
        return this.instances;
    }

    void start() {
        // Main Loop that will periodically collect metrics from the JMX Server
        FileInputStream adPipe = null;

        if (appConfig.getAutoDiscoveryPipeEnabled()) {
            LOGGER.info("Auto Discovery enabled");
            adPipe = newAutoDiscoveryPipe();
            try {
                FileHelper.touch(new File(appConfig.getJmxLaunchFile()));
            } catch (IOException e) {
                LOGGER.warn(
                        "Unable to create launch file"
                        + " - Auto-Discovery configs will not be automatically resubmitted.");
            }
        }

        while (true) {
            // Exit on exit file trigger...
            if (appConfig.getExitWatcher().shouldExit()) {
                LOGGER.info("Exit file detected: stopping JMXFetch.");
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
                            if (Bytes.indexOf(minibuff, App.AD_LEGACY_CONFIG_TERM.getBytes()) > -1
                                    || Bytes.indexOf(minibuff, App.AD_CONFIG_TERM.getBytes())
                                            > -1) {
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
                LOGGER.warn(
                        "Unable to read from pipe" 
                        + "- Service Discovery configuration may have been skipped.");
            } catch (Exception e) {
                LOGGER.warn("Problem parsing auto-discovery configuration: " + e);
            }

            long start = System.currentTimeMillis();
            if (this.reinit.get()) {
                init(true);
            }

            if (instances.size() > 0) {
                doIteration();
            } else {
                LOGGER.warn("No instance could be initiated. Retrying initialization.");
                appConfig.getStatus().flush();
                configs = getConfigs(appConfig);
                init(true);
            }
            long duration = System.currentTimeMillis() - start;
            LOGGER.debug("Iteration ran in " + duration + " ms");
            // Sleep until next collection
            try {
                long loopPeriod = appConfig.getCheckPeriod();
                long sleepPeriod = (duration > loopPeriod ) ? loopPeriod : loopPeriod  - duration;
                LOGGER.debug("Sleeping for " + loopPeriod + " ms.");
                Thread.sleep(loopPeriod);
            } catch (InterruptedException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    void stop() {
        threadPoolExecutor.shutdownNow();
    }

    /** 
     * Iterates enabled instances collecting JMX metrics from them. 
     * Also attempts to fix any broken instances.
     * */
    public void doIteration() {
        int numberOfMetrics = 0;
        Reporter reporter = appConfig.getReporter();
        loopCounter++;


        try {
            List<Callable<LinkedList<HashMap<String, Object>>>> getMetricsTasks =
                new ArrayList<Callable<LinkedList<HashMap<String, Object>>>>();

            for(Instance instance : instances) {
                MetricCollectionTask task = new MetricCollectionTask(instance);
                getMetricsTasks.add(task);
            }

            List<Future<LinkedList<HashMap<String, Object>>>> results =
                threadPoolExecutor.invokeAll(getMetricsTasks, appConfig.getCollectionTimeout(), TimeUnit.SECONDS);

            for (int i=0; i<results.size(); i++) {
                LinkedList<HashMap<String, Object>> metrics;
                String instanceStatus = Status.STATUS_OK;
                String scStatus = Status.STATUS_OK;
                String instanceMessage = null;
                boolean evict = false;

                Future<LinkedList<HashMap<String, Object>>> future = results.get(i);
                Instance instance = instances.get(i); 

                try {
                    if (future.isDone()) {
                        metrics = future.get();
                        numberOfMetrics = metrics.size();

                        if (numberOfMetrics == 0) {
                            instanceMessage = "Instance " + instance + " didn't return any metrics";
                            LOGGER.warn(instanceMessage);
                            instanceStatus = Status.STATUS_ERROR;
                        } else if (instance.isLimitReached()) {
                            instanceMessage = "Number of returned metrics is too high for instance: "
                                    + instance.getName()
                                    + ". Please read http://docs.datadoghq.com/integrations/java/ or get in touch with Datadog "
                                    + "Support for more details. Truncating to " + instance.getMaxNumberOfMetrics() + " metrics.";

                            instanceStatus = Status.STATUS_WARNING;
                            // We don't want to log the warning at every iteration so we use this custom logger.
                            CustomLogger.laconic(LOGGER, Level.WARN, instanceMessage, 0);
                        }

                        if(numberOfMetrics > 0)
                            reporter.sendMetrics(metrics, instance.getName(), instance.getCanonicalRateConfig());

                    } else if (future.isCancelled()) {
                        instanceMessage = "metric collection could not be scheduled in time for: " + instance;
                        LOGGER.warn(instanceMessage);
                        instanceStatus = Status.STATUS_WARNING;
                        scStatus = Status.STATUS_WARNING;
                    }
                } catch (ExecutionException ee){
                    instanceMessage = "Unable to refresh bean list for instance " + instance;
                    LOGGER.warn(instanceMessage, ee.getCause());
                    instanceStatus = Status.STATUS_ERROR;
                    evict = true;
                } catch (CancellationException ee){
                    instanceMessage = "metric collection did not complete in time for: " + instance;
                    LOGGER.warn(instanceMessage, ee);
                    instanceStatus = Status.STATUS_ERROR;
                    evict = true;
                } catch (InterruptedException ie) {
                    instanceMessage = "Instance was interrupted completing bean list refresh for instance " + instance;
                    LOGGER.warn(instanceMessage, ie);
                    instanceStatus = Status.STATUS_ERROR;
                    evict = true;
                } finally {
                    if (evict) {
                        LOGGER.debug("Adding broken instance to list: " + instance.getName());
                        brokenInstances.add(instance);
                    }

                    if (instanceStatus == Status.STATUS_ERROR) {
                        scStatus = Status.STATUS_ERROR;
                    }

                    this.reportStatus(appConfig, reporter, instance, numberOfMetrics, instanceMessage, instanceStatus);
                    this.sendServiceCheck(reporter, instance, instanceMessage, scStatus);
                }
            }
        } catch (Exception e){
            String instanceMessage;
            String instanceStatus = Status.STATUS_ERROR;
            String scStatus = Status.STATUS_ERROR;

            LOGGER.warn("JMXFetch internal error invoking concurrent tasks: ", e);

            for (Instance instance : instances) {
                // don't add instances to broken instances, issue was internal
                instanceMessage = "Internal JMXFetch error refreshing bean list for instance " + instance;
                this.reportStatus(appConfig, reporter, instance, 0, instanceMessage, instanceStatus);
                this.sendServiceCheck(reporter, instance, instanceMessage, scStatus);
            }
        }

        // Attempt to fix broken instances
        fixBrokenInstances(reporter);

        try {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor)threadPoolExecutor;
            if (tpe.getPoolSize() == tpe.getActiveCount()) {
                // we have to replace the executor
                LOGGER.warn("Executor had to be replaced, previous one hogging threads");
                threadPoolExecutor.shutdownNow();
                threadPoolExecutor = Executors.newFixedThreadPool(appConfig.getThreadPoolSize());
            }

            appConfig.getStatus().flush();
        } catch (Exception e) {
            LOGGER.error("Unable to flush stats.", e);
        }
    }

    private void fixBrokenInstances(Reporter reporter) {
        List<Callable<Void>> fixInstanceTasks = new ArrayList<Callable<Void>>();
        List<Instance> newInstances = new ArrayList<Instance>();

        // We shuffle the broken instances to address starvation if first M
        // instances are always broken and our thread pool is M threads deep, 
        // then (N-M) instances could potentially never be fixed. This will
        // help address that problem. 
        //
        // N should be relatively small so the overhead should be acceptable.
        Collections.shuffle(brokenInstances);
        for(Instance instance : brokenInstances) {
            // Clearing rates aggregator so we won't compute wrong rates if we can reconnect
            reporter.clearRatesAggregator(instance.getName());

            LOGGER.warn("Instance " + instance + " didn't return any metrics. " +
                    "Maybe the server got disconnected ? Trying to reconnect.");

            // Remove the broken instance from the good instance list so jmxfetch won't try to
            // collect metrics from this broken instance during next collection and close
            // ongoing connections (do so asynchronously to avoid locking on network timeout).
            instances.remove(instance);
            instance.cleanUpAsync();

            // Resetting the instance
            Instance newInstance = new Instance(instance, appConfig);

            // create the initializing task
            InstanceInitializingTask task = new InstanceInitializingTask(newInstance);

            newInstances.add(newInstance);
            fixInstanceTasks.add(task);
        }

        // Run scheduled tasks to attempt to fix broken instances (reconnect)
        List<Integer> fixedInstanceIndices = new ArrayList<Integer>();
        try {
            List<Future<Void>> results = threadPoolExecutor.invokeAll(fixInstanceTasks,
                    appConfig.getReconnectionTimeout(), TimeUnit.SECONDS);

            for (int i=0; i<results.size(); i++) {
                String warning = null;
                Future<Void> future = results.get(i);

                try{
                    if (future.isDone()) {
                        future.get();

                        // If we get here all went well
                        instances.add(newInstances.get(i));
                        fixedInstanceIndices.add(i);  // i is the index in the brokenInstance list
                    }
                } catch (ExecutionException ee) {
                    Throwable e = ee.getCause();

                    if(e instanceof IOException ) {
                        warning =". Is a JMX Server running at this address?";
                    } else if (e instanceof SecurityException) {
                        warning =" because of bad credentials. Please check your credentials";
                    } else if (e instanceof FailedLoginException) {
                        warning =" because of bad credentials. Please check your credentials";
                    } else {
                        warning = " for an unknown reason." + e.getMessage();
                    }

                } catch (CancellationException ee){
                    warning = " because connection timed out and was canceled. Please check your network.";
                } catch (InterruptedException ie) {
                    warning = " attempt interrupted waiting on IO";
                } catch (Exception e) {
                    warning = " There was an unexpected exception: " + e.getMessage();
                } finally  {
                    if (warning != null) {
                        Instance instance = brokenInstances.get(i);
                        String msg = CANNOT_CONNECT_TO_INSTANCE + instance + warning;

                        LOGGER.warn(msg);

                        this.reportStatus(appConfig, reporter, instance, 0, msg, Status.STATUS_ERROR);
                        this.sendServiceCheck(reporter, instance, msg, Status.STATUS_ERROR);
                    }
                }
            }
        } catch(Exception e) {
            // Should we do anything else here?
            LOGGER.warn("JMXFetch internal error invoking concurrent tasks for broken instances: ", e);
        }

        // cleanup fixed brokenInstances - matching indices in fixedInstanceIndices List
        ListIterator<Integer> it = fixedInstanceIndices.listIterator(fixedInstanceIndices.size());
        while (it.hasPrevious()) {
            Integer idx = it.previous();
            brokenInstances.remove(idx.intValue());
        }

    }

    /** 
     * Adds a configuration to the auto-discovery pipe-collected
     * configuration list.  This method is deprecated.
     * */
    public boolean addConfig(String name, YamlParser config) {
        // named groups not supported with Java6:
        //
        // "AUTO_DISCOVERY_PREFIX(?<check>.{1,80})_(?<version>\\d{0,AD_MAX_MAG_INSTANCES})"
        // + 2 cause of underscores.
        if (name.length()
                > AUTO_DISCOVERY_PREFIX.length() + AD_MAX_NAME_LEN + AD_MAX_MAG_INSTANCES + 2) {
            LOGGER.debug("Name too long - skipping: " + name);
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
            LOGGER.debug("Cannot match instance name: " + name);
            return false;
        }

        // Java 6 doesn't allow name matching - group 1 is "check"
        String check = matcher.group(1);
        if (this.configs.containsKey(check)) {
            // there was already a file config for the check.
            LOGGER.debug("Key already present - skipping: " + name);
            return false;
        }

        this.adPipeConfigs.put(name, config);
        this.setReinit(true);

        return true;
    }

    /** 
     * Adds a configuration to the auto-discovery HTTP collected
     * configuration list (JSON). 
     * */
    public boolean addJsonConfig(String name, String json) {
        return false;
    }

    private ConcurrentHashMap<String, YamlParser> getConfigs(AppConfig config) {
        ConcurrentHashMap<String, YamlParser> configs = new ConcurrentHashMap<String, YamlParser>();

        loadFileConfigs(config, configs);
        loadResourceConfigs(config, configs);

        LOGGER.info("Found " + configs.size() + " config files");
        return configs;
    }

    private void loadFileConfigs(AppConfig config, ConcurrentHashMap<String, YamlParser> configs) {
        List<String> fileList = config.getYamlFileList();
        if (fileList != null) {
            for (String fileName : fileList) {
                File file = new File(config.getConfdDirectory(), fileName);
                String name = file.getName().replace(".yaml", "");
                String yamlPath = file.getAbsolutePath();
                FileInputStream yamlInputStream = null;
                LOGGER.info("Reading " + yamlPath);
                try {
                    yamlInputStream = new FileInputStream(yamlPath);
                    configs.put(name, new YamlParser(yamlInputStream));
                } catch (FileNotFoundException e) {
                    LOGGER.warn("Cannot find " + yamlPath);
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

    private void loadResourceConfigs(
            AppConfig config, ConcurrentHashMap<String, YamlParser> configs) {
        List<String> resourceConfigList = config.getInstanceConfigResources();
        if (resourceConfigList != null) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            for (String resourceName : resourceConfigList) {
                String name = resourceName.replace(".yaml", "");
                LOGGER.info("Reading " + resourceName);
                InputStream inputStream = classLoader.getResourceAsStream(resourceName);
                if (inputStream == null) {
                    LOGGER.warn("Cannot find " + resourceName);
                } else {
                    try {
                        configs.put(name, new YamlParser(inputStream));
                    } catch (Exception e) {
                        LOGGER.warn("Cannot parse yaml file " + resourceName, e);
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
                LOGGER.warn(
                        "Failed collecting JSON configs: ["
                                + response.getResponseCode()
                                + "] "
                                + response.getResponseBody());
                return update;
            } else if (response.getResponseCode() == 204) {
                LOGGER.debug("No configuration changes...");
                return update;
            }

            LOGGER.debug("Received the following JSON configs: " + response.getResponseBody());

            InputStream jsonInputStream = IOUtils.toInputStream(response.getResponseBody(), UTF_8);
            JsonParser parser = new JsonParser(jsonInputStream);
            int timestamp = ((Integer) parser.getJsonTimestamp()).intValue();
            if (timestamp > lastJsonConfigTs) {
                adJsonConfigs = (HashMap<String, Object>) parser.getJsonConfigs();
                lastJsonConfigTs = timestamp;
                update = true;
                LOGGER.debug("update is in order - updating timestamp: " + lastJsonConfigTs);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("error processing JSON response: " + e);
        } catch (IOException e) {
            LOGGER.error("unable to collect remote JMX configs: " + e);
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

        reporter.sendServiceCheck(checkName, status, message, instance.getServiceCheckTags());
        reporter.resetServiceCheckCount(checkName);
    }

    private void instantiate(
            LinkedHashMap<String, Object> instanceMap,
            LinkedHashMap<String, Object> initConfig,
            String checkName,
            AppConfig appConfig,
            boolean forceNewConnection) {

        Instance instance;
        Reporter reporter = appConfig.getReporter();

        try {
            instance = new Instance(instanceMap, initConfig, checkName, appConfig);
        } catch (Exception e) {
            String warning = "Unable to create instance. Please check your yaml file";
            appConfig.getStatus().addInitFailedCheck(checkName, warning, Status.STATUS_ERROR);
            LOGGER.error(warning, e);
            return;
        }

        try {
            //  initiate the JMX Connection
            instance.init(forceNewConnection);
            instances.add(instance);
        } catch (IOException e) {
            instance.cleanUp();
            brokenInstances.add(instance);
            String warning = CANNOT_CONNECT_TO_INSTANCE + instance + ". " + e.getMessage();
            this.reportStatus(appConfig, reporter, instance, 0, warning, Status.STATUS_ERROR);
            this.sendServiceCheck(reporter, instance, warning, Status.STATUS_ERROR);
            LOGGER.error(warning, e);
        } catch (Exception e) {
            instance.cleanUp();
            brokenInstances.add(instance);
            String warning =
                    "Unexpected exception while initiating instance "
                            + instance
                            + " : "
                            + e.getMessage();
            this.reportStatus(appConfig, reporter, instance, 0, warning, Status.STATUS_ERROR);
            this.sendServiceCheck(reporter, instance, warning, Status.STATUS_ERROR);
            LOGGER.error(warning, e);
        }
    }

    /** 
     * Initializes instances and metric collection. 
     * */
    public void init(boolean forceNewConnection) {
        clearInstances(instances);
        clearInstances(brokenInstances);

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

            ArrayList<LinkedHashMap<String, Object>> configInstances =
                    ((ArrayList<LinkedHashMap<String, Object>>) yamlConfig.getYamlInstances());
            if (configInstances == null || configInstances.size() == 0) {
                String warning = "No instance found in :" + name;
                LOGGER.warn(warning);
                appConfig.getStatus().addInitFailedCheck(name, warning, Status.STATUS_ERROR);
                continue;
            }

            for (LinkedHashMap<String, Object> configInstance : configInstances) {
                // Create a new Instance object
                instantiate(
                        configInstance,
                        (LinkedHashMap<String, Object>) yamlConfig.getInitConfig(),
                        name,
                        appConfig,
                        forceNewConnection);
            }
        }

        // Process JSON configurations
        if (adJsonConfigs != null) {
            for (String check : adJsonConfigs.keySet()) {
                HashMap<String, Object> checkConfig =
                        (HashMap<String, Object>) adJsonConfigs.get(check);
                LinkedHashMap<String, Object> initConfig =
                        (LinkedHashMap<String, Object>) checkConfig.get("init_config");
                ArrayList<LinkedHashMap<String, Object>> configInstances =
                        (ArrayList<LinkedHashMap<String, Object>>) checkConfig.get("instances");
                String checkName = (String) checkConfig.get("check_name");
                for (LinkedHashMap<String, Object> configInstance : configInstances) {
                    instantiate(
                            configInstance, initConfig, checkName, appConfig, forceNewConnection);
                }
            }
        }
    }
}
