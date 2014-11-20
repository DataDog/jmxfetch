package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;

@SuppressWarnings("unchecked")
public class JMXSimpleAttribute extends JMXAttribute {

    private String alias;
    private String metricType;

    public JMXSimpleAttribute(MBeanAttributeInfo attribute, ObjectInstance instance, String instanceName,
                              Connection connection, HashMap<String, String> instanceTags) {
        super(attribute, instance, instanceName, connection, instanceTags);
    }

    @Override
    public LinkedList<HashMap<String, Object>> getMetrics() throws AttributeNotFoundException,
            InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        HashMap<String, Object> metric = new HashMap<String, Object>();

        metric.put("alias", getAlias());
        metric.put("value", getValue());
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
        LinkedHashMap<String, Object> exclude = configuration.getExclude();
        if (exclude.get("attribute") == null) {
            return false;
        } else if ((exclude.get("attribute") instanceof LinkedHashMap<?, ?>)
                && ((LinkedHashMap<String, Object>) (exclude.get("attribute"))).containsKey(getAttributeName())) {
            return true;

        } else if ((exclude.get("attribute") instanceof ArrayList<?>
                && ((ArrayList<String>) (exclude.get("attribute"))).contains(getAttributeName()))) {
            return true;
        }
        return false;
    }

    private boolean matchAttribute(Configuration configuration) {
        LinkedHashMap<String, Object> include = configuration.getInclude();
        if (include.get("attribute") == null) {
            return true;

        } else if ((include.get("attribute") instanceof LinkedHashMap<?, ?>)
                && ((LinkedHashMap<String, Object>) (include.get("attribute"))).containsKey(getAttributeName())) {
            return true;

        } else if ((include.get("attribute") instanceof ArrayList<?>
                && ((ArrayList<String>) (include.get("attribute"))).contains(getAttributeName()))) {
            return true;
        }

        return false;
    }

    private String getAlias() {
        LinkedHashMap<String, Object> include = getMatchingConf().getInclude();
        LinkedHashMap<String, Object> conf = getMatchingConf().getConf();
        if (alias != null) {
            return alias;
        } else if (include.get("attribute") instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<String, LinkedHashMap<String, String>> attribute = (LinkedHashMap<String, LinkedHashMap<String, String>>) (include.get("attribute"));
            alias = attribute.get(getAttribute().getName()).get("alias");
        } else if (conf.get("metric_prefix") != null) {
            alias = conf.get("metric_prefix") + "." + getBeanName().split(":")[0] + "." + getAttributeName();
        } else {
            alias = "jmx." + getBeanName().split(":")[0] + "." + getAttributeName();
        }
        alias = convertMetricName(alias);
        return alias;
    }

    private String getMetricType() {
        LinkedHashMap<String, Object> include = getMatchingConf().getInclude();
        if (metricType != null) {
            return metricType;
        } else if (include.get("attribute") instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<String, LinkedHashMap<String, String>> attribute = (LinkedHashMap<String, LinkedHashMap<String, String>>) (include.get("attribute"));
            metricType = attribute.get(getAttributeName()).get("metric_type");
            if (metricType == null) {
                metricType = attribute.get(getAttributeName()).get("type");
            }
        }

        if (metricType == null) { // Default to gauge
            metricType = "gauge";
        }

        return metricType;
    }

    private double getValue() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
            ReflectionException, IOException, NumberFormatException {
        return getValueAsDouble(this.getJmxValue());
    }
}
