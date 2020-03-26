package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.datadog.jmxfetch.reporter.Reporter;
import org.junit.Test;

public class TestServiceChecks extends TestCommon {

    @Test
    public void testServiceCheckOK() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=ServiceCheckTest");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // Test that an OK service check status is sent
        List<Map<String, Object>> serviceChecks = getServiceChecks();

        assertEquals(1, serviceChecks.size());
        Map<String, Object> sc = serviceChecks.get(0);
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        String scStatus = (String) (sc.get("status"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals("jmx.can_connect", scName);
        assertEquals(Status.STATUS_OK, scStatus);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));
        assertTrue(Arrays.asList(scTags).contains("env:stage"));
        assertTrue(Arrays.asList(scTags).contains("newTag:test"));
    }

    @Test
    public void testServiceCheckWarning() throws Exception {
        //  Test application
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        // Populate it with a lot of metrics (>350) !
        testApp.populateHashMap(400);

        // Exposing a few metrics through JMX
        registerMBean(testApp, "org.datadog.jmxfetch.test:type=ServiceCheckTest");

        initApplication("too_many_metrics.yaml");

        // JMX configuration should return > 350 metrics
        run();

        // Test that an WARNING service check status is sent
        List<Map<String, Object>> serviceChecks = getServiceChecks();
        List<Map<String, Object>> metrics = getMetrics();
        assertTrue(metrics.size() >= 350);

        assertEquals(2, serviceChecks.size());
        Map<String, Object> sc = serviceChecks.get(0);
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));

        // Message should not be null anymore and reports a high number of metrics warning
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        String scStatus = (String) (sc.get("status"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals("too_many_metrics.can_connect", scName);
        // We should have an OK service check status when too many metrics are getting sent
        assertEquals(Status.STATUS_OK, scStatus);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));
        assertTrue(Arrays.asList(scTags).contains("env:stage"));
        assertTrue(Arrays.asList(scTags).contains("newTag:test"));
    }

    @Test
    public void testServiceCheckCRITICAL() throws Exception {
        // Test that a non-running service sends a critical service check
        registerMBean(
                new SimpleTestJavaApp(),
                "org.datadog.jmxfetch.test_non_running:type=ServiceCheckTest2");
        initApplication("non_running_process.yaml");

        // Test that a CRITICAL service check status is sent on initialization
        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());

        Map<String, Object> sc = serviceChecks.get(0);
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        String scStatus = (String) (sc.get("status"));
        String scMessage = (String) (sc.get("message"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals("non_running_process.can_connect", scName);
        assertEquals(Status.STATUS_ERROR, scStatus);
        assertEquals(
                "Unable to instantiate or initialize instance process_regex: `.*non_running_process_test.*`. "
                + "Is the target JMX Server or JVM running? No match found. Available JVMs can be listed with "
                + "the `list_jvms` command.",
                scMessage);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));

        // Test that a CRITICAL service check status is sent on iteration
        run();

        serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());

        sc = serviceChecks.get(0);
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        scName = (String) (sc.get("name"));
        scStatus = (String) (sc.get("status"));
        scMessage = (String) (sc.get("message"));
        scTags = (String[]) (sc.get("tags"));

        assertEquals("non_running_process.can_connect", scName);
        assertEquals(Status.STATUS_ERROR, scStatus);
        assertEquals(
                "Unable to instantiate or initialize instance process_regex: `.*non_running_process_test.*`. "
                + "Is the target JMX Server or JVM running? No match found. Available JVMs can be listed with "
                + "the `list_jvms` command.",
                scMessage);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));
        assertTrue(Arrays.asList(scTags).contains("env:stage"));
        assertTrue(Arrays.asList(scTags).contains("newTag:test"));
    }

    @Test
    public void testServiceCheckCounter() throws Exception {
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx.yaml");

        Reporter repo = getReporter();

        // Let's check that the counter is null
        assertEquals(0, repo.getServiceCheckCount("jmx"));

        // Let's put a service check in the pipeline (we cannot call doIteration()
        // here unfortunately because it would call reportStatus which will flush
        // the count to the jmx_status.yaml file and reset the counter.
        repo.sendServiceCheck("jmx", "jmx.can_connect", Status.STATUS_OK, "This is a test", null);

        // Let's check that the counter has been updated
        assertEquals(1, repo.getServiceCheckCount("jmx"));

        // Let's check that each service check counter is reset after each app
        // app iteration
        run();
        assertEquals(0, repo.getServiceCheckCount("jmx"));
    }

    @Test
    public void testServiceCheckPrefix() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=ServiceCheckTest");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx_check_prefix.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // Test that the check prefix is used
        List<Map<String, Object>> serviceChecks = getServiceChecks();

        assertEquals(1, serviceChecks.size());
        Map<String, Object> sc = serviceChecks.get(0);
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));

        assertEquals( "myprefix.can_connect", scName);
    }

    @Test
    public void testServiceCheckNoPrefix() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=ServiceCheckTest");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx_check_no_prefix.yaml");

        run();
        List<Map<String, Object>> metrics = getMetrics();

        // Test that the check prefix is used
        List<Map<String, Object>> serviceChecks = getServiceChecks();

        assertEquals(2, serviceChecks.size());
        Map<String, Object> sc = serviceChecks.get(0);
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        assertEquals( "jmx_check_no_prefix.can_connect", scName);

        Map<String, Object> sc2 = serviceChecks.get(1);
        assertNotNull(sc2.get("name"));
        assertNotNull(sc2.get("status"));
        assertNull(sc2.get("message"));
        assertNotNull(sc2.get("tags"));

        String sc2Name = (String) (sc2.get("name"));
        assertEquals( "jmxchecknoprefix.can_connect", sc2Name);
    }

    @Test
    public void testServiceCheckOnceNoFormattingNeeded() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=ServiceCheckTest");

        // We do a first collection
        when(appConfig.isTargetDirectInstances()).thenReturn(true);
        initApplication("jmx.yaml");

        run();

        List<Map<String, Object>> serviceChecks = getServiceChecks();

        // Only 1 service check is expected if the formatted service check prefix (`jmx`)
        // is same as the unformatted one (`jmx`).
        assertEquals(1, serviceChecks.size());

        Map<String, Object> sc = serviceChecks.get(0);
        String scName = (String) (sc.get("name"));

        assertEquals("jmx.can_connect", scName);
    }

}
