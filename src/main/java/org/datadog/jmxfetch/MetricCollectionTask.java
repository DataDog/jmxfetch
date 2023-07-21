package org.datadog.jmxfetch;

import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricCollectionTask extends InstanceTask<List<Metric>> {
  MetricCollectionTask(Instance instance) {
    super(instance);
    setWarning("Unable to collect metrics or refresh bean list.");
  }

  @Override
  public List<Metric> call() throws Exception {

    if (!instance.timeToCollect()) {
      log.debug("it is not time to collect, skipping run for instance: " + instance.getName());

      // Maybe raise an exception here instead...
      return Collections.emptyList();
    }

    return instance.getMetrics();
  }
}
