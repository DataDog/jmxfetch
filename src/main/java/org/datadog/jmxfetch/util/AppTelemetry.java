package org.datadog.jmxfetch.util;


/** Jmxfetch telemetry JMX MBean. */
public class AppTelemetry implements AppTelemetryMBean {
    private int numRunningInstances;
    private int numBrokenInstances;
    private int numBrokenInstancesTotal;

    /** Jmxfetch telemetry bean constructor. */
    public AppTelemetry() {
        numRunningInstances = 0;
        numBrokenInstances = 0;
        numBrokenInstancesTotal = 0;
    }

    public int getNumRunningInstances() {
        return numRunningInstances;
    }

    public int getNumBrokenInstances() {
        return numBrokenInstances;
    }

    public int getNumBrokenInstancesTotal() {
        return numBrokenInstancesTotal;
    }

    public void setNumRunningInstances(int count) {
        numRunningInstances = count;
    }

    public void setNumBrokenInstances(int count) {
        numBrokenInstances = count;
    }

    public void setNumBrokenInstancesTotal(int count) {
        numBrokenInstancesTotal = count;
    }
}
