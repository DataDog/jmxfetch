package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

public class TestApp extends TestCommon {

    /**
     * Tag metrics with MBean parameters based on user supplied regex
     */
    @Test
    public void testBeanRegexTags() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        initApplication("jmx_bean_regex_tags.yaml");

        // Run the collection
        run();

        List<String> tags = Arrays.asList(
            "type:SimpleTestJavaApp",
            "scope:CoolScope",
            "instance:jmx_test_instance",
            "jmx_domain:org.datadog.jmxfetch.test",
            "bean_host:localhost",
            "component",
            "hosttag:localhost",
            "nonExistantTag:$2",
            "nonRegexTag:value"
        );

        assertMetric("this.is.100", tags, 9);

    }

    /**
     * Tag metrics with MBeans parameters.
     *
     */
    @Test
    public void testBeanTags() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        initApplication("jmx_bean_tags.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 11 = 10 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(11, metrics.size());

        List<String> tags = Arrays.asList(
            "type:SimpleTestJavaApp",
            "scope:CoolScope",
            "instance:jmx_test_instance",
            "jmx_domain:org.datadog.jmxfetch.test",
            "bean_host:localhost",
            "component"
        );

        assertMetric("this.is.100", tags, 6);
    }

    /**
     * Generate metric aliases from a `alias_match` regular expression.
     */
    @Test
    public void testRegexpAliasing() throws Exception {
        // Expose MBeans
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_alias_match.yaml");

        // Collect metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // Assertions

        // 12 metrics = 10 from `java.lang` + 2 from the user configuration file
        assertEquals(12, metrics.size());

        // Metric aliases are generated from `alias_match`
        List<String> tags = Arrays.asList(
            "jmx_domain:org.datadog.jmxfetch.test",
            "instance:jmx_test_instance",
            "foo:Bar",
            "qux:Baz"
        );

        assertMetric("this.is.100.bar.baz", tags, 4);
        assertMetric("org.datadog.jmxfetch.test.baz.hashmap.thisis0", tags, 4);
    }

    /**
     * Test that specifying no alias on an attribute defined with a detailed hashmap works and picks up a valid default alias
     */
    @Test
    public void testNoAliasOnDetailedAttribute() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_no_alias.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // Assertions

        // 11 metrics = 10 from `java.lang` + 1 from the user configuration file
        assertEquals(11, metrics.size());

        // Metric aliases are generated from `alias_match`
        List<String> tags = Arrays.asList(
                "jmx_domain:org.datadog.jmxfetch.test",
                "instance:jmx_test_instance",
                "foo:Bar",
                "qux:Baz"
        );

        assertMetric("jmx.org.datadog.jmxfetch.test.should_be100", tags, 4);
    }

    /**
     * Check JMXFetch Cassandra metric aliasing logic, i.e. compliant with CASSANDRA-4009
     * when `cassandra_aliasing` flag is enabled, or default.
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

        // 22 = 2*10 metrics from java.lang + 2*1 metric explicitly defined in the yaml config file
        assertEquals(22, metrics.size());

        // Assert compliancy with CASSANDRA-4009
        List<String> tags = Arrays.asList(
            "type:ColumnFamily",
            "keyspace:MyKeySpace",
            "ColumnFamily:MyColumnFamily",
            "jmx_domain:org.apache.cassandra.metrics",
            "instance:jmx_first_instance"
        );

        assertMetric("cassandra.pending_tasks.should_be100", tags, 5);

        // Default behavior
        tags = Arrays.asList(
            "type:ColumnFamily",
            "scope:MyColumnFamily",
            "keyspace:MyKeySpace",
            "jmx_domain:org.apache.cassandra.metrics",
            "instance:jmx_second_instance",
            "name:PendingTasks");

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

        // 11 = 10 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(11, metrics.size());

        List<String> tags = Arrays.asList(
            "type:ColumnFamilies",
            "keyspace:MyKeySpace",
            "columnfamily:MyColumnFamily",
            "jmx_domain:org.apache.cassandra.db",
            "instance:jmx_test_instance");

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

        // First filter 26 = 10 metrics from java.lang + 16 metrics implicitly defined
        assertEquals(26, metrics.size());
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

        // First filter 11 = 10 metrics from java.lang + 2 metrics explicitly define- 1 implicitly defined in the exclude section
        assertEquals(11, metrics.size());
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

        // First filter 12 = 10 metrics from java.lang + 3 metrics explicitly defined - 1 implicitly defined in exclude section
        assertEquals(12, metrics.size());
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

        // 10 default metrics from java.lang
        assertEquals(10, metrics.size());
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

        // First filter 11 = 10 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(11, metrics.size());
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

        // First filter 10 = 10 metrics from java.lang + 2 metrics explicitly defined - 2 explicitly defined
        assertEquals(10, metrics.size());
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

        // First filter 11 = 10 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(11, metrics.size());
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

        // First filter 12 = 10 metrics from java.lang + 2 metrics explicitly defined
        assertEquals(12, metrics.size());
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

        // First filter 11 = 10 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(11, metrics.size());
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

        // First filter 11 = 10 metrics from java.lang + 1 metrics explicitly defined
        assertEquals(11, metrics.size());
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

    @Test
    public void testMetricTypes() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_histogram.yaml");

        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect
        List<String> commonTags = Arrays.asList(
            "instance:jmx_test_instance",
            "env:stage",
            "newTag:test");

        // 13 = 10 metrics from java.lang + the 3 collected (gauge and histogram)
        assertEquals(13, metrics.size());

        assertMetric("test.gauge", 1000.0, commonTags, 5, "gauge");
        assertMetric("test.gauge_by_default", 42.0, commonTags, 5, "gauge");
        assertMetric("test.histogram", 424242, commonTags, 5, "histogram");

        // We run a second collection. The counter should now be present
        run();
        metrics = getMetrics();

        // 14 = 10 metrics from java.lang + the 4 collected (gauge, histogram and counter)
        assertEquals(14, metrics.size());
        assertMetric("test.gauge", 1000.0, commonTags, 5, "gauge");
        assertMetric("test.gauge_by_default", 42.0, commonTags, 5, "gauge");
        assertMetric("test.histogram", 424242, commonTags, 5, "histogram");
        assertMetric("test.counter", 0.0, commonTags, 5, "counter");
    }

    @Test
    public void testExcludeTags() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_exclude_tags.yml");
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect.
        // Tags "type", "newTag" and "env" should be excluded
        List<String> commonTags = Arrays.asList(
            "instance:jmx_test_instance",
            "jmx_domain:org.datadog.jmxfetch.test");

        // 12 = 10 metrics from java.lang + the 2 collected (gauge and histogram)
        assertEquals(12, metrics.size());

        // There should only left 2 tags per metric
        assertMetric("test1.gauge", 1000.0, commonTags, 2, "gauge");
        assertMetric("test1.histogram", 424242, commonTags, 2, "histogram");
    }

    @Test
    public void testAdditionalTags() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,name=testName");

        // We do a first collection
        initApplication("jmx_additional_tags.yml");
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect.
        // Tags "type", "newTag" and "env" should be excluded
        List<String> commonTags = Arrays.asList(
            "instance:jmx_test_instance",
            "jmx_domain:org.datadog.jmxfetch.test",
            "type:SimpleTestJavaApp",
            "name:testName",
            "simple:SimpleTestJavaApp",
            "raw_value:value",
            "unknown_tag:$does-not-exist",
            "multiple:SimpleTestJavaApp-testName");

        // 12 = 10 metrics from java.lang + the 2 collected (gauge and histogram)
        assertEquals(12, metrics.size());

        // There should only left 2 tags per metric
        assertMetric("test1.gauge", 1000.0, commonTags, 8, "gauge");
        assertMetric("test1.histogram", 424242, commonTags, 8, "histogram");
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

        // 26 = 10 metrics from java.lang + the 6 gauges we are explicitly collecting + 9 gauges implicitly collected
        // + 1 multi-value, see jmx.yaml in the test/resources folder
        assertEquals(26, metrics.size());


        // We test for the presence and the value of the metrics we want to collect
        List<String> commonTags = Arrays.asList(
            "instance:jmx_test_instance",
            "env:stage",
            "newTag:test");

        assertMetric("this.is.100", 100.0, commonTags, Arrays.asList("foo","gorch","bar:baz") , 8);
        assertMetric("jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242",4.2424242E7, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 5);
        assertMetric("test.converted", 5.0, commonTags, 5);
        assertMetric("test.boolean", 1.0, commonTags, 5);
        assertMetric("test.defaulted", 32.0, commonTags, 5);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 5);
        assertMetric("subattr.defaulted", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 5);
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 7);

        assertCoverage();

        // We run a second collection. The counter should now be present
        run();
        metrics = getMetrics();
        // 28 = 10 metrics from java.lang + the 6 gauges we are explicitly collecting + 9 gauges implicitly collected
        // + 1 multi-value + 2 counter, see jmx.yaml in the test/resources folder
        assertEquals(28, metrics.size());


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
        assertMetric("subattr.defaulted", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 5);
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 7);

        // Counters
        assertMetric("subattr.counter", 0.0, commonTags, 5);
        assertMetric("test.counter", 0.0, commonTags, 5);
        assertCoverage();

        // We run a 3rd collection but this time we increment the counter and we sleep
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);
        testApp.populateTabularData(2);

        run();
        metrics = getMetrics();
        // 28 = 10 metrics from java.lang + the 6 gauges we are explicitly collecting + 9 gauges implicitly collected
        // + 1 multi-value + 2 counter, see jmx.yaml in the test/resources folder
        assertEquals(28, metrics.size());


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
        assertMetric("subattr.defaulted", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 5);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 5);
        assertMetric("multiattr.foo", 2.0, commonTags, Arrays.asList("foo:2", "toto:tata"), 7);

        // Counter (verify rate metrics within range)
        assertMetric("subattr.counter", 0.95, 1, commonTags, 5);
        assertMetric("test.counter", 0.95, 1, commonTags, 5);

        assertCoverage();
    }

    /**
     * Test Canonical Rates.
     *
     */
    @Test
    public void testAppCanonicalRate() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean( testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_canonical.yaml");

        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 25 = 10 metrics from java.lang + the 5 gauges we are explicitly collecting + 9 gauges implicitly collected
        // + 1 multi-value, see jmx.yaml in the test/resources folder
        assertEquals(25, metrics.size());


        // We test for the presence and the value of the metrics we want to collect
        List<String> commonTags = Arrays.asList(
            "instance:jmx_test_instance",
            "env:stage",
            "newTag:test");

        assertMetric("this.is.100", 100.0, commonTags, Arrays.asList("foo","gorch","bar:baz") , 8);
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
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 7);

        assertCoverage();

        // We run a second collection. The counter should now be present
        run();
        metrics = getMetrics();
        // 27 = 10 metrics from java.lang + the 5 gauges we are explicitly collecting + 9 gauges implicitly collected
        // + 1 multi-value + 2 counter, see jmx.yaml in the test/resources folder
        assertEquals(27, metrics.size());


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
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 7);

        // Counters
        assertMetric("subattr.counter", 0.0, commonTags, 5);
        assertMetric("test.counter", 0.0, commonTags, 5);
        assertCoverage();

        // We run a 3rd collection but this time we decrement the counter
        Thread.sleep(5000);
        testApp.decrementCounter(5);

        run();
        metrics = getMetrics();
        assertEquals(26, metrics.size());

        // The metric should be back in the next cycle.
        run();
        metrics = getMetrics();
        assertEquals(27, metrics.size());
        assertMetric("test.counter", 0.0, commonTags, 5);

        // Check that they are working again
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);
        testApp.populateTabularData(2);

        run();
        metrics = getMetrics();
        assertEquals(27, metrics.size());

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
        assertMetric("multiattr.foo", 2.0, commonTags, Arrays.asList("foo:2", "toto:tata"), 7);

        // Counter (verify rate metrics within range)
        assertMetric("subattr.counter", 0.95, 1, commonTags, 5);
        assertMetric("test.counter", 0.95, 1, commonTags, 5);
        assertCoverage();
    }

    /**
     * Test JMX Service Discovery.
     *
     */
    @Test
    public void testServiceDiscovery() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp test = new SimpleTestJavaApp();
        registerMBean(test, "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        registerMBean(test, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        registerMBean(test, "org.apache.cassandra.metrics:keyspace=MyKeySpace,type=ColumnFamily,scope=MyColumnFamily,name=PendingTasks");
        initApplication("jmx_alias_match.yaml", "jmx_sd_pipe.txt");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(47, metrics.size());


        List<String> tags = Arrays.asList(
            "type:SimpleTestJavaApp",
            "scope:CoolScope",
            "instance:jmx_test_instance",
            "jmx_domain:org.datadog.jmxfetch.test",
            "bean_host:localhost",
            "component"
        );

        assertMetric("this.is.100", tags, 6);

        // Assert compliancy with CASSANDRA-4009
        tags = Arrays.asList(
            "type:ColumnFamily",
            "keyspace:MyKeySpace",
            "ColumnFamily:MyColumnFamily",
            "jmx_domain:org.apache.cassandra.metrics",
            "instance:jmx_first_instance"
        );

        assertMetric("cassandra.pending_tasks.should_be100", tags, 5);

        // Default behavior
        tags = Arrays.asList(
            "type:ColumnFamily",
            "scope:MyColumnFamily",
            "keyspace:MyKeySpace",
            "jmx_domain:org.apache.cassandra.metrics",
            "instance:jmx_second_instance",
            "name:PendingTasks");

        assertMetric("cassandra.metrics.should_be1000", tags, 6);

        // Metric aliases are generated from `alias_match`
        tags = Arrays.asList(
            "jmx_domain:org.datadog.jmxfetch.test",
            "instance:jmx_test_instance",
            "foo:Bar",
            "qux:Baz"
        );

        assertMetric("this.is.100.bar.baz", tags, 4);
        assertMetric("org.datadog.jmxfetch.test.baz.hashmap.thisis0", tags, 4);
    }

    /**
     * Test JMX Service Discovery.
     *
     */
    @Test
    public void testServiceDiscoveryLong() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp test = new SimpleTestJavaApp();
        registerMBean(test, "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        registerMBean(test, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        registerMBean(test, "org.apache.cassandra.metrics:keyspace=MyKeySpace,type=ColumnFamily,scope=MyColumnFamily,name=PendingTasks");
        initApplication("jmx_alias_match.yaml", "jmx_sd_pipe_longname.txt");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();
        ArrayList<Instance> instances = getInstances();

        assertEquals(25, metrics.size());

        // 2(jmx_alias_match)  + 1 (jmx_sd_pipe_longname discards one)
        assertEquals(2, instances.size());

    }
}
