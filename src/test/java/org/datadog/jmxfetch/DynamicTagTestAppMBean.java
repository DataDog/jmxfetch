package org.datadog.jmxfetch;

public interface DynamicTagTestAppMBean {
    String getClusterId();
    String getVersion();
    int getPort();
    Double getMetric();
}


