package org.datadog.jmxfetch;

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
}
