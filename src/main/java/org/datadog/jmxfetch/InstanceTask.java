package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

@Slf4j
public abstract class InstanceTask<T> implements Callable<T> {
    protected Instance instance;
    protected String warning;

    public InstanceTask(Instance instance) {
        this.instance = instance;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getWarning() {
        return warning;
    }

    public abstract T call() throws Exception;
}
