package org.datadog.jmxfetch;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.beust.jcommander.JCommander;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.junit.After;
import org.junit.BeforeClass;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.util.CustomLogger;
import org.datadog.jmxfetch.util.MetricsAssert;
import org.datadog.jmxfetch.util.LogLevel;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class TestCommon {
    AppConfig appConfig = spy(AppConfig.builder().build());
    App app;
    MBeanServer mbs;
    List<ObjectName> objectNames = new ArrayList<>();
    List<Map<String, Object>> metrics = new ArrayList<>();
    List<Map<String, Object>> serviceChecks;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** Setup logger. */
    @BeforeClass
    public static void init() {
        String level = System.getProperty("tests.log_level");
        if (level == null) {
            level = "ALL";
        }
        CustomLogger.setup(LogLevel.fromString(level), "/tmp/jmxfetch_test.log", false);
    }

    /**
     * Register a MBean with the given name, and application attributes.
     *
     * @throws NotCompliantMBeanException
     * @throws MBeanRegistrationException
     * @throws InstanceAlreadyExistsException
     * @throws MalformedObjectNameException
     */
    protected void registerMBean(Object application, String objectStringName)
            throws InstanceAlreadyExistsException, MBeanRegistrationException,
                    NotCompliantMBeanException, MalformedObjectNameException {
        mbs = (mbs == null) ? ManagementFactory.getPlatformMBeanServer() : mbs;
        ObjectName objectName = new ObjectName(objectStringName);
        objectNames.add(objectName);
        mbs.registerMBean(application, objectName);
    }

    /**
     * Unregister MBeans. Note: executed after execution of every test.
     *
     * @throws InstanceNotFoundException
     * @throws MBeanRegistrationException
     */
    public void unregisterMBeans() throws MBeanRegistrationException, InstanceNotFoundException {
        if (mbs != null) {
            for (ObjectName objectName : objectNames) {
                mbs.unregisterMBean(objectName);
            }
            this.objectNames.clear();
        }
    }

    /**
     * Tear down instances and application.
     */
    @After
    public void teardown() {
        this.metrics.clear();
        if (app != null) {
            app.clearAllInstances();
            app.stop();
        }
        try {
            unregisterMBeans();
        } catch (MBeanRegistrationException | InstanceNotFoundException e) {
            // Ignore
        }
    }

    /** Init JMXFetch with the given YAML configuration file. */
    protected void initApplication(String yamlFileName, String autoDiscoveryPipeFile)
            throws FileNotFoundException, IOException {
        // We do a first collection
        // We initialize the main app that will collect these metrics using JMX
        String confdDirectory =
                Thread.currentThread().getContextClassLoader().getResource(yamlFileName).getPath();
        confdDirectory = confdDirectory.substring(0, confdDirectory.length() - yamlFileName.length());
        List<String> params = new ArrayList<String>();
        boolean sdEnabled = (autoDiscoveryPipeFile.length() > 0);
        params.add("--reporter");
        params.add("console");

        if (confdDirectory != null) {
            params.add("-c");
            params.add(yamlFileName);
            params.add("--conf_directory");
            params.add(confdDirectory);
            params.add("collect");
        }

        if (sdEnabled) {
            params.add(4, "--tmp_directory");
            params.add(5, "/foo"); // could be anything we're stubbing it out
            params.add(6, "--sd_enabled");
        }
        new JCommander(appConfig, params.toArray(new String[0]));

        if (sdEnabled) {
            String autoDiscoveryPipe =
                    Thread.currentThread()
                            .getContextClassLoader()
                            .getResource(autoDiscoveryPipeFile)
                            .getPath();
            when(appConfig.getAutoDiscoveryPipe())
                    .thenReturn(autoDiscoveryPipe); // mocking with fixture file.
        }

        app = new App(appConfig);
        if (sdEnabled) {
            try (FileInputStream sdPipe = new FileInputStream(appConfig.getAutoDiscoveryPipe())) {
                int len = sdPipe.available();
                byte[] buffer = new byte[len];
                sdPipe.read(buffer);
                app.setReinit(app.processAutoDiscovery(buffer));
            }
        }

        app.init(false);
    }

    protected void initApplication(String yamlFileName) throws IOException {
        initApplication(yamlFileName, "");
    }

    /*
     * Init JMXFetch with the given YAML configuration template
     * The configuration can be specified as a yaml literal with each arg
     * representing one line of the Yaml file
     * Does not support any SD/AD features.
     *
     * @param yamlConfigContents the YAML configuration contents, each representing a configuration file
    */
    protected void initApplicationWithYamlLines(Path... yamlConfigs) {
        if (yamlConfigs == null || yamlConfigs.length == 0) {
            throw new IllegalArgumentException("yamlConfigContents cannot be null or empty");
        }

        String confdDirectory = yamlConfigs[0].getParent().toString();

        List<String> params = new ArrayList<>();
        params.add("--reporter");
        params.add("console");

        for (Path yamlConfig : yamlConfigs) {
            params.add("-c");
            params.add(yamlConfig.getFileName().toString());
        }

        params.add("--conf_directory");
        params.add(confdDirectory);
        params.add("collect");

        new JCommander(appConfig, params.toArray(new String[0]));
        this.app = new App(appConfig);
        app.init(false);
    }

    public Path makeTempYamlConfigFile(String yamlName, List<String> lines) {
        try {
            return Files.write(
                    Files.createTempFile(temporaryFolder.getRoot().toPath(), yamlName, ".yaml"),
                    lines,
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Run a JMXFetch iteration. */
    protected void run() {
        if (app != null) {
            this.metrics.clear();
            app.doIteration();
            this.metrics.addAll(((ConsoleReporter) appConfig.getReporter()).getMetrics());
        }
    }

    /** Return configured instances */
    protected List<Instance> getInstances() {
        return app.getInstances();
    }

    /** Return JMXFetch reporter. */
    protected Reporter getReporter() {
        return appConfig.getReporter();
    }

    /** Return the metrics collected by JMXFetch. */
    protected List<Map<String, Object>> getMetrics() {
        return metrics;
    }

    /** Return the service checks collected by JMXFetch. */
    protected List<Map<String, Object>> getServiceChecks() {
        return ((ConsoleReporter) appConfig.getReporter()).getServiceChecks();
    }

    /**
     * Assert that a specific metric was collected. Brand the metric so we can easily know which
     * metric have/have not been tested
     *
     * @param name metric name
     * @param value metric value
     * @param lowerBound lower bound metric value
     * @param upperBound upper bound metric value
     * @param commonTags metric tags inherited from the instance configuration
     * @param additionalTags metric tags inherited from the bean properties
     * @param countTags number of metric tags
     * @param metricType type of the metric (gauge, histogram, ...)
     * @throws AssertionError if the metric was not found
     */
    public void assertMetric(
            String name,
            Number value,
            Number lowerBound,
            Number upperBound,
            List<String> commonTags,
            List<String> additionalTags,
            int countTags,
            String metricType) {
        MetricsAssert.assertMetric(name, value, lowerBound, upperBound, commonTags, additionalTags, countTags, metricType, this.metrics);
    }

    public void assertMetric(
            String name,
            Number value,
            Number lowerBound,
            Number upperBound,
            List<String> commonTags,
            List<String> additionalTags,
            int countTags) {
        assertMetric(
                name, value, lowerBound, upperBound, commonTags, additionalTags, countTags, null);
    }

    public void assertMetric(
            String name,
            Number value,
            List<String> commonTags,
            List<String> additionalTags,
            int countTags) {
        assertMetric(name, value, -1, -1, commonTags, additionalTags, countTags, null);
    }

    public void assertMetric(
            String name,
            Number value,
            List<String> commonTags,
            List<String> additionalTags,
            int countTags,
            String metricType) {
        assertMetric(name, value, -1, -1, commonTags, additionalTags, countTags, metricType);
    }

    public void assertMetric(
            String name,
            Number lowerBound,
            Number upperBound,
            List<String> commonTags,
            List<String> additionalTags,
            int countTags) {
        assertMetric(name, -1, lowerBound, upperBound, commonTags, additionalTags, countTags, null);
    }

    public void assertMetric(
            String name,
            Number lowerBound,
            Number upperBound,
            List<String> commonTags,
            List<String> additionalTags,
            int countTags,
            String metricType) {
        assertMetric(
                name,
                -1,
                lowerBound,
                upperBound,
                commonTags,
                additionalTags,
                countTags,
                metricType);
    }

    public void assertMetric(String name, Number value, List<String> tags, int countTags) {
        assertMetric(name, value, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(
            String name, Number value, List<String> tags, int countTags, String metricType) {
        assertMetric(name, value, tags, new ArrayList<String>(), countTags, metricType);
    }

    public void assertMetric(
            String name, Number lowerBound, Number upperBound, List<String> tags, int countTags) {
        assertMetric(name, lowerBound, upperBound, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(
            String name,
            Number lowerBound,
            Number upperBound,
            List<String> tags,
            int countTags,
            String metricType) {
        assertMetric(
                name, lowerBound, upperBound, tags, new ArrayList<String>(), countTags, metricType);
    }

    public void assertMetric(String name, List<String> tags, int countTags) {
        assertMetric(name, -1, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(String name, List<String> tags, int countTags, String metricType) {
        assertMetric(name, -1, tags, new ArrayList<String>(), countTags, metricType);
    }

    /**
     * Assert that all -excluding JVM related- metrics were tested.
     *
     * @return fail if a metric was not tested
     */
    public void assertCoverage() {
        int totalMetrics = 0;
        List<Map<String, Object>> untestedMetrics = new ArrayList<Map<String, Object>>();

        for (Map<String, Object> m : metrics) {
            String mName = (String) (m.get("name"));

            // Exclusion logic
            if (mName.startsWith("jvm.")) {
                continue;
            }

            // End of exclusion logic
            totalMetrics += 1;
            if (!m.containsKey("tested")) {
                untestedMetrics.add(m);
            }
        }

        if (!untestedMetrics.isEmpty()) {
            String message = generateReport(untestedMetrics, totalMetrics);
            fail(message);
        }
        return;
    }

    /**
     * Generate a report with untested metrics.
     *
     * @return String report
     */
    private static String generateReport(
            List<Map<String, Object>> untestedMetrics, int totalMetricsCount) {
        StringBuilder sb = new StringBuilder();

        // Compute indicators
        int testedMetricsCount = totalMetricsCount - untestedMetrics.size();
        int coverageMetrics = (int) ((testedMetricsCount * 100.0f) / totalMetricsCount);

        sb.append("Coverage\n");
        sb.append("========================================\n");
        sb.append("\tMETRICS\n");
        sb.append("\t\tTested ");
        sb.append(testedMetricsCount);
        sb.append("/");
        sb.append(totalMetricsCount);
        sb.append(" (");
        sb.append(coverageMetrics);
        sb.append("%)\n");
        sb.append("\t\tUNTESTED: \n");
        for (Map<String, Object> m : untestedMetrics) {
            sb.append(m);
            sb.append("\n");
        }
        sb.append("========================================\n");
        return sb.toString();
    }
}
