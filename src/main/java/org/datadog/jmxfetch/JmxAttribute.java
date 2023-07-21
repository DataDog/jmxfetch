package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.service.ServiceNameProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

@Slf4j
public abstract class JmxAttribute {

    protected static final String ALIAS = "alias";
    protected static final String METRIC_TYPE = "metric_type";
    private static final List<String> EXCLUDED_BEAN_PARAMS =
            Arrays.asList(
                    "domain",
                    "domain_regex",
                    "bean_name",
                    "bean",
                    "bean_regex",
                    "class",
                    "class_regex",
                    "attribute",
                    "exclude_tags",
                    "tags");
    private static final String FIRST_CAP_PATTERN = "(.)([A-Z][a-z]+)";
    private static final String ALL_CAP_PATTERN = "([a-z0-9])([A-Z])";
    private static final String METRIC_REPLACEMENT = "([^a-zA-Z0-9_.]+)|(^[^a-zA-Z]+)";
    private static final String DOT_UNDERSCORE = "_*\\._*";
    protected static final String CASSANDRA_DOMAIN = "org.apache.cassandra.metrics";

    private MBeanAttributeInfo attribute;
    private Connection connection;
    private ObjectName beanName;
    private String domain;
    private String className;
    private String beanStringName;
    private ServiceNameProvider serviceNameProvider;
    private Map<String, String> beanParameters;
    private String attributeName;
    private Map<String, Map<Object, Object>> valueConversions =
            new HashMap<String, Map<Object, Object>>();
    protected String[] tags;
    private Configuration matchingConf;
    private List<String> defaultTagsList;
    private boolean cassandraAliasing;
    protected String checkName;
    private boolean normalizeBeanParamTags;

    JmxAttribute(
            MBeanAttributeInfo attribute,
            ObjectName beanName,
            String className,
            String instanceName,
            String checkName,
            Connection connection,
            ServiceNameProvider serviceNameProvider,
            Map<String, String> instanceTags,
            boolean cassandraAliasing,
            boolean emptyDefaultHostname,
            boolean normalizeBeanParamTags) {
        this.attribute = attribute;
        this.beanName = beanName;
        this.className = className;
        this.matchingConf = null;
        this.connection = connection;
        this.attributeName = attribute.getName();
        this.beanStringName = beanName.toString();
        this.cassandraAliasing = cassandraAliasing;
        this.checkName = checkName;
        this.serviceNameProvider = serviceNameProvider;
        this.normalizeBeanParamTags = normalizeBeanParamTags;

        // A bean name is formatted like that:
        // org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
        // i.e. : domain:bean_parameter1,bean_parameter2
        // Note: some beans have a ':' in the name. Example:
        // some.domain:name="some.bean.0.0.0.0:80.some-metric"
        int splitPosition = beanStringName.indexOf(':');
        String domain = beanStringName.substring(0, splitPosition);
        String beanParameters = beanStringName.substring(splitPosition + 1);
        this.domain = domain;

        Map<String, String> beanParametersHash = getBeanParametersHash(beanParameters);
        List<String> beanParametersList =
                getBeanParametersList(instanceName, beanParametersHash, instanceTags);

        this.beanParameters = beanParametersHash;
        this.defaultTagsList = sanitizeParameters(beanParametersList);
        if (emptyDefaultHostname) {
            this.defaultTagsList.add("host:");
        }
    }

    /** Remove tags listed in the 'exclude_tags' list from configuration. */
    private void applyTagsBlackList() {
        Filter include = this.matchingConf.getInclude();
        if (include != null) {

            for (String excludedTagName : include.getExcludeTags()) {
                for (Iterator<String> it = this.defaultTagsList.iterator(); it.hasNext(); ) {
                    String tag = it.next();
                    if (tag.startsWith(excludedTagName + ":")) {
                        it.remove();
                    }
                }
            }
        }
    }

    /** Add alias tag from the 'tag_alias' configuration list. */
    private void addAdditionalTags() {
        Filter include = this.matchingConf.getInclude();
        if (include != null) {
            for (Map.Entry<String, String> tag : include.getAdditionalTags().entrySet()) {
                String alias = this.replaceByAlias(tag.getValue());
                if (alias != null && alias.trim().length() > 0) {
                    this.defaultTagsList.add(tag.getKey() + ":" + alias);
                } else {
                    log.warn("Unable to apply tag " + tag.getKey() + " - with unknown alias");
                }
            }
        }
    }

    private void addServiceTags() {
        Iterable<String> serviceNames = this.serviceNameProvider.getServiceNames();
        if (serviceNames != null) {
            for (String serviceName : serviceNames) {
                this.defaultTagsList.add("service:" + serviceName);
            }
        }
    }

    /** Gets bean parameter hash map. */
    public static Map<String, String> getBeanParametersHash(String beanParametersString) {
        String[] beanParameters = beanParametersString.split(",");
        Map<String, String> beanParamsMap = new HashMap<String, String>(beanParameters.length);
        for (String param : beanParameters) {
            String[] paramSplit = param.split("=");
            if (paramSplit.length > 1) {
                beanParamsMap.put(new String(paramSplit[0]), new String(paramSplit[1]));
            } else {
                beanParamsMap.put(new String(paramSplit[0]), "");
            }
        }

        return beanParamsMap;
    }

    private List<String> getBeanParametersList(
            String instanceName,
            Map<String, String> beanParameters,
            Map<String, String> instanceTags) {
        List<String> beanTags = new ArrayList<String>();
        beanTags.add("instance:" + instanceName);
        beanTags.add("jmx_domain:" + domain);

        if (renameCassandraMetrics()) {
            beanTags.addAll(getCassandraBeanTags(beanParameters));
        } else {
            for (Map.Entry<String, String> param : beanParameters.entrySet()) {
                beanTags.add(param.getKey() + ":" + param.getValue());
            }
        }

        if (instanceTags != null) {
            for (Map.Entry<String, String> tag : instanceTags.entrySet()) {
                if (tag.getValue() != null) {
                    beanTags.add(tag.getKey() + ":" + tag.getValue());
                } else {
                    beanTags.add(tag.getKey());
                }
            }
        }

        return beanTags;
    }

    /**
     * Sanitize MBean parameter names and values, i.e. - Rename parameter names conflicting with
     * existing tags - Remove illegal characters
     */
    private List<String> sanitizeParameters(List<String> beanParametersList) {
        List<String> defaultTagsList = new ArrayList<String>(beanParametersList.size());
        for (String rawBeanParameter : beanParametersList) {
            // Remove `|` characters
            String beanParameter = rawBeanParameter.replace("|", "");

            // Remove `"` characters
            if(normalizeBeanParamTags == true) {
                beanParameter = beanParameter.replace("\"","");
            }

            // 'host' parameter is renamed to 'bean_host'
            if (beanParameter.startsWith("host:")) {
                defaultTagsList.add("bean_host:" + beanParameter.substring("host:".length()));
            } else if (beanParameter.endsWith(":")) {
                // If the parameter's value is empty, remove the colon in the tag
                defaultTagsList.add(beanParameter.substring(0, beanParameter.length() - 1));
            } else {
                defaultTagsList.add(beanParameter);
            }
        }

        return defaultTagsList;
    }

    protected Boolean renameCassandraMetrics() {
        return cassandraAliasing && domain.equals(CASSANDRA_DOMAIN);
    }

    private static Collection<String> getCassandraBeanTags(Map<String, String> beanParameters) {
        Collection<String> tags = new ArrayList<String>();
        for (Map.Entry<String, String> param : beanParameters.entrySet()) {
            if (param.getKey().equals("name")) {
                // This is already in the alias
                continue;
            } else if (param.getKey().equals("scope")) {
                String type = beanParameters.get("type");
                tags.add(type + ":" + param.getValue());
            } else {
                tags.add(param.getKey() + ":" + param.getValue());
            }
        }
        return tags;
    }

    static String convertMetricName(String metricName) {
        metricName = metricName.replaceAll(FIRST_CAP_PATTERN, "$1_$2");
        metricName = metricName.replaceAll(ALL_CAP_PATTERN, "$1_$2").toLowerCase();
        metricName = metricName.replaceAll(METRIC_REPLACEMENT, "_");
        metricName = metricName.replaceAll(DOT_UNDERSCORE, ".").trim();
        return metricName;
    }

    /** Returns string representation of JMX Attribute. */
    @Override
    public String toString() {
        return "Bean name: "
                + beanStringName
                + " - Attribute name: "
                + attributeName
                + "  - Attribute type: "
                + attribute.getType();
    }

    public abstract List<Metric> getMetrics()
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException;

    /**
     * An abstract function implemented in the inherited classes JmxSimpleAttribute and
     * JmxComplexAttribute.
     *
     * @param conf Configuration a Configuration object that will be used to check if the Jmx
     *     Attribute match this configuration
     * @return a boolean that tells if the attribute matches the configuration or not
     */
    public abstract boolean match(Configuration conf);

    /** Gets the metric count for the attribute. */
    public int getMetricsCount() {
        try {
            return this.getMetrics().size();
        } catch (Exception e) {
            log.warn("Unable to get metrics from " + beanStringName + " - "
                                                + attributeName + ": " + e.toString());
            return 0;
        }
    }

    /** Gets the JMX Attribute info value. Makes a call through the connection */
    Object getJmxValue()
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {
        return this.connection.getAttribute(this.beanName, this.attribute.getName());
    }

    boolean matchDomain(Configuration conf) {
        return includeMatchName(domain,
                conf.getInclude().getDomain(),
                conf.getInclude().getDomainRegex());
    }

    boolean excludeMatchDomain(Configuration conf) {
        return excludeMatchName(domain,
                conf.getExclude().getDomain(),
                conf.getExclude().getDomainRegex());
    }


    boolean matchClassName(Configuration conf) {
        return includeMatchName(className,
                conf.getInclude().getClassName(),
                conf.getInclude().getClassNameRegex());
    }


    boolean excludeMatchClassName(Configuration conf) {
        return excludeMatchName(className,
                conf.getExclude().getClassName(),
                conf.getExclude().getClassNameRegex());
    }

    private boolean includeMatchName(String name, String includeName, Pattern includeNameRegex) {
        return (includeName == null || includeName.equals(name))
                && (includeNameRegex == null || includeNameRegex.matcher(name).matches());
    }

    private boolean excludeMatchName(String name, String excludeName, Pattern excludeNameRegex) {
        return (excludeName != null && excludeName.equals(name))
                || (excludeNameRegex != null && excludeNameRegex.matcher(name).matches());
    }

    Object convertMetricValue(Object metricValue, String field) {
        Object converted = metricValue;
        Map<Object, Object> valueConversions = getValueConversions(field);
        if (valueConversions == null || valueConversions.isEmpty()) {
            return converted;
        }
        converted = valueConversions.get(metricValue);
        if (converted != null) {
            return converted;
        }
        return valueConversions.get("default");
    }

    double castToDouble(Object metricValue, String field) {
        Object value = convertMetricValue(metricValue, field);

        if (value instanceof String) {
            return Double.parseDouble((String) value);
        } else if (value instanceof Integer) {
            return new Double((Integer) (value));
        } else if (value instanceof AtomicInteger) {
            return new Double(((AtomicInteger) (value)).get());
        } else if (value instanceof AtomicLong) {
            Long longValue = ((AtomicLong) (value)).get();
            return longValue.doubleValue();
        } else if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Boolean) {
            return ((Boolean) value ? 1.0 : 0.0);
        } else if (value instanceof Long) {
            Long longValue = new Long((Long) value);
            return longValue.doubleValue();
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

    private boolean matchBeanRegex(Filter filter, boolean matchIfNoRegex) {
        List<Pattern> beanRegexes = filter.getBeanRegexes();
        if (beanRegexes.isEmpty()) {
            return matchIfNoRegex;
        }

        for (Pattern beanRegex : beanRegexes) {
            Matcher matcher = beanRegex.matcher(beanStringName);

            if (matcher.matches()) {
                for (int i = 0; i <= matcher.groupCount(); i++) {
                    this.beanParameters.put(Integer.toString(i), matcher.group(i));
                }
                return true;
            }
        }
        return false;
    }

    private boolean matchBeanName(Configuration configuration) {
        Filter include = configuration.getInclude();

        if (!include.isEmptyBeanName() && !include.getBeanNames().contains(beanStringName)) {
            return false;
        }

        for (String beanAttr : include.keySet()) {
            if (EXCLUDED_BEAN_PARAMS.contains(beanAttr)) {
                continue;
            }

            List<String> beanValues = include.getParameterValues(beanAttr);

            if (beanParameters.get(beanAttr) == null
                    || !(beanValues.contains(beanParameters.get(beanAttr)))) {
                return false;
            }
        }
        return true;
    }

    private boolean excludeMatchBeanName(Configuration conf) {
        Filter exclude = conf.getExclude();
        List<String> beanNames = exclude.getBeanNames();

        if (beanNames.contains(beanStringName)) {
            return true;
        }

        for (String beanAttr : exclude.keySet()) {
            if (EXCLUDED_BEAN_PARAMS.contains(beanAttr)) {
                continue;
            }

            if (beanParameters.get(beanAttr) == null) {
                continue;
            }

            List<String> beanValues = exclude.getParameterValues(beanAttr);
            for (String beanVal : beanValues) {
                if (beanParameters.get(beanAttr).equals(beanVal)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean matchBean(Configuration configuration) {
        return matchBeanName(configuration) && matchBeanRegex(configuration.getInclude(), true);
    }

    boolean excludeMatchBean(Configuration configuration) {
        return excludeMatchBeanName(configuration)
                || matchBeanRegex(configuration.getExclude(), false);
    }

    @SuppressWarnings("unchecked")
    Map<Object, Object> getValueConversions(String field) {
        String fullAttributeName =
                (field != null)
                        ? (getAttribute().getName() + "." + field)
                        : (getAttribute().getName());
        if (valueConversions.get(fullAttributeName) == null) {
            Object includedAttribute = matchingConf.getInclude().getAttribute();
            if (includedAttribute instanceof Map<?, ?>) {
                Map<String, Map<Object, Object>> attribute =
                        ((Map<String, Map<String, Map<Object, Object>>>) includedAttribute)
                                .get(fullAttributeName);

                if (attribute != null) {
                    valueConversions.put(fullAttributeName, attribute.get("values"));
                }
            }
            if (valueConversions.get(fullAttributeName) == null) {
                valueConversions.put(fullAttributeName, new HashMap<Object, Object>());
            }
        }

        return valueConversions.get(fullAttributeName);
    }

    /** Gets the matching configuration for the attribute. */
    public Configuration getMatchingConf() {
        return matchingConf;
    }

    /** Sets a matching configuration for the attribute. */
    public void setMatchingConf(Configuration matchingConf) {
        this.matchingConf = matchingConf;

        // Now that we have the matchingConf we can:
        // - add additional tags
        this.addAdditionalTags();
        // - filter out excluded tags
        this.applyTagsBlackList();
        // Add the service tag(s) - comes last because if the service tag is blacklisted as
        // a JmxAttribute tag, we still want to include the service specified in the config.
        this.addServiceTags();
    }

    MBeanAttributeInfo getAttribute() {
        return attribute;
    }

    public ObjectName getBeanName() {
        return beanName;
    }

    /**
     * Get attribute alias.
     *
     * <p>In order, tries to: * Use `alias_match` to generate an alias with a regular expression *
     * Use `alias` directly * Create an generic alias prefixed with user's `metric_prefix`
     * preference or default to `jmx`
     *
     * <p>Argument(s): * (Optional) `field` `Null` for `JmxSimpleAttribute`.
     */
    protected String getAlias(String field) {
        String alias = null;
        Filter include = getMatchingConf().getInclude();
        Map<String, Object> conf = getMatchingConf().getConf();
        String fullAttributeName =
                (field != null)
                        ? (getAttribute().getName() + "." + field)
                        : (getAttribute().getName());
        if (include.getAttribute() instanceof Map<?, ?>) {
            Map<String, Map<String, String>> attribute =
                    (Map<String, Map<String, String>>) (include.getAttribute());
            alias = getUserAlias(attribute, fullAttributeName);
        }
        if (alias == null) {
            if (conf.get("metric_prefix") != null) {
                alias = conf.get("metric_prefix") + "." + getDomain() + "." + fullAttributeName;
            } else if (getDomain().startsWith("org.apache.cassandra")) {
                alias = getCassandraAlias();
            }
        }
        // If still null - generate generic alias
        if (alias == null) {
            alias = "jmx." + getDomain() + "." + fullAttributeName;
        }
        alias = convertMetricName(alias);
        return alias;
    }

    /**
     * Metric name aliasing specific to Cassandra.
     *
     * <p>* (Default) `cassandra_aliasing` == False. Legacy aliasing: drop `org.apache` prefix. *
     * `cassandra_aliasing` == True Comply with CASSANDRA-4009
     *
     * <p>More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
     */
    private String getCassandraAlias() {
        if (renameCassandraMetrics()) {
            Map<String, String> beanParameters = getBeanParameters();
            String metricName = beanParameters.get("name");
            String attributeName = getAttributeName();
            if (attributeName.equals("Value")) {
                return "cassandra." + metricName;
            }
            return "cassandra." + metricName + "." + attributeName;
        }
        // Deprecated Cassandra metric.  Remove domain prefix.
        return getDomain().replace("org.apache.", "") + "." + getAttributeName();
    }

    /**
     * Retrieve user defined alias. Substitute regular expression named groups.
     *
     * <p>Example: ``` bean: org.datadog.jmxfetch.test:foo=Bar,qux=Baz attribute: toto: alias:
     * my.metric.$foo.$attribute ``` returns a metric name `my.metric.bar.toto`
     */
    private String getUserAlias(Map<String, Map<String, String>> attribute,
            String fullAttributeName) {
        String alias = attribute.get(fullAttributeName).get(ALIAS);
        if (alias == null) {
            return null;
        }

        alias = this.replaceByAlias(alias);

        // Attribute & domain
        alias = alias.replace("$attribute", fullAttributeName);
        alias = alias.replace("$domain", domain);
        if (alias.contains("$value")) {
            // getJmxValue() hits the JMX target (potentially through remote network connection),
            // so only call it if needed.
            try {
                alias = alias.replace("$value", getJmxValue().toString());
            } catch (JMException e) {
                // If we haven't been able to get the value, it wasn't replaced.
                log.warn("Unable to replace $value for attribute " + fullAttributeName, e);
            } catch (IOException e) {
                // Same as above
                log.warn("Unable to replace $value for attribute " + fullAttributeName, e);
            }
        }

        return alias;
    }

    private String replaceByAlias(String alias) {
        // Bean parameters
        for (Map.Entry<String, String> param : beanParameters.entrySet()) {
            alias = alias.replace("$" + param.getKey(), param.getValue());
        }
        return alias;
    }

    @SuppressWarnings("unchecked")
    protected String[] getTags() {
        if (tags != null) {
            return tags;
        }

        Filter include = matchingConf.getInclude();
        if (include != null) {
            Object includeAttribute = include.getAttribute();
            if (includeAttribute instanceof Map<?, ?>) {
                Map<String, List<String>> attributeParams =
                        ((Map<String, Map<String, List<String>>>)
                                        includeAttribute)
                                .get(attributeName);
                if (attributeParams != null) {
                    List<String> yamlTags = attributeParams.get("tags");
                    if (yamlTags != null) {
                        defaultTagsList.addAll(yamlTags);
                    }
                }
            }
        }
        tags = new String[defaultTagsList.size()];
        tags = defaultTagsList.toArray(tags);
        return tags;
    }

    String getBeanStringName() {
        return beanStringName;
    }

    String getAttributeName() {
        return attributeName;
    }

    public static List<String> getExcludedBeanParams() {
        return EXCLUDED_BEAN_PARAMS;
    }

    protected String getDomain() {
        return domain;
    }

    protected Map<String, String> getBeanParameters() {
        return beanParameters;
    }

    protected String getMetricType(String subAttribute) {
        String localMetricType = null;
        String name = subAttribute != null
                ? getAttribute().getName() + "." + subAttribute
                : attributeName;
        Filter include = getMatchingConf().getInclude();
        if (include.getAttribute() instanceof Map<?, ?>) {
            Map<String, Map<String, String>> attribute =
                    (Map<String, Map<String, String>>) (include.getAttribute());
            Map<String, String> attrInfo = attribute.get(name);
            localMetricType = attrInfo.get(METRIC_TYPE);
            if (localMetricType == null) {
                localMetricType = attrInfo.get("type");
            }
        }
        if (localMetricType == null) {
            localMetricType = "gauge";
        }
        return localMetricType;
    }
}
