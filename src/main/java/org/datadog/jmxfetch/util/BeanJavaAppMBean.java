package org.datadog.jmxfetch.util;

public interface BeanJavaAppMBean {
    
    int getBeanCount();

    int getAttributeCount();

    int getMetricCount();

    String getInstance();
}
