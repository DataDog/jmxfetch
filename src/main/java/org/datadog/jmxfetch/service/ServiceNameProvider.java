package org.datadog.jmxfetch.service;

public interface ServiceNameProvider {
    Iterable<String> getServiceNames();
}
