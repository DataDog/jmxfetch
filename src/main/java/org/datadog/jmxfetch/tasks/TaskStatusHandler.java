package org.datadog.jmxfetch;

class TaskStatusHandler {
    Object data;
    Throwable throwableStatus;

    public TaskStatusHandler() {}

    public TaskStatusHandler(Throwable t) {
        throwableStatus = t;
    }

    public TaskStatusHandler(Object o, Throwable t) {
        data = o;
        throwableStatus = t;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Object getData() {
        return data;
    }

    public void setThrowableStatus(Throwable t) {
        throwableStatus = t;
    }

    public void raiseForStatus() throws Throwable {
        if (throwableStatus != null) {
            throw throwableStatus;
        }
    }
}

