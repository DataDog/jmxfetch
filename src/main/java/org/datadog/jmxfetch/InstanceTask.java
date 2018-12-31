package org.datadog.jmxfetch;

import java.util.concurrent.Callable;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;

public abstract class InstanceTask<T> implements Callable<T> {
    protected final static Logger LOGGER = Logger.getLogger(InstanceTask.class.getName());
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

    abstract public T call() throws Exception;
}

class MetricCollectionTask extends InstanceTask<LinkedList<HashMap<String, Object>>> {
    MetricCollectionTask(Instance instance) {
        super(instance);
        setWarning("Unable to collect metrics or refresh bean list.");
    }

    @Override
    public LinkedList<HashMap<String, Object>> call() throws Exception {

        if (!instance.timeToCollect()) {
            LOGGER.debug("it is not time to collect, skipping run for instance: " + instance.getName());

            // Maybe raise an exception here instead...
            return new LinkedList<HashMap<String, Object>>();
        }

        return instance.getMetrics();
    }
}

class InstanceInitializingTask extends InstanceTask<Void> {
        boolean reconnect;

        InstanceInitializingTask(Instance instance, boolean reconnect) {
            super(instance);
            this.reconnect = reconnect;
            setWarning("Unable to instantiate or initialize instance " + instance);
        }

        @Override
        public Void call() throws Exception {
            // Try to reinit the connection and force to renew it
            LOGGER.info("Trying to reconnect to: " + instance);

            instance.init(reconnect);
            return null;
        }
};

class InstanceCleanupTask extends InstanceTask<Void> {

        InstanceCleanupTask(Instance instance) {
            super(instance);
            setWarning("Unable to cleanup instance " + instance);
        }

        @Override
        public Void call() throws Exception {
            LOGGER.info("Trying to cleanup: " + instance);

            instance.cleanUp();
            return null;
        }
};

