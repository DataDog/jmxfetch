package org.datadog.jmxfetch;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import static org.junit.Assert.*;

public class TestApp extends TestCommon {

    @Test
    public void testBeanTags() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=CoolScope,host=localhost,component=");
        initApplication("jmx_bean_tags.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, metrics.size());


        // Fetching our 'defined' metric tags
        for (HashMap<String, Object> m : metrics) {
            String name = (String) (m.get("name"));
            if(!name.equals("this.is.100")){
                continue;
            }
            String[] tags = (String[]) (m.get("tags"));
            Set<String> tagsSet = new HashSet<String>(Arrays.asList(tags));

            // We should find bean parameters as tags
            assertEquals(6, tags.length);
            assertEquals(true, tagsSet.contains("type:SimpleTestJavaApp"));
            assertEquals(true, tagsSet.contains("scope:CoolScope"));
            assertEquals(true, tagsSet.contains("instance:jmx_test_instance"));
            assertEquals(true, tagsSet.contains("jmx_domain:org.datadog.jmxfetch.test"));
            // Special case of the 'host' parameter which tag is renamed to 'bean_host'
            assertEquals(true, tagsSet.contains("bean_host:localhost"));
            // Empty values should also be added as tags, without the colon
            assertEquals(true, tagsSet.contains("component"));
        }
    }

    /**
     * Check JMXFetch Cassandra metric aliasing logic, i.e. compliant with CASSANDRA-4009
     * when `cassandra4009` flag is enabled, or default.
     *
     * More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
     */
    @Test
    public void testCassandraBean() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.apache.cassandra.metrics:keyspace=MyKeySpace,type=ColumnFamily,scope=MyColumnFamily,name=PendingTasks");
        initApplication("jmx_cassandra.yaml");


        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 14 = 2*13 metrics from java.lang + 2*1 metric explicitly defined in the yaml config file
        assertEquals(28, metrics.size());

        // Assert compliancy with CASSANDRA-4009
        ArrayList<String> tags = new ArrayList<String>() {{
            add("type:ColumnFamily");
            add("keyspace:MyKeySpace");
            add("ColumnFamily:MyColumnFamily");
            add("jmx_domain:org.apache.cassandra.metrics");
            add("instance:jmx_first_instance");
        }};

        assertMetric("cassandra.pending_tasks.should_be100", tags, 5);

        // Default behavior
        tags = new ArrayList<String>() {{
            add("type:ColumnFamily");
            add("scope:MyColumnFamily");
            add("keyspace:MyKeySpace");
            add("jmx_domain:org.apache.cassandra.metrics");
            add("instance:jmx_second_instance");
            add("name:PendingTasks");
        }};

        assertMetric("cassandra.metrics.should_be1000", tags, 6);
    }

    @Test
    public void testCassandraDeprecatedBean() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.apache.cassandra.db:type=ColumnFamilies,keyspace=MyKeySpace,columnfamily=MyColumnFamily");
        initApplication("jmx_cassandra_deprecated.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, metrics.size());

        ArrayList<String> tags = new ArrayList<String>() {{
            add("type:ColumnFamilies");
            add("keyspace:MyKeySpace");
            add("columnfamily:MyColumnFamily");
            add("jmx_domain:org.apache.cassandra.db");
            add("instance:jmx_test_instance");
        }};

        assertMetric("cassandra.db.should_be100", tags, 5);
    }

    @Test
    public void testDomainInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.includeme:type=AType");
        initApplication("jmx_domain_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 28 = 13 metrics from java.lang + 15 metrics implicitly defined
        assertEquals(28, metrics.size());
    }

    @Test
    public void testDomainExclude() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.includeme:type=AType");
        registerMBean(testApp, "org.datadog.jmxfetch.excludeme:type=AnotherType");

        // Initializing application
        initApplication("jmx_domain_exclude.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 2 metrics explicitly define- 1 implicitly defined in the exclude section
        assertEquals(14, metrics.size());
    }

    @Test
    public void testDomainRegex() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.includeme:type=AType");
        registerMBean(testApp, "org.datadog.jmxfetch.includeme.too:type=AType");
        registerMBean(testApp, "org.datadog.jmxfetch.includeme.not.me:type=AType");

        // Initializing application
        initApplication("jmx_domain_regex.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 15 = 13 metrics from java.lang + 3 metrics explicitly defined - 1 implicitly defined in exclude section
        assertEquals(15, metrics.size());
    }

    @Test
    public void testParameterMatch() throws Exception {
        // Do not match beans which do not contain types specified in the conf
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:param=AParameter");

        // Initializing application
        initApplication("jmx_list_params_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 13 default metrics from java.lang
        assertEquals(13, metrics.size());
    }

    @Test
    public void testListParamsInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=bar,type=RightType");

        // Initializing application
        initApplication("jmx_list_params_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(14, metrics.size());
    }

    @Test
    public void testListParamsExclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=RightType");

        // Initializing application
        initApplication("jmx_list_params_exclude.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 13 = 13 metrics from java.lang + 2 metrics explicitly defined - 2 explicitly defined
        assertEquals(13, metrics.size());
    }

    @Test
    public void testListBeansInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=IncludeMe");

        // Initializing application
        initApplication("jmx_list_beans_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(14, metrics.size());
    }

    @Test
    public void testListBeansRegexInclude() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=IncludeMe");
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=IncludeMeToo");
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=RightType");

        // Initializing application
        initApplication("jmx_list_beans_regex_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 15 = 13 metrics from java.lang + 2 metrics explicitly defined
        assertEquals(15, metrics.size());
    }

    @Test
    public void testListBeansRegexExclude() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=IncludeMe");
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=ExcludeMe,scope=InScope");
        registerMBean(testApp, "org.datadog.jmxfetch.test:scope=OutOfScope");

        // Initializing application
        initApplication("jmx_list_beans_regex_exclude.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(14, metrics.size());
    }

    @Test
    public void testListBeansExclude() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=IncludeMe");
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=ExcludeMe");
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=ExcludeMeToo");

        // Initializing application
        initApplication("jmx_list_beans_exclude.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(14, metrics.size());
    }

    @Test
    public void testExitWatcher() throws Exception {
        // Test the ExitWatcher logic

        // Create a temp file
        File temp = File.createTempFile("exit-jmxfetch-file-name", ".tmp");
        temp.deleteOnExit();

        ExitWatcher exitWatcher = new ExitWatcher(temp.getAbsolutePath());
        assertTrue(exitWatcher.shouldExit());

        temp.delete();
        assertFalse(exitWatcher.shouldExit());
    }

    /**
     * FIXME: Split this test in multiple sub-tests.
     */
    @Test
    public void testApp() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean( testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx.yaml");

        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        assertEquals(27, metrics.size()); // 27 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + the 9 gauges that is implicitly collected, see jmx.yaml in the test/resources folder

        // We test for the presence and the value of the metrics we want to collect
        ArrayList<String> commonTags = new ArrayList<String>() {{
            add("instance:jmx_test_instance");
            add("env:stage");
            add("newTag:test");
        }};

        assertMetric("this.is.100", 100.0, commonTags, new ArrayList<String>() {{add("foo");add("gorch");add("bar:baz");}} , 8);
        assertMetric("jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242",4.2424242E7, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 5);
        assertMetric("test.converted", 5.0, commonTags, 5);
        assertMetric("test.boolean", 1.0, commonTags, 5);
        assertMetric("test.defaulted", 32.0, commonTags, 5);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 5);
        assertCoverage();

        // We run a second collection. The counter should now be present
        run();
        metrics = getMetrics();
        assertEquals(29, metrics.size()); // 29 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 9 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

        // We test for the same metrics but this time, the counter should be here
        // Previous metrics
        assertMetric("this.is.100", 100.0, commonTags, 8);
        assertMetric("jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242",4.2424242E7, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 5);
        assertMetric("test.converted", 5.0, commonTags, 5);
        assertMetric("test.boolean", 1.0, commonTags, 5);
        assertMetric("test.defaulted", 32.0, commonTags, 5);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 5);

        // Counters
        assertMetric("subattr.counter", 0.0, commonTags, 5);
        assertMetric("test.counter", 0.0, commonTags, 5);
        assertCoverage();

        // We run a 3rd collection but this time we increment the counter and we sleep
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);

        run();
        metrics = getMetrics();
        assertEquals(metrics.size(), 29); // 28 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 9 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

        // Previous metrics
        assertMetric("this.is.100", 100.0, commonTags, 8);
        assertMetric("jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242",4.2424242E7, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 5);
        assertMetric("test.converted", 5.0, commonTags, 5);
        assertMetric("test.boolean", 1.0, commonTags, 5);
        assertMetric("test.defaulted", 32.0, commonTags, 5);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 5);

        // Counter
        assertMetric("subattr.counter", 0.98, 1, commonTags, 5);
        assertMetric("test.counter", 0.98, 1, commonTags, 5);
        assertCoverage();
    }
}
