package org.datadog.jmxfetch.util;

public interface InstanceTelemetryMBean {

    int getBeansFetched();

    int getTopLevelAttributeCount();

    int getMetricCount();

}
