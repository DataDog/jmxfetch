package org.datadog.jmxfetch;

import com.beust.jcommander.JCommander;
import org.apache.log4j.Level;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.util.CustomLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import static org.junit.Assert.*;

public class TestApp {

    @BeforeClass
    public static void init() {
        CustomLogger.setup(Level.toLevel("ALL"), "/tmp/jmxfetch_test.log");
    }

    public static App initApp(String yamlFileName, AppConfig appConfig){
        // We do a first collection
        // We initialize the main app that will collect these metrics using JMX
        String confdDirectory = Thread.currentThread().getContextClassLoader().getResource(yamlFileName).getPath();
        confdDirectory = new String(confdDirectory.substring(0, confdDirectory.length() - yamlFileName.length()));
        String[] params = {"--reporter", "console", "-c", yamlFileName, "--conf_directory", confdDirectory, "collect"};
        new JCommander(appConfig, params);

        App app = new App(appConfig);
        app.init(false);

        return app;
    }

    @Test
    public void testServiceCheckOK() throws Exception {
        // We expose a few metrics through JMX
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.datadog.jmxfetch.test:type=ServiceCheckTest");
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        mbs.registerMBean(testApp, objectName);

        // We do a first collection
        AppConfig appConfig = new AppConfig();
        App app = initApp("jmx.yaml", appConfig);

        app.doIteration();
        ConsoleReporter reporter = ((ConsoleReporter) appConfig.getReporter());
        // Test that an OK service check status is sent
        LinkedList<HashMap<String, Object>> serviceChecks = reporter.getServiceChecks();

        assertEquals(1, serviceChecks.size());
        HashMap<String, Object> sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        int scStatus = Integer.parseInt((String) (sc.get("status")));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals("jmx", scName);
        assertEquals(Status.STATUS_OK, scStatus);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("env:stage"));
        assertTrue(Arrays.asList(scTags).contains("newTag:test"));
        assertTrue(Arrays.asList(scTags).contains("process:.*surefire.*"));
        mbs.unregisterMBean(objectName);
    }

    @Test
    public void testServiceCheckCRITICAL() throws Exception {
        // Test that a non-running service sends a critical service check
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.datadog.jmxfetch.test_non_running:type=ServiceCheckTest2");
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        mbs.registerMBean(testApp, objectName);

        AppConfig appConfig = new AppConfig();

        App app = initApp("non_running_process.yaml", appConfig);
        ConsoleReporter reporter = ((ConsoleReporter) appConfig.getReporter());

        // Test that a CRITICAL service check status is sent on initialization
        LinkedList<HashMap<String, Object>> serviceChecks = reporter.getServiceChecks();
        assertEquals(1, serviceChecks.size());

        HashMap<String, Object> sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        String scName = (String) (sc.get("name"));
        int scStatus = Integer.parseInt((String) (sc.get("status")));
        String scMessage = (String) (sc.get("message"));
        String[] scTags = (String[]) (sc.get("tags"));

        assertEquals("non_running_process", scName);
        assertEquals(Status.STATUS_ERROR, scStatus);
        assertEquals("Cannot connect to instance process_regex: .*non_running_process_test.* Cannot find JVM matching regex: .*non_running_process_test.*", scMessage);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("env:stage"));
        assertTrue(Arrays.asList(scTags).contains("newTag:test"));
        assertTrue(Arrays.asList(scTags).contains("process:.*non_running_process_test.*"));


        // Test that a CRITICAL service check status is sent on iteration
        app.doIteration();

        serviceChecks = reporter.getServiceChecks();
        assertEquals(1, serviceChecks.size());

        sc = serviceChecks.getFirst();
        assertNotNull(sc.get("name"));
        assertNotNull(sc.get("status"));
        assertNotNull(sc.get("message"));
        assertNotNull(sc.get("tags"));

        scName = (String) (sc.get("name"));
        scStatus = Integer.parseInt((String) (sc.get("status")));
        scMessage = (String) (sc.get("message"));
        scTags = (String[]) (sc.get("tags"));

        assertEquals("non_running_process", scName);
        assertEquals(Status.STATUS_ERROR, scStatus);
        assertEquals("Cannot connect to instance process_regex: .*non_running_process_test.*. Is a JMX Server running at this address?", scMessage);
        assertEquals(scTags.length, 3);
        assertTrue(Arrays.asList(scTags).contains("env:stage"));
        assertTrue(Arrays.asList(scTags).contains("newTag:test"));
        assertTrue(Arrays.asList(scTags).contains("process:.*non_running_process_test.*"));

        mbs.unregisterMBean(objectName);
    }

    @Test
    public void testApp() throws Exception {
        // We expose a few metrics through JMX
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.datadog.jmxfetch.test:type=SimpleTestJavaApp");
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        mbs.registerMBean(testApp, objectName);

        // We do a first collection
        AppConfig appConfig = new AppConfig();
        App app = initApp("jmx.yaml", appConfig);

        app.doIteration();
        ConsoleReporter reporter = ((ConsoleReporter) appConfig.getReporter());

        LinkedList<HashMap<String, Object>> metrics = reporter.getMetrics();

        assertEquals(25, metrics.size()); // 25 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + the 7 gauges that is implicitly collected, see jmx.yaml in the test/resources folder

        // We test for the presence and the value of the metrics we want to collect
        boolean metric100Present = false;
        boolean atomicIntPresent = false;
        boolean atomicLongPresent = false;
        boolean objectPresent = false;
        boolean metric1000Present = false;
        boolean convertedPresent = false;
        boolean booleanPresent = false;
        boolean defaultPresent = false;
        boolean numberPresent = false;
        boolean integerPresent = false;
        boolean longPresent = false;
        boolean counterAbsent = true;
        boolean subattr0Present = false;
        boolean subattrCounterAbsent = true;

        for (HashMap<String, Object> m : metrics) {
            assertNotNull(m.get("name"));
            assertNotNull(m.get("value"));
            assertNotNull(m.get("tags"));

            String name = (String) (m.get("name"));
            Double value = (Double) (m.get("value"));
            String[] tags = (String[]) (m.get("tags"));

            // All metrics should be tagged with "instance:jmx_test_instance"
            assertTrue(Arrays.asList(tags).contains("instance:jmx_test_instance"));
            assertTrue(Arrays.asList(tags).contains("env:stage"));
            assertTrue(Arrays.asList(tags).contains("newTag:test"));

            assertNotNull(value);

            if (name.equals("this.is.100")) {
                assertEquals(8, tags.length);
                assertEquals(new Double(100.0), value);
                metric100Present = true;

                assertTrue(Arrays.asList(tags).contains("foo"));
                assertTrue(Arrays.asList(tags).contains("gorch"));
                assertTrue(Arrays.asList(tags).contains("bar:baz"));
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.number_big")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(1.2345678890123457E20), value);
                numberPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.long42424242")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(4.2424242E7), value);
                longPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.int424242")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(424242.0), value);
                integerPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.should_be1000")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(1000.0), value);
                metric1000Present = true;
            } else if (name.equals("test.converted")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(5.0), value);
                convertedPresent = true;
            } else if (name.equals("test.boolean")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(1.0), value);
                booleanPresent = true;
            } else if (name.equals("test.defaulted")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(32.0), value);
                defaultPresent = true;
            } else if (m.get("name").equals("test.counter")) {
                counterAbsent = false;

            } else if (name.equals("subattr.this.is.0")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(0.0), value);
                subattr0Present = true;



            } else if (name.equals("subattr.counter")) {
                subattrCounterAbsent = false;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic42")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(42.0), value);
                atomicIntPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic4242")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(4242.0), value);
                atomicLongPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.object1337")) {
                assertEquals(5, tags.length);
                assertEquals(new Double(13.37), value);
                objectPresent = true;
            }
        }

        assertTrue(metric100Present);
        assertTrue(metric1000Present);
        assertTrue(booleanPresent);
        assertTrue(convertedPresent);
        assertTrue(defaultPresent);
        assertTrue(counterAbsent);
        assertTrue(subattr0Present);
        assertTrue(subattrCounterAbsent);
        assertTrue(atomicIntPresent);
        assertTrue(atomicLongPresent);
        assertTrue(objectPresent);
        assertTrue(numberPresent);
        assertTrue(longPresent);
        assertTrue(integerPresent);

        // We run a second collection. The counter should now be present
        app.doIteration();
        metrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
        assertEquals(27, metrics.size()); // 27 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 7 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

        // We test for the same metrics but this time, the counter should be here
        metric100Present = false;
        atomicIntPresent = false;
        atomicLongPresent = false;
        objectPresent = false;
        metric1000Present = false;
        booleanPresent = false;
        convertedPresent = false;
        defaultPresent = false;
        counterAbsent = true;
        numberPresent = false;
        integerPresent = false;
        longPresent = false;

        for (HashMap<String, Object> m : metrics) {
            assertNotNull(m.get("name"));
            assertNotNull(m.get("value"));
            assertNotNull(m.get("tags"));

            String name = (String) (m.get("name"));
            Double value = (Double) (m.get("value"));
            String[] tags = (String[]) (m.get("tags"));

            // All metrics should be tagged with "instance:jmx_test_instance"
            assertTrue(Arrays.asList(tags).contains("instance:jmx_test_instance"));
            assertTrue(Arrays.asList(tags).contains("env:stage"));
            assertTrue(Arrays.asList(tags).contains("newTag:test"));

            if (name.equals("this.is.100")) {
                assertEquals(tags.length, 8);
                assertEquals(value, new Double(100.0));
                metric100Present = true;

            } else if (name.equals("jmx.org.datadog.jmxfetch.test.should_be1000")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(1000.0));
                metric1000Present = true;

            } else if (name.equals("jmx.org.datadog.jmxfetch.test.number_big")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(1.2345678890123457E20));
                numberPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.long42424242")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(4.2424242E7));
                longPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.int424242")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(424242.0));
                integerPresent = true;


            } else if (name.equals("test.counter")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(0.0)); // We didn't increment the counter, hence a value of 0.0 is what we want
                counterAbsent = false;

            } else if (name.equals("subattr.this.is.0")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(0.0));
                subattr0Present = true;

            } else if (name.equals("subattr.counter")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(0.0)); // We didn't increment the counter, hence a value of 0.0 is what we want
                subattrCounterAbsent = false;

            } else if (name.equals("test.boolean")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(1.0));
                booleanPresent = true;

            } else if (name.equals("test.converted")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(5.0));
                convertedPresent = true;

            } else if (name.equals("test.defaulted")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(32.0)); // We didn't increment the counter, hence a value of 0.0 is what we want
                defaultPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic42")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(42.0));
                atomicIntPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic4242")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(4242.0));
                atomicLongPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.object1337")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(13.37));
                objectPresent = true;
            }
        }

        assertTrue(metric100Present);
        assertTrue(metric1000Present);
        assertTrue(booleanPresent);
        assertTrue(convertedPresent);
        assertTrue(defaultPresent);
        assertFalse(counterAbsent);
        assertTrue(subattr0Present);
        assertFalse(subattrCounterAbsent);
        assertTrue(atomicIntPresent);
        assertTrue(atomicLongPresent);
        assertTrue(objectPresent);
        assertTrue(numberPresent);
        assertTrue(longPresent);
        assertTrue(integerPresent);


        // We run a 3rd collection but this time we increment the counter and we sleep
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);

        app.doIteration();
        metrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
        assertEquals(metrics.size(), 27); // 27 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 7 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

        metric100Present = false;
        metric1000Present = false;
        atomicIntPresent = false;
        atomicLongPresent = false;
        objectPresent = false;
        booleanPresent = false;
        convertedPresent = false;
        defaultPresent = false;
        numberPresent = false;
        integerPresent = false;
        longPresent = false;


        counterAbsent = true;
        HashMap<String, Integer> jvm_metrics = new HashMap<String, Integer>();
        jvm_metrics.put("jvm.gc.cms.count", 2);
        jvm_metrics.put("jvm.gc.parnew.time", 2);
        jvm_metrics.put("jvm.heap_memory", 1);
        jvm_metrics.put("jvm.heap_memory_committed", 1);
        jvm_metrics.put("jvm.heap_memory_init", 1);
        jvm_metrics.put("jvm.heap_memory_max", 1);
        jvm_metrics.put("jvm.non_heap_memory", 1);
        jvm_metrics.put("jvm.non_heap_memory_committed", 1);
        jvm_metrics.put("jvm.non_heap_memory_init", 1);
        jvm_metrics.put("jvm.non_heap_memory_max", 1);

        jvm_metrics.put("jvm.thread_count", 1);

        for (HashMap<String, Object> m : metrics) {
            assertNotNull(m.get("name"));
            assertNotNull(m.get("value"));
            assertNotNull(m.get("tags"));

            String name = (String) (m.get("name"));
            Double value = (Double) (m.get("value"));
            String[] tags = (String[]) (m.get("tags"));

            // All metrics should be tagged with "instance:jmx_test_instance"
            assertTrue(Arrays.asList(tags).contains("instance:jmx_test_instance"));
            assertTrue(Arrays.asList(tags).contains("env:stage"));
            assertTrue(Arrays.asList(tags).contains("newTag:test"));

            if (name.equals("this.is.100")) {
                assertEquals(tags.length, 8);
                assertEquals(value, new Double(100.0));
                metric100Present = true;

            } else if (name.equals("jmx.org.datadog.jmxfetch.test.number_big")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(1.2345678890123457E20));
                numberPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.long42424242")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(4.2424242E7));
                longPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.int424242")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(424242.0));
                integerPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.should_be1000")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(1000.0));
                metric1000Present = true;
            } else if (name.equals("test.counter")) {
                assertEquals(tags.length, 5);
                // The value should be a bit less than 1.0, as we incremented the counter by 5 and we slept for 5 seconds
                assertTrue(value < 1.00);
                assertTrue(value > 0.98);
                counterAbsent = false;
            } else if (name.equals("subattr.this.is.0")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(0.0));
                subattr0Present = true;

            } else if (name.equals("test.boolean")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(1.0));
                booleanPresent = true;

            } else if (name.equals("test.converted")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(5.0));
                convertedPresent = true;

            } else if (name.equals("test.defaulted")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(32.0));
                defaultPresent = true;

            } else if (name.equals("subattr.counter")) {
                assertEquals(tags.length, 5);
                // The value should be a bit less than 1.0, as we incremented the counter by 5 and we slept for 5 seconds
                assertTrue(value < 1.00);
                assertTrue(value > 0.98);
                subattrCounterAbsent = false;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic42")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(42.0));
                atomicIntPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic4242")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(4242.0));
                atomicLongPresent = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.object1337")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(13.37));
                objectPresent = true;


            } else {
                // Those are jvm metrics
                assertTrue(jvm_metrics.containsKey(name));
                jvm_metrics.put(name, jvm_metrics.get(name) - 1);
            }


        }

        assertTrue(metric100Present);
        assertTrue(metric1000Present);
        assertTrue(booleanPresent);
        assertTrue(convertedPresent);
        assertTrue(defaultPresent);
        assertTrue(metric1000Present);
        assertFalse(counterAbsent);
        assertTrue(subattr0Present);
        assertFalse(subattrCounterAbsent);
        assertTrue(atomicIntPresent);
        assertTrue(atomicLongPresent);
        assertTrue(objectPresent);
        assertTrue(numberPresent);
        assertTrue(longPresent);
        assertTrue(integerPresent);

        for (int i : jvm_metrics.values()) {
            assertEquals(0, i);
        }
    }
}
