package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;

public abstract class JMXAttribute {

    protected MBeanAttributeInfo attribute;
    protected Connection connection;
    protected ObjectInstance jmxInstance;
    protected double value;
    protected String domain;
    protected String beanName;
    protected String attributeName;
    protected String[] tags;
    protected Configuration matching_conf;
    protected static final List<String> SIMPLE_TYPES = Arrays.asList("long", "java.lang.String", "int", "double"); 
    protected static final List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData");
    private static final String[] EXCLUDED_BEAN_PARAMS = {"domain", "bean_name", "bean", "attribute"};
    
    private static final String FIRST_CAP_PATTERN = "(.)([A-Z][a-z]+)";
    private static final String ALL_CAP_PATTERN = "([a-z0-9])([A-Z])";
    private static final String METRIC_REPLACEMENT = "([^a-zA-Z0-9_.]+)|(^[^a-zA-Z]+)";
    private static final String DOT_UNDERSCORE = "_*\\._*";

    public JMXAttribute(MBeanAttributeInfo a, ObjectInstance jmxInstance, String instanceName, Connection connection) {
        this.attribute = a;
        this.jmxInstance = jmxInstance;
        this.matching_conf = null;
        this.connection = connection;
        
        this.beanName = jmxInstance.getObjectName().toString();
        // A bean name is formatted like that: org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
        // i.e. : domain:bean_parameter1,bean_parameter2
        String[] split = this.beanName.split(":");
        this.domain = split[0];
        this.attributeName = a.getName();
        
        
        // We add the instance name as a tag. We need to convert the Array of strings to List in order to do that
        LinkedList<String> beanTags = new LinkedList<String>(Arrays.asList(split[1].replace("=",":").split(",")));
        beanTags.add("instance:"+instanceName);
        beanTags.add("jmx_domain:"+domain);
        this.tags = new String[beanTags.size()];
        beanTags.toArray(this.tags);
        
    }

    @Override
    public String toString() {
        return this.beanName + " - " + this.attributeName;
    }

    public abstract LinkedList<HashMap<String, Object>> getMetrics() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException;

    /**
     * An abstract function implemented in the inherited classes JMXSimpleAttribute and JMXComplexAttribute 
     * 
     * @param Configuration , a Configuration object that will be used to check if the JMX Attribute match this configuration
     * @return a boolean that tells if the attribute matches the configuration or not
     */
    public abstract boolean match(Configuration conf);


    protected Object getJmxValue() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return this.connection.getAttribute(this.jmxInstance.getObjectName(), this.attribute.getName());
    }

    protected boolean matchDomain(Configuration conf) {
        return conf.include.get("domain") == null || ((String)(conf.include.get("domain"))).equals(this.domain);
    }

    protected boolean excludeMatchDomain(Configuration conf) {
        return conf.exclude.get("domain") != null && ((String)(conf.exclude.get("domain"))).equals(this.domain);
    }

    protected boolean excludeMatchBean(Configuration conf) {
        String bean = (String) conf.exclude.get("bean");
        String confBeanName = (String) conf.exclude.get("bean_name");

        if (this.beanName.equals(bean) || this.beanName.equals(confBeanName)) {
            return true;
        }

        for (String bean_attr: conf.exclude.keySet()) { 
            if (Arrays.asList(EXCLUDED_BEAN_PARAMS).contains(bean_attr)) {
                continue;
            }

            HashMap<String, String> beanParams = new HashMap<String, String>();
            for (String param : this.tags) {
                String[] paramSplit = param.split(":");
                beanParams.put(paramSplit[0], paramSplit[1]);
            }

            if(conf.exclude.get(bean_attr).equals(beanParams.get(bean_attr))) {
                return true;
            }
        }
        return false;
    }

    protected static String convertMetricName(String metricName) {
        metricName = metricName.replaceAll(FIRST_CAP_PATTERN, "$1_$2");
        metricName = metricName.replaceAll(ALL_CAP_PATTERN, "$1_$2").toLowerCase();
        metricName = metricName.replaceAll(METRIC_REPLACEMENT, "_");
        metricName = metricName.replaceAll(DOT_UNDERSCORE, ".").trim();
        return metricName;
    }
    
    protected double _getValueAsDouble(Object value) {
        if (value instanceof String) {
            return Double.parseDouble((String)value);
            
        } else if (value instanceof Integer) {
            return new Double((Integer)(value));
            
        } else if (value instanceof Double) {
            return (Double)value;
        } else if (value instanceof Long) {
            Long l = new Long((Long) value);
            return l.doubleValue();
        } else {
            throw new NumberFormatException();
        }
        
    }

    protected boolean matchBean(Configuration configuration) {
        
        boolean matchBeanName = false;
        if (configuration.include.get("bean") == null && configuration.include.get("bean_name") == null) {
            matchBeanName = true;
        } else if (configuration.include.get("bean") != null) {
            matchBeanName = ((String)(configuration.include.get("bean"))).equals(this.beanName);
        } else if (configuration.include.get("bean_name") != null) {
            matchBeanName = ((String)(configuration.include.get("bean_name"))).equals(this.beanName);
        }

        if (!matchBeanName) {
            return false;
        }

        for (String bean_attr: configuration.include.keySet()) {    
            if (Arrays.asList(EXCLUDED_BEAN_PARAMS).contains(bean_attr)) {
                continue;
            }

            HashMap<String, String> beanParams = new HashMap<String, String>();
            for (String param : this.tags) {
                String[] paramSplit = param.split(":");
                beanParams.put(paramSplit[0], paramSplit[1]);
            }

            if (beanParams.get(bean_attr) == null 
                    || !((String)beanParams.get(bean_attr)).equals((String)configuration.include.get(bean_attr))) {
                return false;
            }
        }
        return true;
    }

}
