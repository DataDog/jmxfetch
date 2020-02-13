package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

@SuppressWarnings("unchecked")
public class JmxComplexAttribute extends JmxAttribute {

    private List<String> subAttributeList = new ArrayList<String>();

    /** JmxComplexAttribute constructor. */
    public JmxComplexAttribute(
            MBeanAttributeInfo attribute,
            ObjectName beanName,
            String instanceName,
            Connection connection,
            Map<String, String> instanceTags,
            boolean emptyDefaultHostname) {
        super(
                attribute,
                beanName,
                instanceName,
                connection,
                instanceTags,
                false,
                emptyDefaultHostname);
    }

    private void populateSubAttributeList(Object attributeValue) {
        String attributeType = getAttribute().getType();
        if ("javax.management.openmbean.CompositeData".equals(attributeType)) {
            CompositeData data = (CompositeData) attributeValue;
            for (String key : data.getCompositeType().keySet()) {
                this.subAttributeList.add(key);
            }
        } else if (("java.util.HashMap".equals(attributeType))
                || ("java.util.Map".equals(attributeType))) {
            Map<String, Double> data = (Map<String, Double>) attributeValue;
            for (String key : data.keySet()) {
                this.subAttributeList.add(key);
            }
        }
    }

    @Override
    public List<Metric> getMetrics() throws AttributeNotFoundException, MBeanException,
            ReflectionException, InstanceNotFoundException, IOException {
        List<Metric> metrics = new ArrayList<Metric>(subAttributeList.size());
        for (String subAttribute : subAttributeList) {
            String alias = convertMetricName(getAlias(subAttribute));
            String metricType = getMetricType(subAttribute);
            String[] tags = getTags();
            double value = castToDouble(getValue(subAttribute), subAttribute);
            metrics.add(new Metric(alias, metricType, value, tags));
        }
        return metrics;
    }

    private Object getValue(String subAttribute)
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {

        Object value = this.getJmxValue();
        String attributeType = getAttribute().getType();

        if ("javax.management.openmbean.CompositeData".equals(attributeType)) {
            CompositeData data = (CompositeData) value;
            return data.get(subAttribute);
        } else if (("java.util.HashMap".equals(attributeType))
                || ("java.util.Map".equals(attributeType))) {
            Map<String, Object> data = (Map<String, Object>) value;
            return data.get(subAttribute);
        }
        throw new NumberFormatException();
    }

    private String getMetricType(String subAttribute) {
        String subAttributeName = getAttribute().getName() + "." + subAttribute;
        String metricType = null;

        Filter include = getMatchingConf().getInclude();
        if (include.getAttribute() instanceof Map<?, ?>) {
            Map<String, Map<String, String>> attribute =
                    (Map<String, Map<String, String>>) (include.getAttribute());
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

    private boolean matchSubAttribute(
            Filter params, String subAttributeName, boolean matchOnEmpty) {
        if ((params.getAttribute() instanceof Map<?, ?>)
                && ((Map<String, Object>) (params.getAttribute()))
                        .containsKey(subAttributeName)) {
            return true;
        } else if ((params.getAttribute() instanceof List<?>
                && ((List<String>) (params.getAttribute())).contains(subAttributeName))) {
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

        Iterator<String> it = subAttributeList.iterator();

        while (it.hasNext()) {
            String subAttribute = it.next();
            if (!matchSubAttribute(
                    configuration.getInclude(), getAttributeName() + "." + subAttribute, true)) {
                it.remove();
            }
        }

        return subAttributeList.size() > 0;
    }

    private boolean excludeMatchAttribute(Configuration configuration) {

        Filter exclude = configuration.getExclude();
        if (exclude.getAttribute() != null
                && matchSubAttribute(exclude, getAttributeName(), false)) {
            return true;
        }

        Iterator<String> it = subAttributeList.iterator();
        while (it.hasNext()) {
            String subAttribute = it.next();
            if (matchSubAttribute(exclude, getAttributeName() + "." + subAttribute, false)) {
                it.remove();
            }
        }

        return subAttributeList.size() <= 0;
    }
}
