package org.datadog.jmxfetch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tests for dynamic tag resolution from JMX bean attributes.
 */
public class TestDynamicTags extends TestCommon {

    /**
     * Test basic dynamic tag resolution with list-style tags.
     */
    @Test
    public void testDynamicTagsBasic() throws Exception {
        // Register a test MBean with attributes we'll use as tags
        registerMBean(
                new DynamicTagTestApp("prod-kafka-cluster", "3.0.0", 9092),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        // Also register the SimpleTestJavaApp for default JVM metrics
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");
        
        initApplication("jmx_dynamic_tags.yaml");

        // Run the collection
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertNotNull(metrics);
        assertTrue("Should have collected metrics", metrics.size() > 0);

        // Verify that our test metric has the dynamic tags
        List<String> expectedTags = Arrays.asList(
                "env:test",
                "cluster_id:prod-kafka-cluster",
                "kafka_version:3.0.0",
                "instance:jmx_dynamic_tags_test",
                "jmx_domain:org.datadog.jmxfetch.test",
                "dd.internal.jmx_check_name:jmx_dynamic_tags",
                "type:DynamicTagTestApp"
        );

        assertMetric("test.dynamic.tags.metric", 100.0, expectedTags, 7);
    }

    /**
     * Test dynamic tag resolution with "attribute." prefix in the reference.
     */
    @Test
    public void testDynamicTagsWithAttributePrefix() throws Exception {
        registerMBean(
                new DynamicTagTestApp("dev-kafka-cluster", "2.8.0", 9093),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplication("jmx_dynamic_tags_with_attribute_prefix.yaml");

        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);

        // Verify the dynamic tag was resolved correctly
        List<String> expectedTags = Arrays.asList(
                "env:test",
                "cluster_id:dev-kafka-cluster",
                "instance:jmx_dynamic_tags_attribute_test",
                "jmx_domain:org.datadog.jmxfetch.test",
                "dd.internal.jmx_check_name:jmx_dynamic_tags_with_attribute_prefix",
                "type:DynamicTagTestApp"
        );

        assertMetric("test.dynamic.tags.metric", 100.0, expectedTags, 6);
    }

    /**
     * Test dynamic tag resolution with map-style tags.
     */
    @Test
    public void testDynamicTagsMapStyle() throws Exception {
        registerMBean(
                new DynamicTagTestApp("staging-cluster", "3.1.0", 9094),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplication("jmx_dynamic_tags_map_style.yaml");

        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);

        // Verify both static and dynamic tags are present
        List<String> expectedTags = Arrays.asList(
                "env:test",
                "cluster_id:staging-cluster",
                "static_tag:static_value",
                "instance:jmx_dynamic_tags_map_test",
                "jmx_domain:org.datadog.jmxfetch.test",
                "dd.internal.jmx_check_name:jmx_dynamic_tags_map_style",
                "type:DynamicTagTestApp"
        );

        assertMetric("test.dynamic.tags.metric", 100.0, expectedTags, 7);
    }

    /**
     * Test that metrics are still collected when a dynamic tag reference fails to resolve.
     * The tag should simply be absent rather than causing the entire instance to fail.
     */
    @Test
    public void testDynamicTagsNonExistentBean() throws Exception {
        // Only register the app bean, not the bean referenced in the dynamic tag
        registerMBean(
                new DynamicTagTestApp("test-cluster", "1.0.0", 9095),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplication("jmx_dynamic_tags_nonexistent.yaml");

        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics despite failed dynamic tag resolution", 
                metrics.size() > 0);

        // The metric should be collected, but without the failed dynamic tag
        List<String> expectedTags = Arrays.asList(
                "env:test",
                "instance:jmx_dynamic_tags_nonexistent_test",
                "jmx_domain:org.datadog.jmxfetch.test",
                "dd.internal.jmx_check_name:jmx_dynamic_tags_nonexistent",
                "type:DynamicTagTestApp"
        );

        // Note: cluster_id tag should NOT be present since the bean doesn't exist
        assertMetric("test.dynamic.tags.metric", 100.0, expectedTags, 5);
    }

    /**
     * Test that dynamic tags work with different attribute types (integer).
     */
    @Test
    public void testDynamicTagsWithIntegerAttribute() throws Exception {
        registerMBean(
                new DynamicTagTestApp("int-test-cluster", "1.0.0", 8888),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplicationWithYamlLines(
                "init_config:",
                "",
                "instances:",
                "    -   process_name_regex: .*surefire.*",
                "        name: jmx_dynamic_tags_int_test",
                "        tags:",
                "            - env:test",
                "            - port:$org.datadog.jmxfetch.test:type=DynamicTagTestApp#Port",
                "        conf:",
                "            - include:",
                "               domain: org.datadog.jmxfetch.test",
                "               type: DynamicTagTestApp",
                "               attribute:",
                "                    Metric:",
                "                        metric_type: gauge",
                "                        alias: test.dynamic.tags.metric"
        );

        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);

        // Verify the port (integer) was converted to a string tag value
        // Note: dd.internal.jmx_check_name will be a generated config name since we're using initApplicationWithYamlLines
        
        // Find the test metric
        boolean foundMetric = false;
        for (Map<String, Object> metric : metrics) {
            if ("test.dynamic.tags.metric".equals(metric.get("name"))) {
                String[] tags = (String[]) metric.get("tags");
                List<String> tagList = Arrays.asList(tags);
                
                // Check that required tags are present
                assertTrue("Should have env:test tag", tagList.contains("env:test"));
                assertTrue("Should have port:8888 tag", tagList.contains("port:8888"));
                
                // Check for instance tag
                boolean hasInstanceTag = false;
                for (String tag : tagList) {
                    if (tag.startsWith("instance:jmx_dynamic_tags_int_test")) {
                        hasInstanceTag = true;
                        break;
                    }
                }
                assertTrue("Should have instance tag", hasInstanceTag);
                assertTrue("Should have type tag", tagList.contains("type:DynamicTagTestApp"));
                foundMetric = true;
                break;
            }
        }
        assertTrue("Should have found test.dynamic.tags.metric", foundMetric);
    }

    /**
     * Test that multiple instances can have different dynamic tag values.
     */
    @Test
    public void testDynamicTagsMultipleInstances() throws Exception {
        // Register two different instances with different cluster IDs
        registerMBean(
                new DynamicTagTestApp("cluster-1", "1.0.0", 9001),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance1");
        
        registerMBean(
                new DynamicTagTestApp("cluster-2", "2.0.0", 9002),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance2");
        
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");

        initApplicationWithYamlLines(
                "init_config:",
                "",
                "instances:",
                "    -   process_name_regex: .*surefire.*",
                "        name: jmx_dynamic_tags_multi_test",
                "        tags:",
                "            - cluster_id_1:$org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance1#ClusterId",
                "            - cluster_id_2:$org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance2#ClusterId",
                "        conf:",
                "            - include:",
                "               domain: org.datadog.jmxfetch.test",
                "               bean: org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance1",
                "               attribute:",
                "                    Metric:",
                "                        metric_type: gauge",
                "                        alias: test.instance1.metric"
        );

        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);

        // Find the test metric and verify both cluster IDs are present
        boolean foundMetric = false;
        for (Map<String, Object> metric : metrics) {
            if ("test.instance1.metric".equals(metric.get("name"))) {
                String[] tags = (String[]) metric.get("tags");
                List<String> tagList = Arrays.asList(tags);
                
                // Check that both dynamic tags are present with correct values
                assertTrue("Should have cluster_id_1 tag", tagList.contains("cluster_id_1:cluster-1"));
                assertTrue("Should have cluster_id_2 tag", tagList.contains("cluster_id_2:cluster-2"));
                
                // Check for instance tag
                boolean hasInstanceTag = false;
                for (String tag : tagList) {
                    if (tag.startsWith("instance:jmx_dynamic_tags_multi_test")) {
                        hasInstanceTag = true;
                        break;
                    }
                }
                assertTrue("Should have instance tag", hasInstanceTag);
                assertTrue("Should have type tag", tagList.contains("type:DynamicTagTestApp"));
                assertTrue("Should have name tag", tagList.contains("name:Instance1"));
                foundMetric = true;
                break;
            }
        }
        assertTrue("Should have found test.instance1.metric", foundMetric);
    }
}

