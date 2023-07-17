package org.datadog.jmxfetch.util;

public interface JmxfetchTelemetryMBean {
    
    int getBeanCount();

    int getAttributeCount();

    int getMetricCount();

}
