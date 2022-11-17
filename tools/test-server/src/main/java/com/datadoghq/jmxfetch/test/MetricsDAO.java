package com.datadoghq.jmxfetch.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MetricsDAO {
    private final AtomicReference<Double> doubleValue;
    private final AtomicReference<Float> floatValue;
    private final AtomicBoolean boolValue;
    private final AtomicReference<Number> numberValue;

    public MetricsDAO() {
        this.doubleValue = new AtomicReference<>(0.0);
        this.floatValue = new AtomicReference<>((float) 0);
        this.boolValue = new AtomicBoolean();
        this.numberValue = new AtomicReference<Number>(0);
    }

    public Number getNumberValue() {
        return this.numberValue.get();
    }

    public Double getDoubleValue() {
        return this.doubleValue.get();
    }

    public Float getFloatValue() {
        return this.floatValue.get();
    }

    public Boolean getBooleanValue() {
        return this.boolValue.get();
    }

    private void incDoubleValue() {
        final Double current = this.doubleValue.get();
        final Double next = current + 1;
        this.doubleValue.compareAndSet(current, next);
    }

    private void incFloatValue() {
        final Float current = this.floatValue.get();
        final Float next = current + 1;
        this.floatValue.compareAndSet(current, next);
    }

    private void incBooleanValue() {
        final boolean current = this.boolValue.get();
        final boolean next = !current;
        this.boolValue.compareAndSet(current, next);
    }

    private void incNumberValue() {
        final Number current = this.numberValue.get();
        final Number next = current.intValue() + 1;
        this.numberValue.compareAndSet(current, next);
    }

    public void Do() {
        this.incDoubleValue();
        this.incFloatValue();
        this.incBooleanValue();
        this.incNumberValue();
    }

}
