package org.datadog.jmxfetch;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.util.CustomLogger;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.junit.After;
import org.junit.BeforeClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.beust.jcommander.JCommander;
import org.apache.log4j.Level;


public class TestCommon {
    AppConfig appConfig = new AppConfig();
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
    protected void initApplication(String yamlFileName){
        // We do a first collection
        // We initialize the main app that will collect these metrics using JMX
        String confdDirectory = Thread.currentThread().getContextClassLoader().getResource(yamlFileName).getPath();
        confdDirectory = new String(confdDirectory.substring(0, confdDirectory.length() - yamlFileName.length()));
        String[] params = {"--reporter", "console", "-c", yamlFileName, "--conf_directory", confdDirectory, "collect"};
        new JCommander(appConfig, params);

        app = new App(appConfig);
        app.init(false);
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
     * @return                  fail if the metric was not found
     */
    public void assertMetric(String name, Number value, Number lowerBound, Number upperBound, ArrayList<String> commonTags, ArrayList<String> additionalTags, int countTags){
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
                // Brand the metric
                m.put("tested", true);

                return;
            }
        }
        fail("Metric assertion failed (name: "+name+", value: "+value+", tags: "+tags+", #tags: "+countTags+").");
    }

    public void assertMetric(String name, Number value, ArrayList<String> commonTags, ArrayList<String> additionalTags, int countTags){
        assertMetric(name, value, -1, -1, commonTags, additionalTags, countTags);
    }

    public void assertMetric(String name, Number lowerBound, Number upperBound, ArrayList<String> commonTags, ArrayList<String> additionalTags, int countTags){
        assertMetric(name, -1, lowerBound, upperBound, commonTags, additionalTags, countTags);
    }

    public void assertMetric(String name, Number value, ArrayList<String> tags, int countTags){
        assertMetric(name, value, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(String name, Number lowerBound, Number upperBound, ArrayList<String> tags, int countTags){
        assertMetric(name, lowerBound, upperBound, tags, new ArrayList<String>(), countTags);
    }

    public void assertMetric(String name, ArrayList<String> tags, int countTags){
        assertMetric(name, -1, tags, new ArrayList<String>(), countTags);
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
            Double mValue = (Double) (m.get("value"));
            Set<String> mTags = new HashSet<String>(Arrays.asList((String[]) (m.get("tags"))));

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
