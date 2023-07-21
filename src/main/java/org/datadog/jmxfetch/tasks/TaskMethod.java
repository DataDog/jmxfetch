package org.datadog.jmxfetch.tasks;

import java.util.concurrent.Future;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.reporter.Reporter;

public interface TaskMethod<T> {
  TaskStatusHandler invoke(Instance instance, Future<T> future, Reporter reporter);
}
