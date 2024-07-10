package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.datadog.jmxfetch.util.AppTelemetry;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.junit.Test;

public class TestApp extends TestCommon {

    /** Tag metrics with MBean parameters based on user supplied regex */
    @Test
    public void testBeanRegexTags() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        initApplication("jmx_bean_regex_tags.yaml");

        // Run the collection
        run();

        List<String> tags =
                Arrays.asList(
                        "type:SimpleTestJavaApp",
                        "scope:CoolScope",
                        "instance:jmx_test_instance",
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_bean_regex_tags",
                        "bean_host:localhost",
                        "component",
                        "hosttag:localhost",
                        "nonExistantTag:$2",
                        "nonRegexTag:value");

        assertMetric("this.is.100", tags, 10);

        AppTelemetry tlm = app.getAppTelemetryBean();
        assertEquals(1, tlm.getRunningInstanceCount());
    }

    /** Tag metrics with MBeans parameters. */
    @Test
    public void testBeanTags() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        initApplication("jmx_bean_tags.yaml");

        // Collecting metrics
        run();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, getMetrics().size());

        List<String> tags =
                Arrays.asList(
                        "type:SimpleTestJavaApp",
                        "scope:CoolScope",
                        "instance:jmx_test_instance",
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_bean_tags",
                        "bean_host:localhost",
                        "component");

        assertMetric("this.is.100", tags, 7);

        AppTelemetry tlm = app.getAppTelemetryBean();
        assertEquals(1, tlm.getRunningInstanceCount());
    }

    /** Tag metrics with MBeans parameters with normalize_bean_param_tags option enabled. */
    @Test
    public void testBeanTagsNormalizeParams() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=\"SimpleTestJavaApp\",scope=\"Co|olScope\",host=\"localhost\",component=,target_instance="
                + ObjectName.quote(".*example.process.regex.*"));
        initApplication("jmx_bean_tags_normalize_params.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, metrics.size());

        List<String> tags =
                Arrays.asList(
                        "type:SimpleTestJavaApp",
                        "scope:CoolScope",
                        "instance:jmx_test_instance",
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_bean_tags_normalize_params",
                        "bean_host:localhost",
                        "component",
                        "target_instance:.*example.process.regex.*");

        assertMetric("this.is.100", tags, 8);
    }

    /** Tag metrics with MBeans parameters with normalize_bean_param_tags option disabled. */
    @Test
    public void testBeanTagsDontNormalizeParams() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=\"SimpleTestJavaApp\",scope=\"Co|olScope\",host=\"localhost\",component=,target_instance="
                + ObjectName.quote(".*example.process.regex.*"));
        initApplication("jmx_bean_tags_dont_normalize_params.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, metrics.size());

        List<String> tags =
                Arrays.asList(
                        "type:\"SimpleTestJavaApp\"",
                        "scope:\"CoolScope\"",
                        "instance:jmx_test_instance",
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_bean_tags_dont_normalize_params",
                        "bean_host:\"localhost\"",
                        "component",
                        "target_instance:\".\\*example.process.regex.\\*\"");

        assertMetric("this.is.100", tags, 8);
    }

    /** Generate metric aliases from a `alias_match` regular expression. */
    @Test
    public void testRegexpAliasing() throws Exception {
        // Expose MBeans
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_alias_match.yaml");

        // Collect metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // Assertions

        // 17 metrics = 13 from `java.lang` + 4 from the user configuration file
        assertEquals(17, metrics.size());

        // Metric aliases are generated from `alias_match`
        List<String> tags =
                Arrays.asList(
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_alias_match",
                        "instance:jmx_test_instance1",
                        "foo:Bar",
                        "qux:Baz");

        assertMetric("this.is.100.bar.baz", tags, 5);
        assertMetric("org.datadog.jmxfetch.test.baz.hashmap.thisis0", tags, 5);
        assertMetric("this.is.thousand.1000.0", 1000, tags, 5);
        assertMetric("this.is.five.should_be5", 5, tags, 5);
    }

    /**
     * Test that specifying no alias on an attribute defined with a detailed hashmap works and picks
     * up a valid default alias
     */
    @Test
    public void testNoAliasOnDetailedAttribute() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_no_alias.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // Assertions

        // 14 metrics = 13 from `java.lang` + 1 from the user configuration file
        assertEquals(14, metrics.size());

        // Metric aliases are generated from `alias_match`
        List<String> tags =
                Arrays.asList(
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "instance:jmx_test_instance",
                        "dd.internal.jmx_check_name:jmx_no_alias",
                        "foo:Bar",
                        "qux:Baz");

        assertMetric("jmx.org.datadog.jmxfetch.test.should_be100", tags, 5);
    }

    /**
     * Check JMXFetch Cassandra metric aliasing logic, i.e. compliant with CASSANDRA-4009 when
     * `cassandra_aliasing` flag is enabled, or default.
     *
     * <p>More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
     */
    @Test
    public void testCassandraBean() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(
                new SimpleTestJavaApp(),
                "org.apache.cassandra.metrics:keyspace=MyKeySpace,type=ColumnFamily,scope=MyColumnFamily,name=PendingTasks");
        initApplication("jmx_cassandra.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 14 = 2*13 metrics from java.lang + 2*1 metric explicitly defined in the yaml config file
        assertEquals(28, metrics.size());

        // Assert compliancy with CASSANDRA-4009
        List<String> tags =
                Arrays.asList(
                        "type:ColumnFamily",
                        "keyspace:MyKeySpace",
                        "ColumnFamily:MyColumnFamily",
                        "jmx_domain:org.apache.cassandra.metrics",
                        "dd.internal.jmx_check_name:jmx_cassandra",
                        "instance:jmx_first_instance");

        assertMetric("cassandra.pending_tasks.should_be100", tags, 6);

        // Default behavior
        tags =
                Arrays.asList(
                        "type:ColumnFamily",
                        "scope:MyColumnFamily",
                        "keyspace:MyKeySpace",
                        "jmx_domain:org.apache.cassandra.metrics",
                        "dd.internal.jmx_check_name:jmx_cassandra",
                        "instance:jmx_second_instance",
                        "name:PendingTasks");

        assertMetric("cassandra.metrics.should_be1000", tags, 7);
    }

    @Test
    public void testCassandraDeprecatedBean() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(
                new SimpleTestJavaApp(),
                "org.apache.cassandra.db:type=ColumnFamilies,keyspace=MyKeySpace,columnfamily=MyColumnFamily");
        initApplication("jmx_cassandra_deprecated.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, metrics.size());

        List<String> tags =
                Arrays.asList(
                        "type:ColumnFamilies",
                        "keyspace:MyKeySpace",
                        "columnfamily:MyColumnFamily",
                        "jmx_domain:org.apache.cassandra.db",
                        "dd.internal.jmx_check_name:jmx_cassandra_deprecated",
                        "instance:jmx_test_instance");

        assertMetric("cassandra.db.should_be100", tags, 6);
    }

    @Test
    public void testDomainInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.includeme:type=AType");
        initApplication("jmx_domain_include.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 29 = 13 metrics from java.lang + 16 metrics implicitly defined
        assertEquals(29, metrics.size());
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
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 2 metrics explicitly define- 1 implicitly
        // defined in the exclude section
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
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 15 = 13 metrics from java.lang + 3 metrics explicitly defined - 1 implicitly
        // defined in exclude section
        assertEquals(15, metrics.size());
    }

    @Test
    public void testClassInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.includeme:type=AType");
        initApplication("jmx_class_include.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 29 = 13 metrics from java.lang + 16 metrics implicitly defined
        assertEquals(29, metrics.size());
    }

    @Test
    public void testClassExclude() throws Exception {
        class SimpleTestJavaAnotherApp extends SimpleTestJavaApp {

        }

        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.includeme:type=AType");
        registerMBean(new SimpleTestJavaAnotherApp(), "org.datadog.jmxfetch.includeme2:type=AnotherType");

        // Initializing application
        initApplication("jmx_class_exclude.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 14 = 13 metrics from java.lang + 2 metrics explicitly define- 1 implicitly
        // defined in the exclude section
        assertEquals(14, metrics.size());
    }

    @Test
    public void testClassRegex() throws Exception {
        class SimpleTestJavaAppIncludeMe extends SimpleTestJavaApp { }
        class SimpleTestJavaAppIncludeMeToo extends SimpleTestJavaApp { }
        class SimpleTestJavaAppIncludeMeNotMeX extends SimpleTestJavaApp { }

        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaAppIncludeMe(), "org.datadog.jmxfetch.includeme:type=AType");
        registerMBean(new SimpleTestJavaAppIncludeMeToo(), "org.datadog.jmxfetch.includeme.too:type=AType");
        registerMBean(new SimpleTestJavaAppIncludeMeNotMeX(), "org.datadog.jmxfetch.includeme.not.me:type=AType");

        // Initializing application
        initApplication("jmx_class_regex.yaml");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 15 = 13 metrics from java.lang + 3 metrics explicitly defined - 1 implicitly
        // defined in exclude section
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
        List<Map<String, Object>> metrics = getMetrics();

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
        List<Map<String, Object>> metrics = getMetrics();

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
        List<Map<String, Object>> metrics = getMetrics();

        // First filter 13 = 13 metrics from java.lang + 2 metrics explicitly defined - 2 explicitly
        // defined
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
        List<Map<String, Object>> metrics = getMetrics();

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
        List<Map<String, Object>> metrics = getMetrics();

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
        List<Map<String, Object>> metrics = getMetrics();

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
        List<Map<String, Object>> metrics = getMetrics();

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

    @Test
    public void testMetricTypes() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_histogram.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect
        List<String> commonTags =
                Arrays.asList("instance:jmx_test_instance", "env:stage", "newTag:test",
                              "dd.internal.jmx_check_name:jmx_histogram");

        // 15 = 13 metrics from java.lang + the 3 collected (gauge and histogram)
        assertEquals(16, metrics.size());

        assertMetric("test.gauge", 1000.0, commonTags, 6, "gauge");
        assertMetric("test.gauge_by_default", 42.0, commonTags, 6, "gauge");
        assertMetric("test.histogram", 424242, commonTags, 6, "histogram");

        // We run a second collection. The counter should now be present
        run();
        metrics = getMetrics();

        // 16 = 13 metrics from java.lang + the 4 collected (gauge, histogram and counter)
        assertEquals(17, metrics.size());
        assertMetric("test.gauge", 1000.0, commonTags, 6, "gauge");
        assertMetric("test.gauge_by_default", 42.0, commonTags, 6, "gauge");
        assertMetric("test.histogram", 424242, commonTags, 6, "histogram");
        assertMetric("test.counter", 0.0, commonTags, 6, "counter");
    }

    @Test
    public void testExcludeTags() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_exclude_tags.yaml");
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect.
        // Tags "type", "newTag" and "env" should be excluded
        List<String> commonTags =
                Arrays.asList("instance:jmx_test_instance", "jmx_domain:org.datadog.jmxfetch.test", "dd.internal.jmx_check_name:jmx_exclude_tags");

        // 15 = 13 metrics from java.lang + the 2 collected (gauge and histogram)
        assertEquals(15, metrics.size());

        // There should only left 2 tags per metric
        assertMetric("test1.gauge", 1000.0, commonTags, 3, "gauge");
        assertMetric("test1.histogram", 424242, commonTags, 3, "histogram");
    }

    @Test
    public void testExcludeServiceTagsAndOverride() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,service=foo");

        // We do a first collection
        initApplication("jmx_exclude_tags_override_service.yaml");
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect.
        // Tags "type", "newTag" and "env" should be excluded
        List<String> commonTags =
                Arrays.asList("instance:jmx_test_service_override_instance",
                        "jmx_domain:org.datadog.jmxfetch.test","service:test",
                        "dd.internal.jmx_check_name:jmx_exclude_tags_override_service");

        // 15 = 13 metrics from java.lang + the 2 collected (gauge and histogram)
        assertEquals(15, metrics.size());

        // There should only left 2 tags per metric
        assertMetric("test1.gauge", 1000.0, commonTags, 4, "gauge");
        assertMetric("test1.histogram", 424242, commonTags, 4, "histogram");
    }

    @Test
    public void testAdditionalTags() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,name=testName");

        // We do a first collection
        initApplication("jmx_additional_tags.yaml");
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // We test for the presence and the value of the metrics we want to collect.
        // Tags "type", "newTag" and "env" should be excluded
        List<String> commonTags =
                Arrays.asList(
                        "instance:jmx_test_instance",
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_additional_tags",
                        "type:SimpleTestJavaApp",
                        "name:testName",
                        "simple:SimpleTestJavaApp",
                        "raw_value:value",
                        "unknown_tag:$does-not-exist",
                        "multiple:SimpleTestJavaApp-testName");

        // 15 = 13 metrics from java.lang + the 2 collected (gauge and histogram)
        assertEquals(15, metrics.size());

        // There should only left 2 tags per metric
        assertMetric("test1.gauge", 1000.0, commonTags, 9, "gauge");
        assertMetric("test1.histogram", 424242, commonTags, 9, "histogram");
    }

    /** FIXME: Split this test in multiple sub-tests. */
    @Test
    public void testApp() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 29 = 13 metrics from java.lang + the 6 gauges we are explicitly collecting + 9 gauges
        // implicitly collected
        // + 1 multi-value, see jmx.yaml in the test/resources folder
        assertEquals(30, metrics.size());

        // We test for the presence and the value of the metrics we want to collect
        List<String> commonTags =
                Arrays.asList("instance:jmx_test_instance", "dd.internal.jmx_check_name:jmx", "env:stage", "newTag:test");

        assertMetric("this.is.100", 100.0, commonTags, Arrays.asList("foo", "gorch", "bar:baz"), 9);
        assertMetric(
                "jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242", 4.2424242E7, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 6);
        assertMetric("test.converted", 5.0, commonTags, 6);
        assertMetric("test.boolean", 1.0, commonTags, 6);
        assertMetric("test.defaulted", 32.0, commonTags, 6);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 6);
        assertMetric("subattr.defaulted", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 6);
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);
        assertMetric("multiattr_supp.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);

        assertCoverage();

        // We run a second collection. The counter should now be present
        run();
        metrics = getMetrics();
        // 31 = 13 metrics from java.lang + the 6 gauges we are explicitly collecting + 9 gauges
        // implicitly collected
        // + 2 multi-value + 2 counter, see jmx.yaml in the test/resources folder
        assertEquals(32, metrics.size());

        // We test for the same metrics but this time, the counter should be here
        // Previous metrics
        assertMetric("this.is.100", 100.0, commonTags, 9);
        assertMetric(
                "jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242", 4.2424242E7, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 6);
        assertMetric("test.converted", 5.0, commonTags, 6);
        assertMetric("test.boolean", 1.0, commonTags, 6);
        assertMetric("test.defaulted", 32.0, commonTags, 6);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 6);
        assertMetric("subattr.defaulted", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 6);
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);
        assertMetric("multiattr_supp.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);

        // Counters
        assertMetric("subattr.counter", 0.0, commonTags, 6);
        assertMetric("test.counter", 0.0, commonTags, 6);
        assertCoverage();

        // We run a 3rd collection but this time we increment the counter and we sleep
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);
        testApp.populateTabularData(2);

        run();
        metrics = getMetrics();
        // 31 = 13 metrics from java.lang + the 6 gauges we are explicitly collecting + 9 gauges
        // implicitly collected
        // + 2 multi-value + 2 counter, see jmx.yaml in the test/resources folder
        assertEquals(32, metrics.size());

        // Previous metrics
        assertMetric("this.is.100", 100.0, commonTags, 9);
        assertMetric(
                "jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242", 4.2424242E7, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 6);
        assertMetric("test.converted", 5.0, commonTags, 6);
        assertMetric("test.boolean", 1.0, commonTags, 6);
        assertMetric("test.defaulted", 32.0, commonTags, 6);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 6);
        assertMetric("subattr.defaulted", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 6);
        assertMetric("multiattr.foo", 2.0, commonTags, Arrays.asList("foo:2", "toto:tata"), 8);
        assertMetric("multiattr_supp.foo", 2.0, commonTags, Arrays.asList("foo:2", "toto:tata"), 8);

        // // Counter (verify rate metrics within range)
        assertMetric("subattr.counter", 0.95, 1, commonTags, 6);
        assertMetric("test.counter", 0.95, 1, commonTags, 6);

        assertCoverage();
    }

    /** Test Canonical Rates. */
    @Test
    public void testAppCanonicalRate() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_canonical.yaml");

        run();
        // 29 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 9 gauges
        // implicitly collected
        // + 2 multi-value, see jmx.yaml in the test/resources folder
        assertEquals(29, getMetrics().size());

        // We test for the presence and the value of the metrics we want to collect
        List<String> commonTags =
                Arrays.asList("instance:jmx_test_instance", "env:stage", "newTag:test", "dd.internal.jmx_check_name:jmx_canonical");

        assertMetric("this.is.100", 100.0, commonTags, Arrays.asList("foo", "gorch", "bar:baz"), 9);
        assertMetric(
                "jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242", 4.2424242E7, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 6);
        assertMetric("test.converted", 5.0, commonTags, 6);
        assertMetric("test.boolean", 1.0, commonTags, 6);
        assertMetric("test.defaulted", 32.0, commonTags, 6);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 6);
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);
        assertMetric("multiattr_supp.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);

        assertCoverage();

        // We run a second collection. The counter should now be present
        run();
        // 30 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 9 gauges
        // implicitly collected
        // + 2 multi-value + 2 counter, see jmx.yaml in the test/resources folder
        assertEquals(31, getMetrics().size());

        // We test for the same metrics but this time, the counter should be here
        // Previous metrics
        assertMetric("this.is.100", 100.0, commonTags, 9);
        assertMetric(
                "jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242", 4.2424242E7, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 6);
        assertMetric("test.converted", 5.0, commonTags, 6);
        assertMetric("test.boolean", 1.0, commonTags, 6);
        assertMetric("test.defaulted", 32.0, commonTags, 6);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 6);
        assertMetric("multiattr.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);
        assertMetric("multiattr_supp.foo", 1.0, commonTags, Arrays.asList("foo:1", "toto:tata"), 8);

        // Counters
        assertMetric("subattr.counter", 0.0, commonTags, 6);
        assertMetric("test.counter", 0.0, commonTags, 6);
        assertCoverage();

        // We run a 3rd collection but this time we decrement the counter
        Thread.sleep(5000);
        testApp.decrementCounter(5);

        run();
        assertEquals(30, getMetrics().size());

        // The metric should be back in the next cycle.
        run();
        assertEquals(31, getMetrics().size());
        assertMetric("test.counter", 0.0, commonTags, 6);

        // Check that they are working again
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);
        testApp.populateTabularData(2);

        run();
        assertEquals(31, getMetrics().size());

        // Previous metrics
        assertMetric("this.is.100", 100.0, commonTags, 9);
        assertMetric(
                "jmx.org.datadog.jmxfetch.test.number_big", 1.2345678890123457E20, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.long42424242", 4.2424242E7, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.int424242", 424242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.should_be1000", 1000.0, commonTags, 6);
        assertMetric("test.converted", 5.0, commonTags, 6);
        assertMetric("test.boolean", 1.0, commonTags, 6);
        assertMetric("test.defaulted", 32.0, commonTags, 6);
        assertMetric("subattr.this.is.0", 0.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic42", 42.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.atomic4242", 4242.0, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.object1337", 13.37, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.primitive_float", 123.4f, commonTags, 6);
        assertMetric("jmx.org.datadog.jmxfetch.test.instance_float", 567.8f, commonTags, 6);
        assertMetric("multiattr.foo", 2.0, commonTags, Arrays.asList("foo:2", "toto:tata"), 8);
        assertMetric("multiattr_supp.foo", 2.0, commonTags, Arrays.asList("foo:2", "toto:tata"), 8);

        // Counter (verify rate metrics within range)
        assertMetric("subattr.counter", 0.95, 1, commonTags, 6);
        assertMetric("test.counter", 0.95, 1, commonTags, 6);
        assertCoverage();
    }

    /**
     * Test counts.
     */
    @Test
    public void testAppCount() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean( testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplication("jmx_count.yaml");

        // First collection should not contain our count
        run();
        assertEquals(13, getMetrics().size());

        // Since our count is still equal to 0, we should report a delta equal to 0
        run();
        assertEquals(14, getMetrics().size());
        assertMetric("test.counter", 0, Collections.<String>emptyList(), 4);

        // For the 3rd collection we increment the count to 5 so we should get a +5 delta
        testApp.incrementCounter(5);
        run();
        assertEquals(14, getMetrics().size());
        assertMetric("test.counter", 5, Collections.<String>emptyList(), 4);

        assertCoverage();
    }

    /**
     * Test counter and rate.
     */
    @Test
    public void testAppCounterRate() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean( testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplication("jmx_counter_rate.yaml");

        // First collection should not contain our count
        run();
        assertEquals(26, getMetrics().size());

        // Since our count is still equal to 0, we should report a delta equal to 0
        run();
        assertEquals(28, getMetrics().size());
        assertMetric("test.counter", 0, Collections.<String>emptyList(), 4);
        assertMetric("test.rate", 0, Collections.<String>emptyList(), 4);

        Thread.sleep(5000);
        // For the 3rd collection we increment the count to 5 so we should get a +5 delta
        testApp.incrementCounter(5);
        run();
        assertEquals(28, getMetrics().size());
        assertMetric("test.counter", 0.95, 1, Collections.<String>emptyList(), 4);
        assertMetric("test.rate", 0.95, 1, Collections.<String>emptyList(), 4);

        assertCoverage();
    }

    /**
     * Test JMX Service Discovery.
     * */
    @Test
    public void testServiceDiscovery() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp test = new SimpleTestJavaApp();
        registerMBean(test, "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        registerMBean(
                test,
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        registerMBean(
                test,
                "org.apache.cassandra.metrics:keyspace=MyKeySpace,type=ColumnFamily,scope=MyColumnFamily,name=PendingTasks");
        initApplication("jmx_alias_match.yaml", "jmx_sd_pipe.txt");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(63, metrics.size());

        List<String> tags = Arrays.asList(
            "type:SimpleTestJavaApp",
            "scope:CoolScope",
            "instance:jmx_test_instance2",
            "jmx_domain:org.datadog.jmxfetch.test",
            "dd.internal.jmx_check_name:AD-jmx_0",
            "bean_host:localhost",
            "component"
        );

        assertMetric("this.is.100", tags, 7);

        // Assert compliancy with CASSANDRA-4009
        tags =
                Arrays.asList(
                        "type:ColumnFamily",
                        "keyspace:MyKeySpace",
                        "ColumnFamily:MyColumnFamily",
                        "jmx_domain:org.apache.cassandra.metrics",
                        "dd.internal.jmx_check_name:AD-cassandra_0",
                        "instance:jmx_first_instance");

        assertMetric("cassandra.pending_tasks.should_be100", tags, 6);

        // Default behavior
        tags =
                Arrays.asList(
                        "type:ColumnFamily",
                        "scope:MyColumnFamily",
                        "keyspace:MyKeySpace",
                        "jmx_domain:org.apache.cassandra.metrics",
                        "dd.internal.jmx_check_name:AD-cassandra_0",
                        "instance:jmx_second_instance",
                        "name:PendingTasks");

        assertMetric("cassandra.metrics.should_be1000", tags, 7);

        // Metric aliases are generated from `alias_match`
        tags =
                Arrays.asList(
                        "jmx_domain:org.datadog.jmxfetch.test",
                        "dd.internal.jmx_check_name:jmx_alias_match",
                        "instance:jmx_test_instance1",
                        "foo:Bar",
                        "qux:Baz");

        assertMetric("this.is.100.bar.baz", tags, 5);
        assertMetric("org.datadog.jmxfetch.test.baz.hashmap.thisis0", tags, 5);
    }

    /** Test JMX Service Discovery. */
    @Test
    public void testServiceDiscoveryLong() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp test = new SimpleTestJavaApp();
        registerMBean(test, "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        registerMBean(
                test,
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=Co|olScope,host=localhost,component=");
        registerMBean(
                test,
                "org.apache.cassandra.metrics:keyspace=MyKeySpace,type=ColumnFamily,scope=MyColumnFamily,name=PendingTasks");
        initApplication("jmx_alias_match.yaml", "jmx_sd_pipe_longname.txt");

        // Collecting metrics
        run();
        List<Map<String, Object>> metrics = getMetrics();
        List<Instance> instances = getInstances();

        assertEquals(35, metrics.size());

        // 2(jmx_alias_match)  + 1 (jmx_sd_pipe_longname discards one)
        assertEquals(2, instances.size());
    }

    /** Test that when a metric fails to be fetched and there is no default for
     *  it, that metric is ignored while others still get fetched */
    @Test
    public void testMetricError() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        initApplication("jmx_cast.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 13 metrics from java.lang + 2 defined - 1 error
        assertEquals(14, metrics.size());

        List<String> tags = Arrays.asList(
                "instance:jmx_test_instance",
                "jmx_domain:org.datadog.jmxfetch.test",
                "type:SimpleTestJavaApp"
        );

        assertMetric("jmx.org.datadog.jmxfetch.test.should_be100", 100.0, tags, -1);

        assertCoverage();
    }
    @Test
    public void testTabularDataTagless() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx_tabular_data_tagless.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 13 metrics from java.lang + 2 defined - 1 error
        assertEquals(14, metrics.size());

        List<String> tags = Arrays.asList(
                "instance:jmx_test_instance",
                "jmx_domain:org.datadog.jmxfetch.test",
                "type:SimpleTestJavaApp",
                "newTag:test"
        );

        assertMetric("multiattr.foo_tagless", 1.0, tags, -1);

        assertCoverage();
    }
    @Test
    public void testTabularDataTagged() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx_tabular_data_tagged.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 13 metrics from java.lang + 2 defined - 1 error
        assertEquals(14, metrics.size());

        List<String> tags = Arrays.asList(
                "instance:jmx_test_instance",
                "jmx_domain:org.datadog.jmxfetch.test",
                "type:SimpleTestJavaApp",
                "foo:1",
                "toto:tata",
                "newTag:test"
        );

        assertMetric("multiattr.foo_tagged", 1.0, tags, -1);

        assertCoverage();
    }

    @Test
    public void testJeeStatistics() throws Exception {
        // We expose a few metrics through JMX
        SimpleTestJavaApp testApp = new SimpleTestJavaApp(true);
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx_jee_data.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 13 metrics from java.lang + 17 defined - 1 undefined
        assertEquals(29, metrics.size());

        List<String> tags = Arrays.asList(
                "instance:jmx_test_instance",
                "jmx_domain:org.datadog.jmxfetch.test",
                "type:SimpleTestJavaApp"
        );
        final String prefix = "jmx.org.datadog.jmxfetch.test.";

        assertMetric(prefix + "jee_counter.count", testApp.getLong42424242(), tags, -1);
        assertMetric(prefix + "jee_time.count", 1, tags, -1);
        assertMetric(prefix + "jee_time.min_time", 0, tags, -1);
        assertMetric(prefix + "jee_time.max_time", Long.MAX_VALUE, tags, -1);
        assertMetric(prefix + "jee_time.total_time", testApp.getLong42424242(), tags, -1);
        assertMetric(prefix + "jee_range.low_water_mark", Long.MIN_VALUE, tags, -1);
        assertMetric(prefix + "jee_range.high_water_mark", Long.MAX_VALUE, tags, -1);
        assertMetric(prefix + "jee_range.current", testApp.getLong42424242(), tags, -1);
        assertMetric(prefix + "jee_boundary.lower_bound", Long.MIN_VALUE, tags, -1);
        assertMetric(prefix + "jee_boundary.upper_bound", Long.MAX_VALUE, tags, -1);
        assertMetric(prefix + "jee_bounded_range.low_water_mark", Long.MIN_VALUE, tags, -1);
        assertMetric(prefix + "jee_bounded_range.high_water_mark", Long.MAX_VALUE, tags, -1);
        assertMetric(prefix + "jee_bounded_range.current", 0, tags, -1);
        assertMetric(prefix + "jee_bounded_range.lower_bound", -1, tags, -1);
        assertMetric(prefix + "jee_bounded_range.upper_bound", 1, tags, -1);
        assertMetric(prefix + "jee_stat.my_counter.count", testApp.getLong42424242(), tags, -1);
        assertCoverage();
    }

    @Test
    public void testNestedCompositeData() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx_composite_data.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        assertEquals(1, metrics.size());

        List<String> tags = Arrays.asList(
                "instance:jmx_test_instance",
                "jmx_domain:org.datadog.jmxfetch.test",
                "type:SimpleTestJavaApp",
                "env:stage",
                "newTag:test");

        assertMetric("one_level_int", 42, tags, -1);

        // This assertion currently fails as JMXFetch does not support accessing
        // data from a nested compositedata object
        //assertMetric("second_level_long", 123456L, tags, -1);

        assertCoverage();
    }

    @Test
    public void testTelemetryTags() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        when(appConfig.getJmxfetchTelemetry()).thenReturn(true);
        when(appConfig.getVersion()).thenReturn("MOCKED_VERSION");

        initApplication("jmx_telemetry_tags.yaml");

        run();

        List<String> telemetryTags = Arrays.asList(
                "instance:jmxfetch_telemetry_instance",
                "name:jmxfetch_app",
                "jmx_domain:jmx_fetch",
                "version:MOCKED_VERSION");

        assertMetric("jmx.jmx_fetch.running_instance_count", 2, telemetryTags, -1);

        // not asserting coverage, this is intended to test the tags present on telemetry
        // not the set metrics collected
    }
}
