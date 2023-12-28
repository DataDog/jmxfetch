package org.datadog.jmxfetch.util;

import java.util.concurrent.atomic.AtomicInteger;

/** Jmxfetch telemetry JMX MBean. */
public class AppTelemetry implements AppTelemetryMBean {
    private AtomicInteger runningInstanceCount;
    private AtomicInteger brokenInstanceCount;
    private AtomicInteger brokenInstanceEventCount;

    /** Jmxfetch telemetry bean constructor. */
    public AppTelemetry() {
        runningInstanceCount = new AtomicInteger(0);
        brokenInstanceCount = new AtomicInteger(0);
        brokenInstanceEventCount = new AtomicInteger(0);
    }

    public int getRunningInstanceCount() {
        return runningInstanceCount.get();
    }

    public int getBrokenInstanceCount() {
        return brokenInstanceCount.get();
    }

    public int getBrokenInstanceEventCount() {
        return brokenInstanceEventCount.get();
    }

    public void setRunningInstanceCount(int count) {
        this.runningInstanceCount.set(count);
    }

    public void setBrokenInstanceCount(int count) {
        brokenInstanceCount.set(count);
    }

    public void incrementBrokenInstanceEventCount() {
        brokenInstanceEventCount.incrementAndGet();
    }
}
