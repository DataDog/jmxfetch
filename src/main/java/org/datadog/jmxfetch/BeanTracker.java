package org.datadog.jmxfetch;

import javax.management.ObjectName;

public interface BeanTracker {
    public void trackBean(ObjectName beanName);

    public void untrackBean(ObjectName beanName);
}
