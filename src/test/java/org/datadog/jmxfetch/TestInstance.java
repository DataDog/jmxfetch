package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.Test;

public class TestInstance extends TestCommon {
    private static final Logger log = LogManager.getLogger("Test Instance");

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
        assertEquals(28, metrics.size());
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

    // assertServiceTag is used by testServiceTagGlobal and testServiceTagInstanceOverride
    private void assertServiceTag(List<String> tagList, String service) throws Exception {
        assertTrue(tagList.contains(new String("service:" + service)));
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
            this.assertServiceTag(Arrays.asList(tags), "global");
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), "global");
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
            this.assertServiceTag(Arrays.asList(tags), "override");
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), "override");
        }
    }

    @Test
    public void testLoadMetricConfigFiles() throws Exception {
        URL defaultConfig = Instance.class.getResource("default-jmx-metrics.yaml");
        AppConfig config = mock(AppConfig.class);
        when(config.getMetricConfigFiles()).thenReturn(Lists.newArrayList(defaultConfig.getPath()));
        List<Configuration> configurationList = new ArrayList<Configuration>();
        Instance.loadMetricConfigFiles(config, configurationList);

        assertEquals(2, configurationList.size());
    }

    @Test
    public void testLoadMetricConfigResources() throws Exception {
        URL defaultConfig = Instance.class.getResource("sample-metrics.yaml");
        String configResource = defaultConfig.getPath().split("test-classes/")[1];
        AppConfig config = mock(AppConfig.class);
        when(config.getMetricConfigResources()).thenReturn(Lists.newArrayList(configResource));
        List<Configuration> configurationList = new ArrayList<Configuration>();
        Instance.loadMetricConfigResources(config, configurationList);

        assertEquals(2, configurationList.size());
    }
}
