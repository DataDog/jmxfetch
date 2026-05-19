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
}
