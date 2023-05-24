package org.datadog.jmxfetch;

import javax.management.ObjectName;

public interface BeanListener {
    public void beanRegistered(ObjectName beanName);

    public void beanUnregistered(ObjectName beanName);
}
