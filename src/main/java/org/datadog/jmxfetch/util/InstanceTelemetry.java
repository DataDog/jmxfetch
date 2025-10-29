package org.datadog.jmxfetch.util;


/** Jmxfetch telemetry JMX MBean. */
public class InstanceTelemetry implements InstanceTelemetryMBean {

    private int beansFetched;
    private int topLevelAttributeCount;
    private int metricCount;
    private int wildcardDomainQueryCount;
    private double beanMatchRatio;
    private int beanRegistrationsHandled;
    private int beanUnregistrationsHandled;

    /** Jmxfetch telemetry bean constructor. */
    public InstanceTelemetry() {
        beansFetched = 0;
        topLevelAttributeCount = 0;
        metricCount = 0;
        wildcardDomainQueryCount = 0;
        // This needs to be re-thought a bit
        // it makes sense in a bean-refresh-loop world
        // but in a subscription-world
        // it's not clear what this should be
        // current thought is to split this
        // into two fields:
        // - numBeansWithMatchingAttributes
        // - numBeansWithoutMatchingAttributes
        // a bit wordy though.
        beanMatchRatio = 0.0;
        beanRegistrationsHandled = 0;
        beanUnregistrationsHandled = 0;
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

    public int getBeanRegistrationsHandled() {
        return beanRegistrationsHandled;
    }

    public int getBeanUnregistrationsHandled() {
        return beanUnregistrationsHandled;
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

    public void setBeanRegistrationsHandled(int count) {
        beanRegistrationsHandled = count;
    }

    public void setBeanUnregistrationsHandled(int count) {
        beanUnregistrationsHandled = count;
    }

}
