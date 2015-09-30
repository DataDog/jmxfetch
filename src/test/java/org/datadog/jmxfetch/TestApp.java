package org.datadog.jmxfetch;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import static org.junit.Assert.*;

public class TestApp extends TestCommon {

    @Test
    public void testBeanTags() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=SimpleTestJavaApp,scope=CoolScope,host=localhost,component=");
        initApplication("jmx_bean_tags.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 14 = 13 metrics from java.lang + 1 metric explicitly defined in the yaml config file
        assertEquals(14, metrics.size());


        // Fetching our 'defined' metric tags
        for (HashMap<String, Object> m : metrics) {
            String name = (String) (m.get("name"));
            if(!name.equals("this.is.100")){
                continue;
            }
            String[] tags = (String[]) (m.get("tags"));
            Set<String> tagsSet = new HashSet<String>(Arrays.asList(tags));

            // We should find bean parameters as tags
            assertEquals(6, tags.length);
            assertEquals(true, tagsSet.contains("type:SimpleTestJavaApp"));
            assertEquals(true, tagsSet.contains("scope:CoolScope"));
            assertEquals(true, tagsSet.contains("instance:jmx_test_instance"));
            assertEquals(true, tagsSet.contains("jmx_domain:org.datadog.jmxfetch.test"));
            // Special case of the 'host' parameter which tag is renamed to 'bean_host'
            assertEquals(true, tagsSet.contains("bean_host:localhost"));
            // Empty values should also be added as tags, without the colon
            assertEquals(true, tagsSet.contains("component"));
        }
    }

    @Test
    public void testDomainInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.includeme:type=AType");
        initApplication("jmx_domain_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 27 = 13 metrics from java.lang + 13 metrics implicitly defined
        assertEquals(27, metrics.size());
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

        // First filter 14 = 13 metrics from java.lang + 2 metrics explicitly define- 1 implicitly defined in the exclude section
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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 15 = 13 metrics from java.lang + 3 metrics explicitly defined - 1 implicitly defined in exclude section
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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // 13 default metrics from java.lang
        assertEquals(13, metrics.size());
    }

    @Test
    public void testListParamsInclude() throws Exception {
        // We expose a few metrics through JMX
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=RightType");

        // Initializing application
        initApplication("jmx_list_params_include.yaml");

        // Collecting metrics
        run();
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

        // First filter 13 = 13 metrics from java.lang + 2 metrics explicitly defined - 2 explicitly defined
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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

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
        LinkedList<HashMap<String, Object>> metrics = getMetrics();

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

        assertEquals(26, metrics.size()); // 26 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + the 8 gauges that is implicitly collected, see jmx.yaml in the test/resources folder

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
        run();
        metrics = getMetrics();
        assertEquals(28, metrics.size()); // 28 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 8 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

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

        run();
        metrics = getMetrics();
        assertEquals(metrics.size(), 28); // 28 = 13 metrics from java.lang + the 5 gauges we are explicitly collecting + 8 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

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
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.primitive_float")) {
                assertEquals(tags.length, 5);
                assertEquals(value, new Double(123.4f));
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
