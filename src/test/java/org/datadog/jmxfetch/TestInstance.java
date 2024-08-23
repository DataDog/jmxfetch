package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TestInstance extends TestCommon {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Test Instance");

    @Test
    public void testMinCollectionInterval() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_min_collection_period.yml");

        run();
        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(15, metrics.size());

        run();
        metrics = getMetrics();
        assertEquals(0, metrics.size());

        log.info("sleeping before the next collection");
        Thread.sleep(5000);
        run();
        metrics = getMetrics();
        assertEquals(15, metrics.size());
    }

    // assertHostnameTags is used by testEmptyDefaultHostname
    private void assertHostnameTags(List<String> tagList) throws Exception {
        // Fixed instance tag
        assertTrue(tagList.contains(new String("jmx:fetch")));

        if (tagList.contains(new String("instance:jmx_test_default_hostname"))) {
            // Nominal case
            assertFalse(tagList.contains(new String("host:")));
        } else if (tagList.contains(new String("instance:jmx_test_no_hostname"))) {
            // empty_default_hostname case
            assertTrue(tagList.contains(new String("host:")));
        } else {
            fail("unexpected instance tag");
        }
    }

    @Test
    public void testEmptyDefaultHostname() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_empty_default_hostname.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(14, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertHostnameTags(Arrays.asList(tags));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertHostnameTags(Arrays.asList(tags));
        }
    }

    @Test
    public void testBaselineDefaultHostname() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_baseline_default_hostname.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(14, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertHostnameTags(Arrays.asList(tags));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertHostnameTags(Arrays.asList(tags));
        }
    }

    @Test
    public void testParsableMaxReturnedMetrics() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        // Populate testApp with a lot of metrics (>350) !
        testApp.populateHashMap(400);
        // Exposing a few metrics through JMX
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=ParsableMaxReturnedMetricsTest");
        initApplication("jmx_parsable_max_returned_metrics_string.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(429, metrics.size());
    }

    @Test
    public void testNonParsableMaxReturnedMetrics() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=NonParsableMaxReturnedMetricsTest");
        initApplication("jmx_non_parsable_max_returned_metrics_string.yaml");
        run();

        // Despite non parsable max_returned_metrics, metrics should still be collected.
        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(29, metrics.size());
    }

    // assertServiceTags is used by testServiceTagGlobal and testServiceTagInstanceOverride
    private void assertServiceTag(List<String> tagList, List<String> services) throws Exception {
        for (String service: services) {
            assertTrue(tagList.contains(new String("service:" + service)));
        }
    }

    @Test
    public void testServiceTagGlobal() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_service_tag_global.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global"));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global"));
        }
    }

    @Test
    public void testServiceTagGlobalList() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_service_tag_global_list.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global", "foo", "bar"));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global", "foo", "bar"));
        }
    }

    @Test
    public void testServiceTagInstanceOverride() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_service_tag_instance_override.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("override"));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("override"));
        }
    }

    @Test
    public void testLoadMetricConfigFiles() throws Exception {
        URL defaultConfig = Instance.class.getResource("default-jmx-metrics.yaml");
        AppConfig config = mock(AppConfig.class);
        when(config.getMetricConfigFiles()).thenReturn(Collections.singletonList(defaultConfig.getPath()));
        List<Configuration> configurationList = new ArrayList<Configuration>();
        Instance.loadMetricConfigFiles(config, configurationList);

        assertEquals(2, configurationList.size());
    }

    @Test
    public void testLoadMetricConfigResources() throws Exception {
        URL defaultConfig = Instance.class.getResource("sample-metrics.yaml");
        String configResource = defaultConfig.getPath().split("test-classes/")[1];
        AppConfig config = mock(AppConfig.class);
        when(config.getMetricConfigResources()).thenReturn(Collections.singletonList(configResource));
        List<Configuration> configurationList = new ArrayList<Configuration>();
        Instance.loadMetricConfigResources(config, configurationList);

        assertEquals(2, configurationList.size());
    }

    /** Tests refresh_beans_initial and the following refresh_beans */
    @Test
    public void testRefreshBeans() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        initApplication("jmx_refresh_beans.yaml");

        // We do a first collection
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 13 metrics from java.lang
        assertEquals(13, metrics.size());

        // We register an additional mbean
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        log.info("sleeping before the next collection");
        Thread.sleep(1500);

        // We run a second collection. refresh_beans_initial should be due.
        run();
        metrics = getMetrics();

        // 15 = 13 metrics from java.lang + 2 iteration=one
        assertEquals(15, metrics.size());

        // We register additional mbean
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=two");
        log.info("sleeping before the next collection");
        Thread.sleep(1500);

        // We run a third collection. No change expected; refresh_beans not due.
        run();
        metrics = getMetrics();

        // 15 = 13 metrics from java.lang + 2 iteration=one
        assertEquals(15, metrics.size());

        log.info("sleeping before the next collection");
        Thread.sleep(1500);

        // We run the last collection. refresh_beans should be due.
        run();
        metrics = getMetrics();

        // 17 = 13 metrics from java.lang + 2 iteration=one + 2 iteration=two
        assertEquals(17, metrics.size());
    }
}
