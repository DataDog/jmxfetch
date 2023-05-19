package org.datadog.misbehavingjmxserver;

public class SingleAttributeMetric implements SingleAttributeMetricMBean {
    private final MetricDAO metricsDAO;

    public SingleAttributeMetric(final MetricDAO metricsDAO) {
        this.metricsDAO = metricsDAO;
    }

    public synchronized int getCounter() {
        return this.metricsDAO.getNumberValue().intValue();
    }
}

