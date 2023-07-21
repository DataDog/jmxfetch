package org.datadog.jmxfetch.tasks;

public class TaskStatusHandler {
  Object data;
  Throwable throwableStatus;

  /** Default constructor. */
  public TaskStatusHandler() {}

  /** Constructor sets throwable for status reporting. */
  public TaskStatusHandler(Throwable throwable) {
    throwableStatus = throwable;
  }

  /**
   * Constructor sets throwable for status reporting, and generic object if return data is expected.
   */
  public TaskStatusHandler(Object obj, Throwable throwable) {
    data = obj;
    throwableStatus = throwable;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public Object getData() {
    return data;
  }

  public void setThrowableStatus(Throwable throwable) {
    throwableStatus = throwable;
  }

  /** Throws the throwable status set in the handler if not null. */
  public void raiseForStatus() throws Throwable {
    if (throwableStatus != null) {
      throw throwableStatus;
    }
  }
}
