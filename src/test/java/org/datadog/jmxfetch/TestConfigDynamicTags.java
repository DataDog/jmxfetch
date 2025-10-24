package org.datadog.jmxfetch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestConfigDynamicTags extends TestCommon {

    @Test
    public void testConfigDynamicTagsBasic() throws Exception {
        registerMBean(
                new DynamicTagTestApp("kafka-prod-cluster", "3.2.0", 9092),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        
        initApplication("jmx_config_dynamic_tags.yaml");
        
        run();
        
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);
        
        boolean foundMetric = false;
        for (Map<String, Object> metric : metrics) {
            String metricName = (String) metric.get("name");
            if ("test.config.dynamic.tags.metric".equals(metricName)) {
                foundMetric = true;
                
                Object tagsObj = metric.get("tags");
                assertNotNull("Metric should have tags", tagsObj);
                
                List<String> tagList = new ArrayList<>();
                if (tagsObj instanceof String[]) {
                    for (String tag : (String[]) tagsObj) {
                        tagList.add(tag);
                    }
                } else if (tagsObj instanceof List) {
                    for (Object tag : (List<?>) tagsObj) {
                        tagList.add((String) tag);
                    }
                }
                
                assertTrue("Should have cluster_id tag", 
                        tagList.contains("cluster_id:kafka-prod-cluster"));
                assertTrue("Should have kafka_version tag", 
                        tagList.contains("kafka_version:3.2.0"));
                assertTrue("Should have env tag", 
                        tagList.contains("env:test"));
                
                break;
            }
        }
        
        assertTrue("Should have found the test metric", foundMetric);
    }
    
    @Test
    public void testConfigDynamicTagsMultiple() throws Exception {
        registerMBean(
                new DynamicTagTestApp("cluster-1", "version-1", 9092),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance1");
        
        registerMBean(
                new DynamicTagTestApp("cluster-2", "version-2", 9093),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=Instance2");
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        
        initApplication("jmx_config_dynamic_tags_multi.yaml");
        
        run();
        
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);
        
        boolean foundInstance1 = false;
        boolean foundInstance2 = false;
        
        for (Map<String, Object> metric : metrics) {
            String metricName = (String) metric.get("name");
            
            if ("test.instance1.metric".equals(metricName)) {
                foundInstance1 = true;
                
                List<String> tagList = getTagsAsList(metric);
                assertTrue("Instance1 should have cluster_id tag", 
                        tagList.contains("cluster_id:cluster-1"));
                
                boolean hasVersionTag = false;
                for (String tag : tagList) {
                    if (tag.startsWith("version:")) {
                        hasVersionTag = true;
                        break;
                    }
                }
                assertTrue("Instance1 should NOT have version tag", !hasVersionTag);
            }
            
            if ("test.instance2.metric".equals(metricName)) {
                foundInstance2 = true;
                
                List<String> tagList = getTagsAsList(metric);
                assertTrue("Instance2 should have version tag", 
                        tagList.contains("version:version-2"));
                
                boolean hasClusterTag = false;
                for (String tag : tagList) {
                    if (tag.startsWith("cluster_id:")) {
                        hasClusterTag = true;
                        break;
                    }
                }
                assertTrue("Instance2 should NOT have cluster_id tag", !hasClusterTag);
            }
        }
        
        assertTrue("Should have found instance1 metric", foundInstance1);
        assertTrue("Should have found instance2 metric", foundInstance2);
    }
    
    @Test
    public void testConfigDynamicTagsWithBeanParams() throws Exception {
        registerMBean(
                new DynamicTagTestApp("test-cluster", "1.0.0", 9092),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp,name=TestBean");
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        
        initApplication("jmx_config_dynamic_tags_bean_params.yaml");
        
        run();
        
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);
        
        boolean foundMetric = false;
        for (Map<String, Object> metric : metrics) {
            String metricName = (String) metric.get("name");
            if ("test.bean.params.metric".equals(metricName)) {
                foundMetric = true;
                
                List<String> tagList = getTagsAsList(metric);
                
                assertTrue("Should have bean_type tag", 
                        tagList.contains("bean_type:DynamicTagTestApp"));
                assertTrue("Should have bean_name tag", 
                        tagList.contains("bean_name:TestBean"));
                assertTrue("Should have cluster_id tag", 
                        tagList.contains("cluster_id:test-cluster"));
                
                break;
            }
        }
        
        assertTrue("Should have found the test metric", foundMetric);
    }
    
    @Test
    public void testConfigDynamicTagsCaching() throws Exception {
        registerMBean(
                new DynamicTagTestApp("shared-cluster", "1.0.0", 9092),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        
        initApplication("jmx_config_dynamic_tags_caching.yaml");
        
        run();
        
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);
        
        int metricsWithClusterId = 0;
        for (Map<String, Object> metric : metrics) {
            String metricName = (String) metric.get("name");
            if (metricName != null && metricName.startsWith("test.metric")) {
                List<String> tagList = getTagsAsList(metric);
                if (tagList.contains("cluster_id:shared-cluster")) {
                    metricsWithClusterId++;
                }
            }
        }
        
        assertTrue("Found " + metricsWithClusterId + " metrics with cluster_id, expected 2", 
                metricsWithClusterId == 2);
    }
    
    @Test
    public void testConfigDynamicTagsMultiplePerConf() throws Exception {
        registerMBean(
                new DynamicTagTestApp("cluster-1", "version-1", 9999),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        initApplication("jmx_config_dynamic_tags_multiple_per_conf.yaml");
        
        run();
        
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);
        
        boolean foundMetric = false;
        for (Map<String, Object> metric : metrics) {
            String metricName = (String) metric.get("name");
            
            if ("test.metric.with.multiple.tags".equals(metricName)) {
                foundMetric = true;
                
                List<String> tagList = getTagsAsList(metric);
                
                // Verify all three dynamic tags are present
                assertTrue("Should have cluster_id dynamic tag", 
                        tagList.contains("cluster_id:cluster-1"));
                assertTrue("Should have version dynamic tag", 
                        tagList.contains("version:version-1"));
                assertTrue("Should have port dynamic tag", 
                        tagList.contains("port:9999"));
                
                // Verify normal tags are also present
                assertTrue("Should have env tag", 
                        tagList.contains("env:test"));
            }
        }
        
        assertTrue("Should have found metric with multiple dynamic tags", foundMetric);
    }
    
    @Test
    public void testConfigDynamicTagsInvalid() throws Exception {
        registerMBean(
                new DynamicTagTestApp("cluster-1", "version-1", 9999),
                "org.datadog.jmxfetch.test:type=DynamicTagTestApp");
        
        initApplication("jmx_config_dynamic_tags_invalid.yaml");
        
        run();
        
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue("Should have collected metrics", metrics.size() > 0);
        
        boolean foundMetric = false;
        for (Map<String, Object> metric : metrics) {
            String metricName = (String) metric.get("name");
            
            if ("test.metric.with.invalid.tags".equals(metricName)) {
                foundMetric = true;
                
                List<String> tagList = getTagsAsList(metric);
                
                // Verify normal tags are still applied
                assertTrue("Should have env tag", 
                        tagList.contains("env:test"));
                assertTrue("Should have region tag", 
                        tagList.contains("region:us-east-1"));
                
                // Verify the valid dynamic tag is applied
                assertTrue("Should have valid_port dynamic tag", 
                        tagList.contains("valid_port:9999"));
                
                // Verify invalid dynamic tags are NOT applied
                boolean hasMissingBeanTag = false;
                boolean hasMissingAttrTag = false;
                for (String tag : tagList) {
                    if (tag.startsWith("missing_bean:")) {
                        hasMissingBeanTag = true;
                    }
                    if (tag.startsWith("missing_attr:")) {
                        hasMissingAttrTag = true;
                    }
                }
                assertTrue("Should NOT have missing_bean tag (invalid config)", 
                        !hasMissingBeanTag);
                assertTrue("Should NOT have missing_attr tag (invalid config)", 
                        !hasMissingAttrTag);
            }
        }
        
        assertTrue("Should have found metric (verifies invalid dynamic tags don't crash)", 
                foundMetric);
    }
    
    private List<String> getTagsAsList(Map<String, Object> metric) {
        List<String> tagList = new ArrayList<>();
        Object tagsObj = metric.get("tags");
        if (tagsObj instanceof String[]) {
            for (String tag : (String[]) tagsObj) {
                tagList.add(tag);
            }
        } else if (tagsObj instanceof List) {
            for (Object tag : (List<?>) tagsObj) {
                tagList.add((String) tag);
            }
        }
        return tagList;
    }
}

