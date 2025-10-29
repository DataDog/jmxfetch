package org.datadog.jmxfetch;

import org.datadog.jmxfetch.service.ServiceNameProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

@SuppressWarnings("unchecked")
public class JmxSimpleAttribute extends JmxAttribute {
    private static final List<String> SIMPLE_TYPES =
            Arrays.asList(
                    "long",
                    "java.lang.String",
                    "int",
                    "float",
                    "double",
                    "java.lang.Double",
                    "java.lang.Float",
                    "java.lang.Integer",
                    "java.lang.Long",
                    "java.util.concurrent.atomic.AtomicInteger",
                    "java.util.concurrent.atomic.AtomicLong",
                    "java.lang.Object",
                    "java.lang.Boolean",
                    "boolean",
                    "java.lang.Number",
                    //Workaround for jasperserver, which returns attribute types as `class <type>`
                    "class java.lang.String",
                    "class java.lang.Double",
                    "class java.lang.Float",
                    "class java.lang.Integer",
                    "class java.lang.Long",
                    "class java.util.concurrent.atomic.AtomicInteger",
                    "class java.util.concurrent.atomic.AtomicLong",
                    "class java.lang.Object",
                    "class java.lang.Boolean",
                    "class java.lang.Number");
    private Metric cachedMetric;

    /** JmxSimpleAttribute constructor. */
    public JmxSimpleAttribute(
            MBeanAttributeInfo attribute,
            ObjectName beanName,
            String className,
            String instanceName,
            String checkName,
            Connection connection,
            ServiceNameProvider serviceNameProvider,
            Map<String, String> instanceTags,
            boolean cassandraAliasing,
            Boolean emptyDefaultHostname,
            Boolean normalizeBeanParamTags) {
        super(
                attribute,
                beanName,
                className,
                instanceName,
                checkName,
                connection,
                serviceNameProvider,
                instanceTags,
                cassandraAliasing,
                emptyDefaultHostname,
                normalizeBeanParamTags);
    }

    @Override
    public List<Metric> getMetricsImpl()
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {
        if (cachedMetric == null) {
            String alias = getAlias(null);
            String metricType = getMetricType(null);
            String[] tags = getTags();
            cachedMetric = new Metric(alias, metricType, tags, checkName);
        }
        double value = castToDouble(getValue(), null);
        cachedMetric.setValue(value);
        return Collections.singletonList(cachedMetric);
    }

    public static boolean matchAttributeType(String attributeType) {
        return SIMPLE_TYPES.contains(attributeType);
    }

    /** Returns whether an attribute matches in a configuration spec. */
    public boolean match(Configuration configuration) {
        return matchDomain(configuration)
                && matchClassName(configuration)
                && matchBean(configuration)
                && matchAttribute(configuration)
                && !(excludeMatchDomain(configuration)
                        || excludeMatchClassName(configuration)
                        || excludeMatchBean(configuration)
                        || excludeMatchAttribute(configuration));
    }

    private boolean excludeMatchAttribute(Configuration configuration) {
        Filter exclude = configuration.getExclude();
        if (exclude.getAttribute() == null) {
            return false;
        } else if ((exclude.getAttribute() instanceof Map<?, ?>)
                && ((Map<String, Object>) (exclude.getAttribute()))
                        .containsKey(getAttributeName())) {
            return true;

        } else if ((exclude.getAttribute() instanceof List<?>
                && ((List<String>) (exclude.getAttribute())).contains(getAttributeName()))) {
            return true;
        }
        return false;
    }

    private boolean matchAttribute(Configuration configuration) {
        Filter include = configuration.getInclude();
        if (include.getAttribute() == null) {
            return true;

        } else if ((include.getAttribute() instanceof Map<?, ?>)
                && ((Map<String, Object>) (include.getAttribute()))
                        .containsKey(getAttributeName())) {
            return true;

        } else if ((include.getAttribute() instanceof List<?>
                && ((List<String>) (include.getAttribute())).contains(getAttributeName()))) {
            return true;
        }

        return false;
    }

    private Object getValue()
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException, NumberFormatException {
        return this.getJmxValue();
    }
}
