package org.datadog.jmxfetch;

import org.apache.log4j.Logger;

import java.util.concurrent.Callable;

public abstract class InstanceTask<T> implements Callable<T> {
    protected static final Logger LOGGER = Logger.getLogger(InstanceTask.class.getName());
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
