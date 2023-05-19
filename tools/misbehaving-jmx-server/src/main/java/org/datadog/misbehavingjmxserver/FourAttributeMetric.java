package org.datadog.misbehavingjmxserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FourAttributeMetric implements FourAttributeMetricMBean {
    public final String name;
    private final MetricDAO metricsDAO;

    public FourAttributeMetric(final String name, final MetricDAO metricsDAO) {
        this.name = name;
        this.metricsDAO = metricsDAO;
    }

    @Override
    public Number getNumberValue() {
        return this.metricsDAO.getNumberValue();
    }

    @Override
    public Double getDoubleValue() {
        return this.metricsDAO.getDoubleValue();
    }

    @Override
    public Float getFloatValue() {
        return this.metricsDAO.getFloatValue();
    }

    @Override
    public Boolean getBooleanValue() {
        return this.metricsDAO.getBooleanValue();
    }
}
