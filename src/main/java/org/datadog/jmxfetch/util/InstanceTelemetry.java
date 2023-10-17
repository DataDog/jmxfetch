package org.datadog.jmxfetch.util;


/** Jmxfetch telemetry JMX MBean. */
public class InstanceTelemetry implements InstanceTelemetryMBean {

    private int beansFetched;
    private int topLevelAttributeCount;
    private int metricCount;
    private int domainsQueried;
    private int wildcardQueryCount;
    private double attributeMatchRatio;

    /** Jmxfetch telemetry bean constructor. */
    public InstanceTelemetry() {
        beansFetched = 0;
        topLevelAttributeCount = 0;
        metricCount = 0;
        domainsQueried = 0;
        wildcardQueryCount = 0;
        attributeMatchRatio = 0.0;
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

    public int getDomainsQueried() {
        return domainsQueried;
    }

    public int getWildcardQueryCount() {
        return wildcardQueryCount;
    }

    public double getAttributeMatchRatio() {
        return attributeMatchRatio;
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

    public void setDomainsQueried(int count) {
        domainsQueried = count;
    }

    public void setWildcardQueryCount(int count) {
        wildcardQueryCount = count;
    }

    public void setAttributeMatchRatio(double ratio) {
        attributeMatchRatio = ratio;
    }

}
