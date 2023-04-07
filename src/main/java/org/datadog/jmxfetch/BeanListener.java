package org.datadog.jmxfetch;

import javax.management.ObjectName;

public interface BeanListener {
    public void beanRegistered(ObjectName mBeanName);
    public void beanUnregistered(ObjectName mBeanName);
}
