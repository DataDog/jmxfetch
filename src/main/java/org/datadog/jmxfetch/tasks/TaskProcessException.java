package org.datadog.jmxfetch.tasks;

public class TaskProcessException extends Exception { 
    public TaskProcessException(String errorMessage) {
        super(errorMessage);
    }
}
