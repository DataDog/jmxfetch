package org.datadog.jmxfetch;


import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.service.ServiceNameProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

@Slf4j
@SuppressWarnings("unchecked")
public class JmxComplexAttribute extends JmxSubAttribute {
    private static final List<String> COMPOSED_TYPES =
            Arrays.asList(
                    "javax.management.openmbean.CompositeData",
                    "javax.management.openmbean.CompositeDataSupport",
                    "java.util.HashMap",
                    "java.util.Map");

    private List<String> subAttributeList = new ArrayList<String>();

    /** JmxComplexAttribute constructor. */
    public JmxComplexAttribute(
            MBeanAttributeInfo attribute,
            ObjectName beanName,
            String className,
            String instanceName,
            String checkName,
            Connection connection,
            ServiceNameProvider serviceNameProvider,
            Map<String, String> instanceTags,
            boolean emptyDefaultHostname,
            boolean normalizeBeanParamTags) {
        super(
                attribute,
                beanName,
                className,
                instanceName,
                checkName,
                connection,
                serviceNameProvider,
                instanceTags,
                false,
                emptyDefaultHostname,
                normalizeBeanParamTags);
    }

    private void populateSubAttributeList(Object attributeValue) {
        if (attributeValue instanceof javax.management.openmbean.CompositeData) {
            CompositeData data = (CompositeData) attributeValue;
            for (String key : data.getCompositeType().keySet()) {
                this.subAttributeList.add(key);
            }
        } else if (attributeValue instanceof java.util.Map) {
            Map<String, Double> data = (Map<String, Double>) attributeValue;
            for (String key : data.keySet()) {
                this.subAttributeList.add(key);
            }
        }
    }

    @Override
    public List<Metric> getMetricsImpl() throws AttributeNotFoundException, MBeanException,
            ReflectionException, InstanceNotFoundException, IOException {
        List<Metric> metrics = new ArrayList<Metric>(subAttributeList.size());
        for (String subAttribute : subAttributeList) {
            Metric metric = getCachedMetric(subAttribute);
            double value = castToDouble(getValue(subAttribute), subAttribute);
            metric.setValue(value);
            metrics.add(metric);
        }
        return metrics;
    }

    private Object getValue(String subAttribute)
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {

        Object value = this.getJmxValue();

        if (value instanceof CompositeData) {
            CompositeData data = (CompositeData) value;
            return data.get(subAttribute);
        } else if (value instanceof java.util.Map) {
            Map<String, Object> data = (Map<String, Object>) value;
            return data.get(subAttribute);
        }
        throw new NumberFormatException();
    }

    public static boolean matchAttributeType(String attributeType) {
        return COMPOSED_TYPES.contains(attributeType);
    }

    @Override
    public boolean match(Configuration configuration) {
        if (!matchDomain(configuration)
                || !matchClassName(configuration)
                || !matchBean(configuration)
                || excludeMatchDomain(configuration)
                || excludeMatchClassName(configuration)
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
