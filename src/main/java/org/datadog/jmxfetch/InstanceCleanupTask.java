package org.datadog.jmxfetch;

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
}
