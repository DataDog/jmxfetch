package org.datadog.jmxfetch;

public class DynamicTagTestApp implements DynamicTagTestAppMBean {
    private final String clusterId;
    private final String version;
    private final int port;
    private double metric;

    public DynamicTagTestApp() {
        this("local-kafka-cluster", "2.8.0", 9092);
    }

    public DynamicTagTestApp(String clusterId, String version, int port) {
        this.clusterId = clusterId;
        this.version = version;
        this.port = port;
        this.metric = 100.0;
    }

    @Override
    public String getClusterId() {
        return clusterId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Double getMetric() {
        return metric;
    }

    public void setMetric(double metric) {
        this.metric = metric;
    }
}


