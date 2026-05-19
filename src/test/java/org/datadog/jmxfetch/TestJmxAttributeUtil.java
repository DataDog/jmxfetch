package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestJmxAttributeUtil {

    @Test
    public void testConvertMetricNameCamelCase() {
        assertEquals("jmx.my_camel_case", JmxAttribute.convertMetricName("jmx.myCamelCase"));
    }

    @Test
    public void testConvertMetricNameUpperCase() {
        assertEquals("jmx.my_metric", JmxAttribute.convertMetricName("jmx.MyMetric"));
    }

    @Test
    public void testConvertMetricNameSpecialChars() {
        assertEquals("jmx.my_metric", JmxAttribute.convertMetricName("jmx.my-metric"));
    }

    @Test
    public void testConvertMetricNameDotUnderscore() {
        assertEquals("jmx.my.metric", JmxAttribute.convertMetricName("jmx.my_.metric"));
    }

    @Test
    public void testConvertMetricNameCacheConsistency() {
        // Exercise cache: 10 distinct inputs, then re-call each to hit cache
        String[] inputs = {
            "jmx.myCamelCase", "jmx.MyMetric", "jmx.my-metric",
            "jmx.my_.metric", "jmx.HeapMemoryUsage", "jmx.GcCount",
            "jmx.ThreadCount", "jmx.ClassLoadingTotal", "jmx.UpTime", "jmx.FreeMemory"
        };
        String[] firstResults = new String[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            firstResults[i] = JmxAttribute.convertMetricName(inputs[i]);
        }
        // All cache hits must return identical results
        for (int i = 0; i < inputs.length; i++) {
            assertEquals(firstResults[i], JmxAttribute.convertMetricName(inputs[i]));
        }
    }
}
