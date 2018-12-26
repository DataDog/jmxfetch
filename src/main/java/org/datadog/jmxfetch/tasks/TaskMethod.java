package org.datadog.jmxfetch.tasks;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.Instance;

import java.util.concurrent.Future;

public interface TaskMethod<T> {
  TaskStatusHandler invoke(Instance instance, Future<T> future, Reporter reporter);
}
