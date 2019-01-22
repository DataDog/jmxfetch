package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import  org.mockito.Spy;
import static org.mockito.Mockito.*;

import java.lang.management.ManagementFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.log4j.Level;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.util.CustomLogger;
import org.junit.After;
import org.junit.BeforeClass;

import com.beust.jcommander.JCommander;


public class TestCommon {
    AppConfig appConfig = spy(new AppConfig());
    App app;
    MBeanServer mbs;
    ArrayList<ObjectName> objectNames = new ArrayList<ObjectName>();
    LinkedList<HashMap<String, Object>> metrics;
    LinkedList<HashMap<String, Object>> serviceChecks;

    /**
     * Setup logger.
     */
    @BeforeClass
    public static void init() throws Exception {
        CustomLogger.setup(Level.toLevel("ALL"), "/tmp/jmxfetch_test.log");
    }

    /**
     * Register a MBean with the given name, and application attributes.
     * @throws NotCompliantMBeanException
     * @throws MBeanRegistrationException
     * @throws InstanceAlreadyExistsException
     * @throws MalformedObjectNameException
     */
    protected void registerMBean(Object application, String objectStringName) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
        mbs = (mbs == null)? ManagementFactory.getPlatformMBeanServer() : mbs;
        ObjectName objectName = new ObjectName(objectStringName);
        objectNames.add(objectName);
        mbs.registerMBean(application, objectName);
    }

    /**
     * Unregister MBeans.
     * Note: executed after execution of every test.
     * @throws InstanceNotFoundException
     * @throws MBeanRegistrationException
     */
    @After
    public void unregisterMBean() throws MBeanRegistrationException, InstanceNotFoundException{
        if (mbs != null) {
            for (ObjectName objectName: objectNames ) {
                mbs.unregisterMBean(objectName);
            }
        }
    }

    /**
     * Init JMXFetch with the given YAML configuration file.
     */
    protected void initApplication(String yamlFileName, String autoDiscoveryPipeFile) throws FileNotFoundException, IOException {
        // We do a first collection
        // We initialize the main app that will collect these metrics using JMX
        String confdDirectory = Thread.currentThread().getContextClassLoader().getResource(yamlFileName).getPath();
        confdDirectory = new String(confdDirectory.substring(0, confdDirectory.length() - yamlFileName.length()));
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
            params.add(5, "/foo"); //could be anything we're stubbing it out
            params.add(6, "--sd_enabled");
        }
        new JCommander(appConfig, params.toArray(new String[params.size()]));

       if (sdEnabled) {
           String autoDiscoveryPipe = Thread.currentThread().getContextClassLoader().getResource(
                   autoDiscoveryPipeFile).getPath();
           when(appConfig.getAutoDiscoveryPipe()).thenReturn(autoDiscoveryPipe); //mocking with fixture file.
       }

        app = new App(appConfig);
        if (sdEnabled) {
            FileInputStream sdPipe = new FileInputStream(appConfig.getAutoDiscoveryPipe());
            int len = sdPipe.available();
            byte[] buffer = new byte[len];
            sdPipe.read(buffer);
            app.setReinit(app.processAutoDiscovery(buffer));
        }

        app.init(false);
    }

    protected void initApplication(String yamlFileName) throws FileNotFoundException, IOException {
        initApplication(yamlFileName, "");
    }

    /**
     * Run a JMXFetch iteration.
     */
    protected void run(){
        if (app != null) {
            app.doIteration();
            metrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
        }
    }

    /**
     * Return configured instances
     */
    protected ArrayList<Instance> getInstances() {
        return app.getInstances();
    }

    /**
     * Return JMXFetch reporter.
     */
    protected Reporter getReporter(){
        return appConfig.getReporter();
    }

    /**
     * Return the metrics collected by JMXFetch.
     */
    protected LinkedList<HashMap<String, Object>> getMetrics(){
        return metrics;
    }

    /**
     * Return the service checks collected by JMXFetch.
     */
    protected LinkedList<HashMap<String, Object>> getServiceChecks(){
        return ((ConsoleReporter) appConfig.getReporter()).getServiceChecks();
    }

    /**
     * Assert that a specific metric was collected.
     * Brand the metric so we can easily know which metric have/have not been tested
     *
     * @param name              metric name
     *
     * @param value             metric value
     *
     * @param lowerBound        lower bound metric value
     *
     * @param upperBound        upper bound metric value
     *
     * @param commonTags        metric tags inherited from the instance configuration
     *
     * @param additionalTags    metric tags inherited from the bean properties
     *
     * @param countTags         number of metric tags
     *
     * @param metricType        type of the metric (gauge, histogram, ...)
     *
     * @return                  fail if the metric was not found
     */
    public void assertMetric(String name, Number value, Number lowerBound, Number upperBound, List<String> commonTags, List<String> additionalTags, int countTags, String metricType){
        List<String> tags = new ArrayList<String>(commonTags);
        tags.addAll(additionalTags);

        for (HashMap<String, Object> m: metrics) {
            String mName = (String) (m.get("name"));
            Double mValue = (Double) (m.get("value"));
            Set<String> mTags = new HashSet<String>(Arrays.asList((String[]) (m.get("tags"))));

            if (mName.equals(name)) {

                if (!value.equals(-1)){
                    assertEquals((Double)value.doubleValue(), mValue);
                } else if (!lowerBound.equals(-1) || !upperBound.equals(-1)){
                    assertTrue(mValue > (Double)lowerBound.doubleValue());
                    assertTrue(mValue < (Double)upperBound.doubleValue());
                }

                if (countTags != -1) {
                    assertEquals(countTags, mTags.size());
                }
                for (String t: tags) {
                    assertTrue(mTags.contains(t));
                }

                if (metricType != null) {
                    assertEquals(metricType, m.get("type"));
                }
                // Brand the metric
                m.put("tested", true);

                return;
            }
        }
        fail("Metric assertion failed (name: "+name+", value: "+value+", tags: "+tags+", #tags: "+countTags+").");
    }

    public void assertMetric(String name, Number value, Number lowerBound, Number upperBound, List<String> commonTags, List<String> additionalTags, int countTags) {
        assertMetric(name, value, lowerBound, upperBound, commonTags, additionalTags, countTags, null);
    }

    public void assertMetric(String name, Number value, List<String> commonTags, List<String> additionalTags, int countTags){
        assertMetric(name, value, -1, -1, commonTags, additionalTags, countTags, null);
    }

    public void assertMetric(String name, Number value, List<String> commonTags, List<String> additionalTags, int countTags, String metricType){
        assertMetric(name, value, -1, -1, commonTags, additionalTags, countTags, metricType);
    }

    public void assertMetric(String name, Number lowerBound, Number upperBound, List<String> commonTags, List<String> additionalTags, int countTags){
        assertMetric(name, -1, lowerBound, upperBound, commonTags, additionalTags, countTags, null);
    }
    public void assertMetric(String name, Number lowerBound, Number upperBound, List<String> commonTags, List<String> additionalTags, int countTags, String metricType){
        assertMetric(name, -1, lowerBound, upperBound, commonTags, additionalTags, countTags, metricType);
    }

    public void assertMetric(String name, Number value, List<String> tags, int countTags){
        assertMetric(name, value, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(String name, Number value, List<String> tags, int countTags, String metricType){
        assertMetric(name, value, tags, new ArrayList<String>(), countTags, metricType);
    }

    public void assertMetric(String name, Number lowerBound, Number upperBound, List<String> tags, int countTags){
        assertMetric(name, lowerBound, upperBound, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(String name, Number lowerBound, Number upperBound, List<String> tags, int countTags, String metricType){
        assertMetric(name, lowerBound, upperBound, tags, new ArrayList<String>(), countTags, metricType);
    }

    public void assertMetric(String name, List<String> tags, int countTags){
        assertMetric(name, -1, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(String name, List<String> tags, int countTags, String metricType){
        assertMetric(name, -1, tags, new ArrayList<String>(), countTags, metricType);
    }

    /**
     * Assert that all  -excluding JVM related- metrics were tested.
     *
     * @return          fail if a metric was not tested
     */
    public void assertCoverage(){
        int totalMetrics = 0;
        LinkedList<HashMap<String, Object>> untestedMetrics = new LinkedList<HashMap<String, Object>>();

        for (HashMap<String, Object> m: metrics) {
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

        if (untestedMetrics.size() > 0) {
            String message = generateReport(untestedMetrics, totalMetrics);
            fail(message);
        }
        return;
    }

    /**
     * Generate a report with untested metrics.
     *
     * @return          String report
     */
    private static String generateReport(LinkedList<HashMap<String, Object>> untestedMetrics, int totalMetricsCount){
        StringBuilder sb = new StringBuilder();

        // Compute indicators
        int testedMetricsCount = totalMetricsCount - untestedMetrics.size();
        int coverageMetrics = (int)((testedMetricsCount * 100.0f) / totalMetricsCount);

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
        for (HashMap<String, Object> m: untestedMetrics) {
            sb.append(m);
            sb.append("\n");
        }
        sb.append("========================================\n");
        return sb.toString();

    }

}
