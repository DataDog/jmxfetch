package org.datadog.jmxfetch;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.log4j.Level;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase
{

    static SimpleTestJavaApp testApp = new SimpleTestJavaApp();

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
        try {
            CustomLogger.setup(Level.toLevel("ALL"), "/tmp/jmxfetch_test.log");
        } catch (IOException e) {
            System.out.println("Unable to setup logging");
            e.printStackTrace();
        }
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() throws Exception
    {
        // We expose a few metrics through JMX
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("org.datadog.jmxfetch.test:type=SimpleTestJavaApp");       
        mbs.registerMBean(testApp, name);

        // We initialize the main app that will collect these metrics using JMX
        String confdDirectory = Thread.currentThread().getContextClassLoader().getResource("jmx.yaml").getPath();
        confdDirectory = confdDirectory.substring(0, confdDirectory.length() - 8);


        String[] params = {"--reporter", "console",  "-c", "jmx.yaml", "--conf_directory", confdDirectory, "collect"};

        AppConfig config = AppConfig.getInstance();
        try{
            // Try to parse the args using JCommander
            new JCommander(config, params);
        } catch(ParameterException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        App.init(config, false);

        return new TestSuite( AppTest.class );
    }

    /**
     * The actual test
     * @throws Exception 
     */
    public void testApp() throws Exception {
        // We do a first collection
        AppConfig config = AppConfig.getInstance();
        App.doIteration(config);
        LinkedList<HashMap<String, Object>> metrics = ((ConsoleReporter) config.reporter).getMetrics();

        assertEquals(metrics.size(), 13); // 13 = 7 metrics from java.lang + the 2 gauges we are explicitly collecting + the 4 gauges that is implicitly collected, see jmx.yaml in the test/resources folder

        // We test for the presence and the value of the metrics we want to collect
        boolean metric_100_present = false;
        boolean atomic_int_present = false;
        boolean atomic_long_present = false;
        boolean object_present = false;
        boolean metric_1000_present = false;
        boolean counter_absent = true;
        boolean subattr_0_present = false;
        boolean subattr_counter_absent = true;

        for (HashMap<String, Object> m : metrics) {
            String name = (String)(m.get("name"));
            Double value = (Double)(m.get("value"));
            String[] tags = (String[])(m.get("tags"));

            // All metrics should be tagged with "instance:jmx_test_instance"
            assertTrue(Arrays.asList(tags).contains("instance:jmx_test_instance"));

            if (name.equals("this.is.100")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 100.0);
                metric_100_present = true;
            }

            else if (name.equals("jmx.org.datadog.jmxfetch.test.should_be1000")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 1000.0);
                metric_1000_present = true;
            }

            else if (m.get("name").equals("test.counter")) {
                counter_absent = false;

            } else if (name.equals("subattr.this.is.0")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 0.0);
                subattr_0_present = true;

            } else if(name.equals("subattr.counter")) {
                subattr_counter_absent = false;
            }

            else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic42")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 42.0);
                atomic_int_present = true;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic4242")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 4242.0);
                atomic_long_present = true;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.object1337")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 13.37);
                object_present = true;
            }

        }

        assertTrue(metric_100_present);
        assertTrue(metric_1000_present);
        assertTrue(counter_absent);
        assertTrue(subattr_0_present);
        assertTrue(subattr_counter_absent);
        assertTrue(atomic_int_present);
        assertTrue(atomic_long_present);
        assertTrue(object_present);

        // We run a second collection. The counter should now be present        
        App.doIteration(config);
        metrics = ((ConsoleReporter) config.reporter).getMetrics();
        assertEquals(metrics.size(), 15); // 12 = 7 metrics from java.lang + the 2 gauges we are explicitly collecting + 4 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

        // We test for the same metrics but this time, the counter should be here
        metric_100_present = false;
        atomic_int_present = false;
        atomic_long_present = false;
        object_present = false;
        metric_1000_present = false;
        counter_absent = true;

        for (HashMap<String, Object> m : metrics) {
            String name = (String)(m.get("name"));
            Double value = (Double)(m.get("value"));
            String[] tags = (String[])(m.get("tags"));

            // All metrics should be tagged with "instance:jmx_test_instance"
            assertTrue(Arrays.asList(tags).contains("instance:jmx_test_instance"));

            if (name.equals("this.is.100")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 100.0);
                metric_100_present = true;

            } else if (name.equals("jmx.org.datadog.jmxfetch.test.should_be1000")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 1000.0);
                metric_1000_present = true;

            } else if (name.equals("test.counter")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 0.0); // We didn't increment the counter, hence a value of 0.0 is what we want
                counter_absent = false;

            } else if (name.equals("subattr.this.is.0")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 0.0);
                subattr_0_present = true;

            } else if(name.equals("subattr.counter")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 0.0); // We didn't increment the counter, hence a value of 0.0 is what we want
                subattr_counter_absent = false;
            }

            else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic42")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 42.0);
                atomic_int_present = true;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic4242")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 4242.0);
                atomic_long_present = true;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.object1337")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 13.37);
                object_present = true;
            }

        }

        assertTrue(metric_100_present);
        assertTrue(metric_1000_present);
        assertFalse(counter_absent);
        assertTrue(subattr_0_present);
        assertFalse(subattr_counter_absent);
        assertTrue(atomic_int_present);
        assertTrue(atomic_long_present);
        assertTrue(object_present);


        // We run a 3rd collection but this time we increment the counter and we sleep
        Thread.sleep(5000);
        testApp.incrementCounter(5);
        testApp.incrementHashMapCounter(5);

        App.doIteration(config);
        metrics = ((ConsoleReporter) config.reporter).getMetrics();
        assertEquals(metrics.size(), 15); // 15 = 7 metrics from java.lang + the 2 gauges we are explicitly collecting + 4 gauges implicitly collected + 2 counter, see jmx.yaml in the test/resources folder

        metric_100_present = false;
        metric_1000_present = false;
        atomic_int_present = false;
        atomic_long_present = false;
        object_present = false;

        counter_absent = true;
        HashMap<String, Integer> jvm_metrics = new HashMap<String, Integer>();
        jvm_metrics.put("jvm.gc.cms.count", 2);
        jvm_metrics.put("jvm.gc.parnew.time", 2);
        jvm_metrics.put("jvm.heap_memory", 1);
        jvm_metrics.put("jvm.non_heap_memory", 1);
        jvm_metrics.put("jvm.thread_count", 1);

        for (HashMap<String, Object> m : metrics) {
            String name = (String)(m.get("name"));
            Double value = (Double)(m.get("value"));
            String[] tags = (String[])(m.get("tags"));

            // All metrics should be tagged with "instance:jmx_test_instance"
            assertTrue(Arrays.asList(tags).contains("instance:jmx_test_instance"));

            if (name.equals("this.is.100")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 100.0);
                metric_100_present = true;
            } else if (name.equals("jmx.org.datadog.jmxfetch.test.should_be1000")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 1000.0);
                metric_1000_present = true;
            } else if (name.equals("test.counter")) {
                assertEquals(tags.length, 3);
                // The value should be a bit less than 1.0, as we incremented the counter by 5 and we slept for 5 seconds
                assertTrue(value < 1.00);
                assertTrue(value > 0.99);
                counter_absent = false;
            } else if (name.equals("subattr.this.is.0")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 0.0);
                subattr_0_present = true;

            } else if(name.equals("subattr.counter")) {
                assertEquals(tags.length, 3);
                // The value should be a bit less than 1.0, as we incremented the counter by 5 and we slept for 5 seconds
                assertTrue(value < 1.00);
                assertTrue(value > 0.99);
                subattr_counter_absent = false;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic42")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 42.0);
                atomic_int_present = true;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.atomic4242")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 4242.0);
                atomic_long_present = true;
            }
            else if (name.equals("jmx.org.datadog.jmxfetch.test.object1337")) {
                assertEquals(tags.length, 3);
                assertEquals(value, 13.37);
                object_present = true;


            } else {
                // Those are jvm metrics
                assertTrue(jvm_metrics.containsKey(name));
                jvm_metrics.put(name, jvm_metrics.get(name) - 1);
            }


        }

        assertTrue(metric_100_present);
        assertTrue(metric_1000_present);
        assertFalse(counter_absent);
        assertTrue(subattr_0_present);
        assertFalse(subattr_counter_absent);
        assertTrue(atomic_int_present);
        assertTrue(atomic_long_present);
        assertTrue(object_present);


        for (int i : jvm_metrics.values()) {
            assertEquals(i, 0);
        }

    }
}
