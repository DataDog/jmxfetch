package com.datadoghq.jmxfetch.test;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Metrics implements MetricsMBean, MBeanRegistration {
    private final String name;
    private final AtomicReference<Double> doubleValue;
    private final AtomicReference<Float> floatValue;
    private final AtomicBoolean boolValue;
    private final AtomicReference<Number> numberValue;

    public Metrics(String name) {
        this.name = name;
        this.doubleValue = new AtomicReference<>(0.0);
        this.floatValue = new AtomicReference<>((float) 0);
        this.boolValue = new AtomicBoolean();
        this.numberValue = new AtomicReference<Number>(0);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Number getNumberValue() {
        return this.numberValue.get();
    }

    @Override
    public Double getDoubleValue() {
        return this.doubleValue.get();
    }

    @Override
    public Float getFloatValue() {
        return this.floatValue.get();
    }

    @Override
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

    @Override
    public void Do() {
        this.incDoubleValue();
        this.incFloatValue();
        this.incBooleanValue();
        this.incNumberValue();
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName("test:type=MyMBean,name=" + this.name);
    }

    @Override
    public void postRegister(Boolean registrationDone) {

    }

    @Override
    public void preDeregister() throws Exception {

    }

    @Override
    public void postDeregister() {

    }
}
