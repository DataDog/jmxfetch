package com.datadoghq.jmxfetch.test;

public interface MetricsMBean {
    String getName();
    Number getNumberValue();
    Double getDoubleValue();
    Float getFloatValue();
    Boolean getBooleanValue();

    void Do();
}
