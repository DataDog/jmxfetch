package org.datadog.jmxfetch;

/**
 * MBean interface for testing dynamic tag resolution.
 */
public interface DynamicTagTestAppMBean {
    String getClusterId();
    String getVersion();
    int getPort();
    Double getMetric();
}


