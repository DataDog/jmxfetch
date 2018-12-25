package org.datadog.jmxfetch;

public class TaskProcessException extends Exception { 
    public TaskProcessException(String errorMessage) {
        super(errorMessage);
    }
}
