package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;

@Slf4j
class MetricCollectionTask extends InstanceTask<LinkedList<HashMap<String, Object>>> {
    MetricCollectionTask(Instance instance) {
        super(instance);
        setWarning("Unable to collect metrics or refresh bean list.");
    }

    @Override
    public LinkedList<HashMap<String, Object>> call() throws Exception {

        if (!instance.timeToCollect()) {
            LOGGER.debug(
                    "it is not time to collect, skipping run for instance: " + instance.getName());

            // Maybe raise an exception here instead...
            return new LinkedList<HashMap<String, Object>>();
        }

        return instance.getMetrics();
    }
}
