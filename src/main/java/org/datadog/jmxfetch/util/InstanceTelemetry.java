package org.datadog.jmxfetch.util;


/** Jmxfetch telemetry JMX MBean. */
public class InstanceTelemetry implements InstanceTelemetryMBean {

    private int beansFetched;
    private int topLevelAttributeCount;
    private int metricCount;
    private int wildcardDomainQueryCount;
    private double beanMatchRatio;

    /** Jmxfetch telemetry bean constructor. */
    public InstanceTelemetry() {
        beansFetched = 0;
        topLevelAttributeCount = 0;
        metricCount = 0;
        wildcardDomainQueryCount = 0;
        beanMatchRatio = 0.0;
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

    public int getWildcardDomainQueryCount() {
        return wildcardDomainQueryCount;
    }

    public double getBeanMatchRatio() {
        return beanMatchRatio;
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

    public void setWildcardDomainQueryCount(int count) {
        wildcardDomainQueryCount = count;
    }

    public void setBeanMatchRatio(double ratio) {
        beanMatchRatio = ratio;
    }

}
