package org.datadog.jmxfetch;

import org.datadog.jmxfetch.service.ServiceNameProvider;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class JmxComplexAttribute extends JmxSubAttribute {

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
    public List<Metric> getMetrics() throws AttributeNotFoundException, MBeanException,
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
        String attributeType = getAttribute().getType();

        if ("javax.management.openmbean.CompositeData".equals(attributeType)
                || "javax.management.openmbean.CompositeDataSupport".equals(attributeType)) {
            CompositeData data = (CompositeData) value;
            return data.get(subAttribute);
        } else if (("java.util.HashMap".equals(attributeType))
                || ("java.util.Map".equals(attributeType))) {
            Map<String, Object> data = (Map<String, Object>) value;
            return data.get(subAttribute);
        }
        log.warn("Could not retrieve value from bean '{}', attribute '{}', subattribute '{}'.", super.beanStringName,
                super.getAttributeName(), subAttribute);
        throw new NumberFormatException();
    }

    @Override
    public boolean match(Configuration configuration) {
        log.info("checking match for config {} with domain {} classname {} and bean {}", configuration,
                this.getDomain(), super.className, super.beanStringName);
        if (!matchDomain(configuration)
                || !matchClassName(configuration)
                || !matchBean(configuration)
                || excludeMatchDomain(configuration)
                || excludeMatchClassName(configuration)
                || excludeMatchBean(configuration)) {
            log.info("Failed the domain, class, bean checks. {} returns false.", super.beanStringName);
            return false;
        }

        try {
            populateSubAttributeList(getJmxValue());
        } catch (Exception e) {
            log.info("Couldn't populate subattribute list, returning false for bean {}: ", super.beanStringName, e);
            return false;
        }

        log.info("Made it to the matchAttribute step for attribute {} (bean: {}), checking....", this.getAttribute(),
                super.beanStringName);

        return matchAttribute(configuration) && !excludeMatchAttribute(configuration);
    }

    private boolean matchSubAttribute(
            Filter params, String subAttributeName, boolean matchOnEmpty) {
        log.info("MatchSubAttribute checking for subattr name {} with filters: {}", subAttributeName, params);
        if ((params.getAttribute() instanceof Map<?, ?>)
                && ((Map<String, Object>) (params.getAttribute()))
                        .containsKey(subAttributeName)) {
            log.info("MatchSubAttribute Found attribute name in include map, returning true.");
            return true;
        } else if ((params.getAttribute() instanceof List<?>
                && ((List<String>) (params.getAttribute())).contains(subAttributeName))) {
            log.info("MatchSubAttribute Found attribute name in include list, returning true.");
            return true;
        } else if (params.getAttribute() == null) {
            log.info("Returning matchonEmpty {}", matchOnEmpty);
            return matchOnEmpty;
        }
        log.info("MatchSubAttribute did not match. REturning false.");
        return false;
    }

    private boolean matchAttribute(Configuration configuration) {
        if (matchSubAttribute(configuration.getInclude(), getAttributeName(), true)) {
            return true;
        }

        Iterator<String> it = subAttributeList.iterator();

        while (it.hasNext()) {
            String subAttribute = it.next();
            log.info("Checking subattribute named {}", subAttribute);
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
