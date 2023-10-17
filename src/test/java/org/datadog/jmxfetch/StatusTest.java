package org.datadog.jmxfetch;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.datadog.jmxfetch.util.InstanceTelemetry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.Yaml;

public class StatusTest {

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Test
    public void TestStatus() throws IOException {

        File tempFile= folder.newFile("tempFile.txt");
        String tempFilePath = tempFile.getAbsolutePath();

        final Status status = new Status(tempFilePath);
        InstanceTelemetry instance = new InstanceTelemetry();

        int fakeBeansFetched = 11;
        int fakeMetricCount = 29;
        int fakeAttributeCount = 55;
        int fakeDomainsQueried = 3;
        int fakeWildcardQueryCount = 9;
        double fakeBeansAttributeMatchRatio = .4;

        instance.setBeansFetched(fakeBeansFetched);
        instance.setMetricCount(fakeMetricCount);
        instance.setTopLevelAttributeCount(fakeAttributeCount);
        instance.setDomainsQueried(fakeDomainsQueried);
        instance.setWildcardQueryCount(fakeWildcardQueryCount);
        instance.setAttributeMatchRatio(fakeBeansAttributeMatchRatio);

        status.addInstanceStats("fake_check", "fake_instance", 10, 3, "fake_message", Status.STATUS_OK, instance);
        status.flush();

        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(tempFilePath);

        HashMap yamlMap = yaml.load(inputStream);
        HashMap checks = (HashMap) yamlMap.get("checks");
        HashMap initializedChecks = (HashMap) checks.get("initialized_checks");
        List<Map<String, Object>> fakeCheck = (List<Map<String, Object>>) initializedChecks.get("fake_check");
        Map<String, Object> stats = fakeCheck.get(0);
        assertEquals("fake_instance", stats.get("instance_name"));
        assertEquals(10, stats.get("metric_count"));
        assertEquals(3, stats.get("service_check_count"));
        assertEquals(fakeBeansFetched, stats.get("instance_bean_count"));
        assertEquals(fakeAttributeCount, stats.get("instance_attribute_count"));
        assertEquals(fakeMetricCount, stats.get("instance_metric_count"));
        assertEquals(fakeDomainsQueried, stats.get("instance_domains_queried"));
        assertEquals(fakeWildcardQueryCount, stats.get("instance_wildcard_query_count"));
        assertEquals(fakeBeansAttributeMatchRatio, stats.get("instance_attribute_match_ratio"));
        assertEquals("fake_message", stats.get("message"));
        assertEquals(Status.STATUS_OK, stats.get("status"));
    }
}