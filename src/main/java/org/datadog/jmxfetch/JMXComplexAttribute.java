package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

@SuppressWarnings("unchecked")
public class JMXComplexAttribute extends JMXAttribute {

    private HashMap<String, HashMap<String, Object>> subAttributeList;

    public JMXComplexAttribute(MBeanAttributeInfo attribute, ObjectName beanName, String instanceName,
                               Connection connection, HashMap<String, String> instanceTags, boolean emptyDefaultHostname) {
        super(attribute, beanName, instanceName, connection, instanceTags, false, emptyDefaultHostname);
        this.subAttributeList = new HashMap<String, HashMap<String, Object>>();
    }

    private void populateSubAttributeList(Object attributeValue) {
        String attributeType = getAttribute().getType();
        if ("javax.management.openmbean.CompositeData".equals(attributeType)) {
            CompositeData data = (CompositeData) attributeValue;
            for (String key : data.getCompositeType().keySet()) {
                this.subAttributeList.put(key, new HashMap<String, Object>());
            }
        } else if (("java.util.HashMap".equals(attributeType)) || ("java.util.Map".equals(attributeType))){
            Map<String, Double> data = (Map<String, Double>) attributeValue;
            for (String key : data.keySet()) {
                this.subAttributeList.put(key, new HashMap<String, Object>());
            }
        }
    }

    @Override
    public LinkedList<HashMap<String, Object>> getMetrics()
            throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException {

        LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();

        for (Map.Entry<String, HashMap<String, Object>> pair : subAttributeList.entrySet()) {
            String subAttribute = pair.getKey();
            HashMap<String, Object> metric = pair.getValue();

            if (metric.get(ALIAS) == null) {
                metric.put(ALIAS, convertMetricName(getAlias(subAttribute)));
            }

            if (metric.get(METRIC_TYPE) == null) {
                metric.put(METRIC_TYPE, getMetricType(subAttribute));
            }

            if (metric.get("tags") == null) {
                metric.put("tags", getTags());
            }

            metric.put("value", castToDouble(getValue(subAttribute), subAttribute));
            metrics.add(metric);

        }
        return metrics;

    }

    private Object getValue(String subAttribute) throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException {

        Object value = this.getJmxValue();
        String attributeType = getAttribute().getType();

        if ("javax.management.openmbean.CompositeData".equals(attributeType)) {
            CompositeData data = (CompositeData) value;
            return data.get(subAttribute);
        } else if (("java.util.HashMap".equals(attributeType)) || ("java.util.Map".equals(attributeType))) {
            Map<String, Object> data = (Map<String, Object>) value;
            return data.get(subAttribute);
        }
        throw new NumberFormatException();
    }

    private Object getMetricType(String subAttribute) {
        String subAttributeName = getAttribute().getName() + "." + subAttribute;
        String metricType = null;

        Filter include = getMatchingConf().getInclude();
        if (include.getAttribute() instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<String, LinkedHashMap<String, String>> attribute = (LinkedHashMap<String, LinkedHashMap<String, String>>) (include.getAttribute());
            metricType = attribute.get(subAttributeName).get(METRIC_TYPE);
            if (metricType == null) {
                metricType = attribute.get(subAttributeName).get("type");
            }
        }

        if (metricType == null) {
            metricType = "gauge";
        }

        return metricType;
    }

    @Override
    public boolean match(Configuration configuration) {
        if (!matchDomain(configuration)
                || !matchBean(configuration)
                || excludeMatchDomain(configuration)
                || excludeMatchBean(configuration)) {
            return false;
        }

        try {
            populateSubAttributeList(getJmxValue());
        } catch (Exception e) {
            return false;
        }

        return matchAttribute(configuration) && !excludeMatchAttribute(configuration);
    }

    private boolean matchSubAttribute(Filter params, String subAttributeName, boolean matchOnEmpty) {
        if ((params.getAttribute() instanceof LinkedHashMap<?, ?>)
                && ((LinkedHashMap<String, Object>) (params.getAttribute())).containsKey(subAttributeName)) {
            return true;
        } else if ((params.getAttribute() instanceof ArrayList<?>
                && ((ArrayList<String>) (params.getAttribute())).contains(subAttributeName))) {
            return true;
        } else if (params.getAttribute() == null) {
            return matchOnEmpty;
        }
        return false;

    }

    private boolean matchAttribute(Configuration configuration) {
        if (matchSubAttribute(configuration.getInclude(), getAttributeName(), true)) {
            return true;
        }

        Iterator<String> it = subAttributeList.keySet().iterator();

        while (it.hasNext()) {
            String subAttribute = it.next();
            if (!matchSubAttribute(configuration.getInclude(), getAttributeName() + "." + subAttribute, true)) {
                it.remove();
            }
        }

        return subAttributeList.size() > 0;
    }

    private boolean excludeMatchAttribute(Configuration configuration) {

        Filter exclude = configuration.getExclude();
        if (exclude.getAttribute() != null && matchSubAttribute(exclude, getAttributeName(), false)) {
            return true;
        }

        Iterator<String> it = subAttributeList.keySet().iterator();
        while (it.hasNext()) {
            String subAttribute = it.next();
            if (matchSubAttribute(exclude, getAttributeName() + "." + subAttribute, false)) {
                it.remove();
            }
        }

        return subAttributeList.size() <= 0;
    }
}
