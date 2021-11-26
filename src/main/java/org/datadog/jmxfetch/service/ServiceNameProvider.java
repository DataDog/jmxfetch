package org.datadog.jmxfetch.service;

import java.util.List;

public interface ServiceNameProvider {
    List<String> getServiceNames();
}
