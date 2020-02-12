package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Slf4j
class MetricCollectionTask extends InstanceTask<List<HashMap<String, Object>>> {
    MetricCollectionTask(Instance instance) {
        super(instance);
        setWarning("Unable to collect metrics or refresh bean list.");
    }

    @Override
    public List<HashMap<String, Object>> call() throws Exception {

        if (!instance.timeToCollect()) {
            log.debug(
                    "it is not time to collect, skipping run for instance: " + instance.getName());

            // Maybe raise an exception here instead...
            return Collections.emptyList();
        }

        return instance.getMetrics();
    }
}
