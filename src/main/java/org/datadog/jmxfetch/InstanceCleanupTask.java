package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class InstanceCleanupTask extends InstanceTask<Void> {

  InstanceCleanupTask(Instance instance) {
    super(instance);
    setWarning("Unable to cleanup instance " + instance);
  }

  @Override
  public Void call() throws Exception {
    log.info("Trying to cleanup: " + instance);

    instance.cleanUp();
    return null;
  }
}
