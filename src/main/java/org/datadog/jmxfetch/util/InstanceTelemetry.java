package org.datadog.jmxfetch.util;


/** Jmxfetch telemetry JMX MBean. */
public class InstanceTelemetry implements InstanceTelemetryMBean {

    private int beansFetched;
    private int topLevelAttributeCount;
    private int metricCount;

    /** Jmxfetch telemetry bean constructor. */
    public InstanceTelemetry() {
        beansFetched = 0;
        topLevelAttributeCount = 0;
        metricCount = 0;
    }

    public int getBeansFetched() {
        return beansFetched;
    }

    public int getTopLevelAttributeCount() {
        return topLevelAttributeCount;
    }

    public int getMetricCount() {
        return metricCount;
    }


    public void setBeansFetched(int count) {
        beansFetched = count;
    }

    public void setTopLevelAttributeCount(int count) {
        topLevelAttributeCount = count;
    }

    public void setMetricCount(int count) {
        metricCount = count;
    }


}
