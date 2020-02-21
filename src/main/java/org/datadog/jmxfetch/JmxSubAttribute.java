package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;

abstract class JmxSubAttribute extends JmxAttribute {
    private Map<String, Metric> cachedMetrics = new HashMap<String, Metric>();

    public JmxSubAttribute(MBeanAttributeInfo attribute, ObjectName beanName, String instanceName,
            String checkName, Connection connection, Map<String, String> instanceTags,
            boolean cassandraAliasing, boolean emptyDefaultHostname) {
        super(attribute, beanName, instanceName, checkName, connection, instanceTags,
                cassandraAliasing, emptyDefaultHostname);
    }

    public Metric getCachedMetric(String name) {
        Metric metric = cachedMetrics.get(name);
        if (metric != null) {
            return metric;
        }
        String alias = getAlias(name);
        String metricType = getMetricType(name);
        String[] tags = getTags();
        metric = new Metric(alias, metricType, tags, checkName);
        cachedMetrics.put(name, metric);
        return metric;
    }
}
