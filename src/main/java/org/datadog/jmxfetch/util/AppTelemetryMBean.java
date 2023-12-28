package org.datadog.jmxfetch.util;

public interface AppTelemetryMBean {

    int getNumRunningInstances();

    int getNumBrokenInstances();

    int getNumBrokenInstancesTotal();

}
