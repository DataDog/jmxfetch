package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;

public abstract class JMXAttribute {

    private static final List<String> EXCLUDED_BEAN_PARAMS = Arrays.asList("domain", "bean_name", "bean", "attribute");
    private static final String FIRST_CAP_PATTERN = "(.)([A-Z][a-z]+)";
    private static final String ALL_CAP_PATTERN = "([a-z0-9])([A-Z])";
    private static final String METRIC_REPLACEMENT = "([^a-zA-Z0-9_.]+)|(^[^a-zA-Z]+)";
    private static final String DOT_UNDERSCORE = "_*\\._*";
    private MBeanAttributeInfo attribute;
    private Connection connection;
    private ObjectInstance jmxInstance;
    private String domain;
    private String beanName;
    private String attributeName;
    private LinkedHashMap<Object, Object> valueConversions;
    protected String[] tags;
    private Configuration matchingConf;
    private LinkedList<String> defaultTags;

    JMXAttribute(MBeanAttributeInfo attribute, ObjectInstance jmxInstance, String instanceName,
            Connection connection, HashMap<String, String> instanceTags) {
        this.attribute = attribute;
        this.jmxInstance = jmxInstance;
        this.matchingConf = null;
        this.connection = connection;

        this.beanName = jmxInstance.getObjectName().toString();
        // A bean name is formatted like that: org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
        // i.e. : domain:bean_parameter1,bean_parameter2
        String[] split = this.beanName.split(":");
        this.domain = split[0];
        this.attributeName = attribute.getName();

        LinkedList<String> beanTags = new LinkedList<String>(Arrays.asList(new String(split[1]).replace("=", ":").split(",")));
        beanTags.add("instance:" + instanceName);
        beanTags.add("jmx_domain:" + domain);
        if (instanceTags != null) {
            for (Map.Entry<String, String> tag : instanceTags.entrySet()) {
                beanTags.add(tag.getKey() + ":" + tag.getValue());
            }
        }
        this.defaultTags = beanTags;

    }

    static String convertMetricName(String metricName) {
        metricName = metricName.replaceAll(FIRST_CAP_PATTERN, "$1_$2");
        metricName = metricName.replaceAll(ALL_CAP_PATTERN, "$1_$2").toLowerCase();
        metricName = metricName.replaceAll(METRIC_REPLACEMENT, "_");
        metricName = metricName.replaceAll(DOT_UNDERSCORE, ".").trim();
        return metricName;
    }

    @Override
    public String toString() {
        return "Bean name: " + beanName +
                " - Attribute name: " + attributeName +
                "  - Attribute type: " + attribute.getType();
    }

    public abstract LinkedList<HashMap<String, Object>> getMetrics() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException;

    /**
     * An abstract function implemented in the inherited classes JMXSimpleAttribute and JMXComplexAttribute
     *
     * @param conf Configuration a Configuration object that will be used to check if the JMX Attribute match this configuration
     * @return a boolean that tells if the attribute matches the configuration or not
     */
    public abstract boolean match(Configuration conf);

    public int getMetricsCount() {
        try {
            return this.getMetrics().size();
        } catch (Exception e) {
            return 0;
        }
    }

    Object getJmxValue() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return this.connection.getAttribute(this.jmxInstance.getObjectName(), this.attribute.getName());
    }

    boolean matchDomain(Configuration conf) {
        LinkedHashMap<String, Object> include = conf.getInclude();
        return include.get("domain") == null || include.get("domain").equals(domain);
    }

    boolean excludeMatchDomain(Configuration conf) {
        LinkedHashMap<String, Object> exclude = conf.getExclude();
        return exclude.get("domain") != null && exclude.get("domain").equals(domain);
    }

    boolean excludeMatchBean(Configuration conf) {
        LinkedHashMap<String, Object> exclude = conf.getExclude();
        String bean = (String) exclude.get("bean");
        String confBeanName = (String) exclude.get("bean_name");

        if (beanName.equals(bean) || beanName.equals(confBeanName)) {
            return true;
        }

        for (String bean_attr : exclude.keySet()) {
            if (EXCLUDED_BEAN_PARAMS.contains(bean_attr)) {
                continue;
            }

            HashMap<String, String> beanParams = new HashMap<String, String>();
            for (String param : this.tags) {
                String[] paramSplit = param.split(":");
                beanParams.put(new String(paramSplit[0]), new String(paramSplit[1]));
            }

            if (exclude.get(bean_attr).equals(beanParams.get(bean_attr))) {
                return true;
            }
        }
        return false;
    }

    Object convertMetricValue(Object metricValue) {
        Object converted = metricValue;

        if (!getValueConversions().isEmpty()) {
            converted = getValueConversions().get(metricValue);
            if (converted == null && getValueConversions().get("default") != null) {
                converted = getValueConversions().get("default");
            }
        }

        return converted;
    }

    double getValueAsDouble(Object metricValue) {
        Object value = convertMetricValue(metricValue);

        if (value instanceof String) {
            return Double.parseDouble((String) value);
        } else if (value instanceof Integer) {
            return new Double((Integer) (value));
        } else if (value instanceof AtomicInteger) {
            return new Double(((AtomicInteger) (value)).get());
        } else if (value instanceof AtomicLong) {
            Long l = ((AtomicLong) (value)).get();
            return l.doubleValue();
        } else if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Boolean) {
            return ((Boolean) value ? 1.0 : 0.0);
        } else if (value instanceof Long) {
            Long l = new Long((Long) value);
            return l.doubleValue();
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            try {
                return new Double((Double) value);
            } catch (Exception e) {
                throw new NumberFormatException();
            }
        }
    }

    boolean matchBean(Configuration configuration) {
        boolean matchBeanName = false;
        LinkedHashMap<String, Object> include = configuration.getInclude();
        if (include.get("bean") == null && include.get("bean_name") == null) {
            matchBeanName = true;
        } else if (include.get("bean") != null) {
            matchBeanName = include.get("bean").equals(beanName);
        } else if (include.get("bean_name") != null) {
            matchBeanName = include.get("bean_name").equals(beanName);
        }

        if (!matchBeanName) {
            return false;
        }

        for (String bean_attr : include.keySet()) {
            if (EXCLUDED_BEAN_PARAMS.contains(bean_attr)) {
                continue;
            }

            HashMap<String, String> beanParams = new HashMap<String, String>();
            for (String param : this.defaultTags) {
                String[] paramSplit = param.split(":");
                beanParams.put(new String(paramSplit[0]), new String(paramSplit[1]));
            }

            if (beanParams.get(bean_attr) == null || !beanParams.get(bean_attr).equals(include.get(bean_attr))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    HashMap<Object, Object> getValueConversions() {
        if (valueConversions == null) {
            Object includedAttribute = matchingConf.getInclude().get("attribute");
            if (includedAttribute instanceof LinkedHashMap<?, ?>) {
                String attributeName = this.attribute.getName();
                LinkedHashMap<String, LinkedHashMap<Object, Object>> attribute =
                        ((LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Object, Object>>>) includedAttribute).get(attributeName);

                if (attribute != null) {
                    valueConversions = attribute.get("values");
                }
            }
            if (valueConversions == null) {
                valueConversions = new LinkedHashMap<Object, Object>();
            }
        }

        return valueConversions;
    }

    public Configuration getMatchingConf() {
        return matchingConf;
    }

    public void setMatchingConf(Configuration matchingConf) {
        this.matchingConf = matchingConf;
    }

    MBeanAttributeInfo getAttribute() {
        return attribute;
    }

    @SuppressWarnings("unchecked")
    protected String[] getTags() {
        if(tags != null) {
            return tags;
        }

        LinkedHashMap<String, Object> include = matchingConf.getInclude();
        if (include != null) {
            if (include.get("attribute") instanceof LinkedHashMap<?, ?>) {
                LinkedHashMap<String, ArrayList<String>> attributeParams = ((LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>>)(include.get("attribute"))).get(attributeName);
                if (attributeParams != null) {
                    ArrayList<String> yamlTags = attributeParams.get("tags");
                    if ( yamlTags != null) {
                        defaultTags.addAll(yamlTags);
                    }
                }
            }
        }
        tags = new String[defaultTags.size()];
        tags = defaultTags.toArray(tags);
        return tags;
    }

    String getBeanName() {
        return beanName;
    }

    String getAttributeName() {
        return attributeName;
    }
}
