package org.datadog.jmxfetch.util;

public interface AppTelemetryMBean {

    int getRunningInstanceCount();

    int getBrokenInstanceCount();

    int getBrokenInstanceEventCount();

}
