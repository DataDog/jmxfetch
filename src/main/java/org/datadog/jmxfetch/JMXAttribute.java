package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.log4j.Logger;

public abstract class JMXAttribute {

    protected static final String ALIAS = "alias";
    protected static final String METRIC_TYPE = "metric_type";
    protected final static Logger LOGGER = Logger.getLogger(JMXAttribute.class.getName());
    private static final List<String> EXCLUDED_BEAN_PARAMS = Arrays.asList("domain", "domain_regex", "bean_name", "bean",
                                                                           "bean_regex", "attribute", "exclude_tags", "tags");
    private static final String FIRST_CAP_PATTERN = "(.)([A-Z][a-z]+)";
    private static final String ALL_CAP_PATTERN = "([a-z0-9])([A-Z])";
    private static final String METRIC_REPLACEMENT = "([^a-zA-Z0-9_.]+)|(^[^a-zA-Z]+)";
    private static final String DOT_UNDERSCORE = "_*\\._*";
    protected static final String CASSANDRA_DOMAIN = "org.apache.cassandra.metrics";

    private MBeanAttributeInfo attribute;
    private Connection connection;
    private ObjectName beanName;
    private String domain;
    private String beanStringName;
    private HashMap<String, String> beanParameters;
    private String attributeName;
    private LinkedHashMap<String, LinkedHashMap<Object, Object>> valueConversions = new LinkedHashMap<String, LinkedHashMap<Object, Object>>();
    protected String[] tags;
    private Configuration matchingConf;
    private LinkedList<String> defaultTagsList;
    private Boolean cassandraAliasing;

    JMXAttribute(MBeanAttributeInfo attribute, ObjectName beanName, String instanceName,
            Connection connection, HashMap<String, String> instanceTags, Boolean cassandraAliasing,
            boolean emptyDefaultHostname) {
        this.attribute = attribute;
        this.beanName = beanName;
        this.matchingConf = null;
        this.connection = connection;
        this.attributeName = attribute.getName();
        this.beanStringName = beanName.toString();
        this.cassandraAliasing = cassandraAliasing;

        // A bean name is formatted like that: org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
        // i.e. : domain:bean_parameter1,bean_parameter2
        //Note: some beans have a ':' in the name. Example:  some.domain:name="some.bean.0.0.0.0:80.some-metric"
        int splitPosition = beanStringName.indexOf(':');
       	String domain = beanStringName.substring(0, splitPosition);
       	String beanParameters = beanStringName.substring(splitPosition+1);
        this.domain = domain;

        HashMap<String, String> beanParametersHash = getBeanParametersHash(beanParameters);
        LinkedList<String> beanParametersList = getBeanParametersList(instanceName, beanParametersHash, instanceTags);

        this.beanParameters = beanParametersHash;
        this.defaultTagsList = sanitizeParameters(beanParametersList);
        if (emptyDefaultHostname) {
            this.defaultTagsList.add("host:");
        }
    }

    /**
     * Remove tags listed in the 'exclude_tags' list from configuration.
     */
    private void applyTagsBlackList() {
        Filter include = this.matchingConf.getInclude();
        if (include != null) {

            for (String excludedTagName : include.getExcludeTags()) {
                for (Iterator<String> it = this.defaultTagsList.iterator(); it.hasNext();) {
                    if (it.next().startsWith(excludedTagName + ":")) {
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Add alias tag from the 'tag_alias' configuration list
     */
    private void addAdditionalTags() {
        Filter include = this.matchingConf.getInclude();
        if (include != null) {
            for (Map.Entry<String, String> tag : include.getAdditionalTags().entrySet()) {
            		String alias = this.replaceByAlias(tag.getValue());
            		if ((alias.trim().length() > 0) && alias != null) {
            			this.defaultTagsList.add(tag.getKey() + ":" + alias);
            		} else {
            			LOGGER.warn("Unable to apply tag " + tag.getKey() + " - with unknown alias");
            		}
            }
        }
    }

    public static HashMap<String, String> getBeanParametersHash(String beanParametersString) {
        String[] beanParameters = beanParametersString.split(",");
        HashMap<String, String> beanParamsMap = new HashMap<String, String>(beanParameters.length);
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

    private LinkedList<String> getBeanParametersList(String instanceName, Map<String, String> beanParameters, HashMap<String, String> instanceTags) {
        LinkedList<String> beanTags = new LinkedList<String>();
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
                }
                else {
                    beanTags.add(tag.getKey());
                }
            }
        }

        return beanTags;
    }

    /**
     * Sanitize MBean parameter names and values, i.e.
     * - Rename parameter names conflicting with existing tags
     * - Remove illegal characters
     */
    private static LinkedList<String> sanitizeParameters(LinkedList<String> beanParametersList) {
        LinkedList<String> defaultTagsList = new LinkedList<String>();
        for (String rawBeanParameter: beanParametersList) {
            // Remove `|` characters
            String beanParameter = rawBeanParameter.replace("|", "");

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

    protected Boolean renameCassandraMetrics(){
        return cassandraAliasing && domain.equals(CASSANDRA_DOMAIN);
    }

    private static Collection<String> getCassandraBeanTags(Map<String, String> beanParameters) {
        Collection<String> tags = new LinkedList<String>();
        for (Map.Entry<String, String> param : beanParameters.entrySet()) {
            if (param.getKey().equals("name")) {
                //This is already in the alias
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

    @Override
    public String toString() {
        return "Bean name: " + beanStringName +
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
            LOGGER.warn("Unable to get metrics from " + beanStringName + " - " + attributeName, e);
            return 0;
        }
    }

    Object getJmxValue() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return this.connection.getAttribute(this.beanName, this.attribute.getName());
    }

    boolean matchDomain(Configuration conf) {
        String includeDomain = conf.getInclude().getDomain();
        Pattern includeDomainRegex = conf.getInclude().getDomainRegex();

        return (includeDomain == null || includeDomain.equals(domain))
            && (includeDomainRegex == null || includeDomainRegex.matcher(domain).matches());
    }

    boolean excludeMatchDomain(Configuration conf) {
        String excludeDomain = conf.getExclude().getDomain();
        Pattern excludeDomainRegex = conf.getExclude().getDomainRegex();

        return excludeDomain != null  && excludeDomain.equals(domain)
            || excludeDomainRegex != null && excludeDomainRegex.matcher(domain).matches();
    }

    Object convertMetricValue(Object metricValue, String field) {
        Object converted = metricValue;

        if (!getValueConversions(field).isEmpty()) {
            converted = getValueConversions(field).get(metricValue);
            if (converted == null && getValueConversions(field).get("default") != null) {
                converted = getValueConversions(field).get("default");
            }
        }

        return converted;
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

    private boolean matchBeanRegex(Filter filter, boolean matchIfNoRegex) {
        ArrayList<Pattern> beanRegexes = filter.getBeanRegexes();
        if (beanRegexes.isEmpty()) {
            return matchIfNoRegex;
        }

        for (Pattern beanRegex : beanRegexes) {
            Matcher m = beanRegex.matcher(beanStringName);

            if(m.matches()) {
                for (int i = 0; i<= m.groupCount(); i++) {
            		this.beanParameters.put(Integer.toString(i), m.group(i));
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

        for (String bean_attr : include.keySet()) {
            if (EXCLUDED_BEAN_PARAMS.contains(bean_attr)) {
                continue;
            }

            ArrayList<String> beanValues = include.getParameterValues(bean_attr);

            if (beanParameters.get(bean_attr) == null || !(beanValues.contains(beanParameters.get(bean_attr)))){
                    return false;
                }
        }
        return true;
    }

    private boolean excludeMatchBeanName(Configuration conf) {
        Filter exclude = conf.getExclude();
        ArrayList<String> beanNames = exclude.getBeanNames();

        if(beanNames.contains(beanStringName)){
            return true;
        }

        for (String bean_attr : exclude.keySet()) {
            if (EXCLUDED_BEAN_PARAMS.contains(bean_attr)) {
                continue;
            }

            if (beanParameters.get(bean_attr) == null) {
                continue;
            }

            ArrayList<String> beanValues = exclude.getParameterValues(bean_attr);
            for (String beanVal : beanValues) {
                if (beanParameters.get(bean_attr).equals(beanVal)) {
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
        return excludeMatchBeanName(configuration) || matchBeanRegex(configuration.getExclude(), false);
    }

    @SuppressWarnings("unchecked")
    HashMap<Object, Object> getValueConversions(String field) {
        String fullAttributeName =(field!=null)?(getAttribute().getName() + "." + field):(getAttribute().getName());
        if (valueConversions.get(fullAttributeName) == null) {
            Object includedAttribute = matchingConf.getInclude().getAttribute();
            if (includedAttribute instanceof LinkedHashMap<?, ?>) {
                LinkedHashMap<String, LinkedHashMap<Object, Object>> attribute =
                        ((LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Object, Object>>>) includedAttribute).get(fullAttributeName);

                if (attribute != null) {
                    valueConversions.put(fullAttributeName, attribute.get("values"));
                }
            }
            if (valueConversions.get(fullAttributeName) == null) {
                valueConversions.put(fullAttributeName, new LinkedHashMap<Object, Object>());
            }
        }

        return valueConversions.get(fullAttributeName);
    }

    public Configuration getMatchingConf() {
        return matchingConf;
    }

    public void setMatchingConf(Configuration matchingConf) {
        this.matchingConf = matchingConf;

        // Now that we have the matchingConf we can:
        // - add additional tags
        this.addAdditionalTags();
        // - filter out excluded tags
        this.applyTagsBlackList();
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
     * In order, tries to:
     * * Use `alias_match` to generate an alias with a regular expression
     * * Use `alias` directly
     * * Create an generic alias prefixed with user's `metric_prefix` preference or default to `jmx`
     *
     * Argument(s):
     * * (Optional) `field`
     *   `Null` for `JMXSimpleAttribute`.
     */
    protected String getAlias(String field) {
        String alias = null;

        Filter include = getMatchingConf().getInclude();
        LinkedHashMap<String, Object> conf = getMatchingConf().getConf();

        String fullAttributeName =(field!=null)?(getAttribute().getName() + "." + field):(getAttribute().getName());

        if (include.getAttribute() instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<String, LinkedHashMap<String, String>> attribute = (LinkedHashMap<String, LinkedHashMap<String, String>>) (include.getAttribute());
            alias = getUserAlias(attribute, fullAttributeName);
        }

        if (alias == null) {
            if (conf.get("metric_prefix") != null) {
                alias = conf.get("metric_prefix") + "." + getDomain() + "." + fullAttributeName;
            } else if (getDomain().startsWith("org.apache.cassandra")) {
                alias = getCassandraAlias();
            }
        }

        //If still null - generate generic alias
        if (alias == null) {
            alias = "jmx." + getDomain() + "." + fullAttributeName;
        }
        alias = convertMetricName(alias);
        return alias;
    }

    /**
     * Metric name aliasing specific to Cassandra.
     *
     * * (Default) `cassandra_aliasing` == False.
     *   Legacy aliasing: drop `org.apache` prefix.
     * * `cassandra_aliasing` == True
     *   Comply with CASSANDRA-4009
     *
     * More information: https://issues.apache.org/jira/browse/CASSANDRA-4009
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
        //Deprecated Cassandra metric.  Remove domain prefix.
        return getDomain().replace("org.apache.", "") + "." + getAttributeName();
    }

    /**
     * Retrieve user defined alias. Substitute regular expression named groups.
     *
     * Example:
     *   ```
     *   bean: org.datadog.jmxfetch.test:foo=Bar,qux=Baz
     *   attribute:
     *     toto:
     *       alias: my.metric.$foo.$attribute
     *   ```
     *   returns a metric name `my.metric.bar.toto`
     */
    private String getUserAlias(LinkedHashMap<String, LinkedHashMap<String, String>> attribute, String fullAttributeName){
        String alias = attribute.get(fullAttributeName).get(ALIAS);
        if (alias == null) {
            return null;
        }

        alias = this.replaceByAlias(alias);

        // Attribute & domain
        alias = alias.replace("$attribute", fullAttributeName);
        alias = alias.replace("$domain", domain);

        return alias;
    }

    private String replaceByAlias(String alias){
        // Bean parameters
        for (Map.Entry<String, String> param : beanParameters.entrySet()) {
            alias = alias.replace("$" + param.getKey(), param.getValue());
        }
        return alias;
    }

    /**
     * Overload `getAlias` method.
     *
     * Note: used for `JMXSimpleAttribute` only, as `field` is null.
     */
    protected String getAlias(){
        return getAlias(null);
    }


    @SuppressWarnings("unchecked")
    protected String[] getTags() {
        if(tags != null) {
            return tags;
        }

        Filter include = matchingConf.getInclude();
        if (include != null) {
            Object includeAttribute = include.getAttribute();
            if (includeAttribute instanceof LinkedHashMap<?, ?>) {
                LinkedHashMap<String, ArrayList<String>> attributeParams = ((LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>>)includeAttribute).get(attributeName);
                if (attributeParams != null) {
                    ArrayList<String> yamlTags = attributeParams.get("tags");
                    if ( yamlTags != null) {
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

    public static List<String> getExcludedBeanParams(){
        return EXCLUDED_BEAN_PARAMS;
    }

    protected String getDomain() {
        return domain;
    }

    protected HashMap<String, String> getBeanParameters() {
        return beanParameters;
    }
}
