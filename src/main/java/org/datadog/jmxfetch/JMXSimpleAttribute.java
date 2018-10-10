package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

@SuppressWarnings("unchecked")
public class JMXSimpleAttribute extends JMXAttribute {
    private String metricType;

    public JMXSimpleAttribute(MBeanAttributeInfo attribute, ObjectName beanName, String instanceName,
                              Connection connection, HashMap<String, String> instanceTags, boolean cassandraAliasing,
                              Boolean emptyDefaultHostname) {
        super(attribute, beanName, instanceName, connection, instanceTags, cassandraAliasing, emptyDefaultHostname);
    }

    @Override
    public LinkedList<HashMap<String, Object>> getMetrics() throws AttributeNotFoundException,
            InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        HashMap<String, Object> metric = new HashMap<String, Object>();

        metric.put("alias", getAlias());
        metric.put("value", castToDouble(getValue(), null));
        metric.put("tags", getTags());
        metric.put("metric_type", getMetricType());
        LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
        metrics.add(metric);
        return metrics;
    }

    public boolean match(Configuration configuration) {
        return matchDomain(configuration)
                && matchBean(configuration)
                && matchAttribute(configuration)
                && !(
                excludeMatchDomain(configuration)
                        || excludeMatchBean(configuration)
                        || excludeMatchAttribute(configuration));
    }

    private boolean excludeMatchAttribute(Configuration configuration) {
        Filter exclude = configuration.getExclude();
        if (exclude.getAttribute() == null) {
            return false;
        } else if ((exclude.getAttribute() instanceof LinkedHashMap<?, ?>)
                && ((LinkedHashMap<String, Object>) (exclude.getAttribute())).containsKey(getAttributeName())) {
            return true;

        } else if ((exclude.getAttribute() instanceof ArrayList<?>
                && ((ArrayList<String>) (exclude.getAttribute())).contains(getAttributeName()))) {
            return true;
        }
        return false;
    }

    private boolean matchAttribute(Configuration configuration) {
        Filter include = configuration.getInclude();
        if (include.getAttribute() == null) {
            return true;

        } else if ((include.getAttribute() instanceof LinkedHashMap<?, ?>)
                && ((LinkedHashMap<String, Object>) (include.getAttribute())).containsKey(getAttributeName())) {
            return true;

        } else if ((include.getAttribute() instanceof ArrayList<?>
                && ((ArrayList<String>) (include.getAttribute())).contains(getAttributeName()))) {
            return true;
        }

        return false;
    }

    private String getMetricType() {
        Filter include = getMatchingConf().getInclude();
        if (metricType != null) {
            return metricType;
        } else if (include.getAttribute() instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<String, LinkedHashMap<String, String>> attribute = (LinkedHashMap<String, LinkedHashMap<String, String>>) (include.getAttribute());
            metricType = attribute.get(getAttributeName()).get(METRIC_TYPE);
            if (metricType == null) {
                metricType = attribute.get(getAttributeName()).get("type");
            }
        }

        if (metricType == null) { // Default to gauge
            metricType = "gauge";
        }

        return metricType;
    }

    private Object getValue() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
            ReflectionException, IOException, NumberFormatException {
        return this.getJmxValue();
    }
}
