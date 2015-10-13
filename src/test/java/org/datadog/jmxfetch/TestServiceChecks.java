package org.datadog.jmxfetch;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import static org.junit.Assert.*;

public class TestServiceChecks extends TestCommon {

    @Test
    public void testServiceCheckOK() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=ServiceCheckTest");

        // We do a first collection
        initApplication("jmx.yaml");

        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // Test that an OK service check status is sent
        LinkedList<HashMap<String, Object>> serviceChecks = getServiceChecks();

        assertEquals(1, serviceChecks.size());
        HashMap<String, Object> sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        String scStatus = (String) (sc.get("status"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals(Reporter.formatServiceCheckPrefix("jmx"), scName);
        assertEquals(Status.STATUS_OK, scStatus);
        assertEquals(scTags.length, 1);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));
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
        LinkedList<HashMap<String, Object>> serviceChecks = getServiceChecks();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();
        assertTrue(metrics.size() >= 350 );

        assertEquals(1, serviceChecks.size());
        HashMap<String, Object> sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));

        // Message should not be null anymore and reports a high number of metrics warning
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        String scStatus = (String) (sc.get("status"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals(Reporter.formatServiceCheckPrefix("too_many_metrics"), scName);
        // We should have an OK service check status when too many metrics are getting sent
        assertEquals(Status.STATUS_OK, scStatus);
        assertEquals(scTags.length, 1);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));
    }

    @Test
    public void testServiceCheckCRITICAL() throws Exception {
        // Test that a non-running service sends a critical service check
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test_non_running:type=ServiceCheckTest2");
        initApplication("non_running_process.yaml");


        // Test that a CRITICAL service check status is sent on initialization
        LinkedList<HashMap<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(1, serviceChecks.size());

        HashMap<String, Object> sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        String scStatus = (String) (sc.get("status"));
        String scMessage = (String) (sc.get("message"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals(Reporter.formatServiceCheckPrefix("non_running_process"), scName);
        assertEquals(Status.STATUS_ERROR, scStatus);
        assertEquals("Cannot connect to instance process_regex: .*non_running_process_test.* Cannot find JVM matching regex: .*non_running_process_test.*", scMessage);
        assertEquals(scTags.length, 1);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));


        // Test that a CRITICAL service check status is sent on iteration
        run();

        serviceChecks = getServiceChecks();
        assertEquals(1, serviceChecks.size());

        sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        scName = (String) (sc.get("name"));
        scStatus = (String) (sc.get("status"));
        scMessage = (String) (sc.get("message"));
        scTags = (String[]) (sc.get("tags"));

        assertEquals(Reporter.formatServiceCheckPrefix("non_running_process"), scName);
        assertEquals(Status.STATUS_ERROR, scStatus);
        assertEquals("Cannot connect to instance process_regex: .*non_running_process_test.*. Is a JMX Server running at this address?", scMessage);
        assertEquals(scTags.length, 1);
        assertTrue(Arrays.asList(scTags).contains("instance:jmx_test_instance"));

    }

    @Test
    public void testServiceCheckCounter() throws Exception {
        initApplication("jmx.yaml");

        Reporter repo = getReporter();

        // Let's check that the counter is null
        assertEquals(0, repo.getServiceCheckCount("jmx"));

        // Let's put a service check in the pipeline (we cannot call doIteration()
        // here unfortunately because it would call reportStatus which will flush
        // the count to the jmx_status.yaml file and reset the counter.
        repo.sendServiceCheck("jmx", Status.STATUS_OK, "This is a test", null);

        // Let's check that the counter has been updated
        assertEquals(1, repo.getServiceCheckCount("jmx"));

        // Let's check that each service check counter is reset after each app
        // app iteration
        run();
        assertEquals(0, repo.getServiceCheckCount("jmx"));
    }

    @Test
    public void testPrefixFormatter() throws Exception {
        // Let's get a list of Strings to test (add real versionned check names
        // here when you add  new versionned check)
        String[][] data = {
                {"activemq_58.foo.bar12", "activemq.foo.bar12"},
                {"test_package-X86_64-VER1:0.weird.metric_name", "testpackage.weird.metric_name" }
        };

        // Let's test them all
        for(int i=0; i<data.length; ++i)
            assertEquals(data[i][1], Reporter.formatServiceCheckPrefix(data[i][0]));
    }
}
