package org.datadog.jmxfetch.util;


/** jmxfetch telemetry JMX MBean */
public class JmxfetchTelemetry implements JmxfetchTelemetryMBean {

    private int beanCount;
    private int attributeCount;
    private int metricCount;

    public JmxfetchTelemetry() {
        beanCount = 0;
        attributeCount = 0;
        metricCount = 0;
    }

    public int getBeanCount() {
        return beanCount;
    }

    public int getAttributeCount() {
        return attributeCount;
    }

    public int getMetricCount() {
        return metricCount;
    }


    public void setBeanCount(int count) {
        beanCount = count;
    }

    public void setAttributeCount(int count) {
        attributeCount = count;
    }

    public void setMetricCount(int count) {
        metricCount = count;
    }


}
