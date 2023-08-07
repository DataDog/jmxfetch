package org.datadog.jmxfetch.util;

public interface InstanceTelemetryMBean {

    int getBeanCount();

    int getAttributeCount();

    int getMetricCount();

}
