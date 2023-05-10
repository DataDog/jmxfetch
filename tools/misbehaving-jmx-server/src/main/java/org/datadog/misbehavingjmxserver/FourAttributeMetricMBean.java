package org.datadog.misbehavingjmxserver;

public interface FourAttributeMetricMBean {
    Number getNumberValue();

    Double getDoubleValue();

    Float getFloatValue();

    Boolean getBooleanValue();
}
