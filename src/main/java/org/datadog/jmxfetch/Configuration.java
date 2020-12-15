package org.datadog.jmxfetch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Configuration {

    private Map<String, Object> conf;
    private Filter include;
    private Filter exclude;

    /**
     * Access configuration elements more easily
     *
     * <p>Also provides helper methods to extract common information among filters.
     */
    public Configuration(Map<String, Object> conf) {
        this.conf = conf;
        this.include = new Filter(conf.get("include"));
        this.exclude = new Filter(conf.get("exclude"));
    }

    public Map<String, Object> getConf() {
        return conf;
    }

    public Filter getInclude() {
        return include;
    }

    public Filter getExclude() {
        return exclude;
    }

    public String toString() {
        return "include: " + this.include + " - exclude: " + this.exclude;
    }

    private Boolean hasInclude() {
        return getInclude() != null && !getInclude().isEmptyFilter();
    }

    /**
     * Filter a configuration list to keep the ones with `include` filters.
     *
     * @param configurationList the configuration list to filter
     * @return a configuration list
     */
    private static List<Configuration> getIncludeConfigurationList(
            List<Configuration> configurationList) {
        List<Configuration> includeConfigList = new ArrayList<Configuration>(configurationList);
        Iterator<Configuration> confItr = includeConfigList.iterator();

        while (confItr.hasNext()) {
            Configuration conf = confItr.next();
            if (!conf.hasInclude()) {
                confItr.remove();
            }
        }
        return includeConfigList;
    }

    /**
     * Extract `include` filters from the configuration list and index then by domain name.
     *
     * @param configurationList the configuration list to process
     * @return filters by domain name
     */
    private static Map<String, List<Filter>> getIncludeFiltersByDomain(
            List<Configuration> configurationList) {
        Map<String, List<Filter>> includeFiltersByDomain =
                new HashMap<String, List<Filter>>();

        for (Configuration conf : configurationList) {
            Filter filter = conf.getInclude();
            List<Filter> filters = new ArrayList<Filter>();

            // Convert bean name, to a proper filter, i.e. a hash
            if (!filter.isEmptyBeanName()) {
                List<String> beanNames = filter.getBeanNames();

                for (String beanName : beanNames) {
                    String[] splitBeanName = beanName.split(":");
                    String domain = splitBeanName[0];
                    String rawBeanParameters = splitBeanName[1];
                    Map<String, String> beanParametersHash =
                            JmxAttribute.getBeanParametersHash(rawBeanParameters);
                    beanParametersHash.put("domain", domain);
                    filters.add(new Filter(beanParametersHash));
                }
            } else {
                filters.add(filter);
            }

            for (Filter f : filters) {
                //  Retrieve the existing filters for the domain, add the new filters
                List<Filter> domainFilters;
                String domainName = f.getDomain();

                if (includeFiltersByDomain.containsKey(domainName)) {
                    domainFilters = includeFiltersByDomain.get(domainName);
                } else {
                    domainFilters = new ArrayList<Filter>();
                }

                domainFilters.add(f);
                includeFiltersByDomain.put(domainName, domainFilters);
            }
        }
        return includeFiltersByDomain;
    }

    /**
     * Extract, among filters, bean key parameters in common.
     *
     * @param filtersByDomain filters by domain name
     * @return common bean key parameters by domain name
     */
    private static Map<String, Set<String>> getCommonBeanKeysByDomain(
            Map<String, List<Filter>> filtersByDomain) {
        Map<String, Set<String>> beanKeysIntersectionByDomain =
                new HashMap<String, Set<String>>();

        for (Entry<String, List<Filter>> filtersEntry : filtersByDomain.entrySet()) {
            List<Filter> filters = filtersEntry.getValue();
            if (filters == null || filters.isEmpty()) {
                continue;
            }
            // Compute keys intersection
            Set<String> keysIntersection = new HashSet<String>(filters.get(0).keySet());

            for (Filter filter : filters) {
                keysIntersection.retainAll(filter.keySet());
            }

            // Remove special parameters
            for (String param : JmxAttribute.getExcludedBeanParams()) {
                keysIntersection.remove(param);
            }
            String domainName = filtersEntry.getKey();
            beanKeysIntersectionByDomain.put(domainName, keysIntersection);
        }

        return beanKeysIntersectionByDomain;
    }

    /**
     * Build a map of common bean keys->values, with the specified bean keys, among the given
     * filters.
     *
     * @param beanKeysByDomain bean keys by domain name
     * @param filtersByDomain filters by domain name
     * @return bean pattern (keys->values) by domain name
     */
    private static Map<String, Map<String, String>> getCommonScopeByDomain(
            Map<String, Set<String>> beanKeysByDomain,
            Map<String, List<Filter>> filtersByDomain) {
        // Compute a common scope a among filters by domain name
        Map<String, Map<String, String>> commonScopeByDomain =
                new HashMap<String, Map<String, String>>();

        for (Entry<String, Set<String>> commonParametersByDomainEntry :
                beanKeysByDomain.entrySet()) {
            String domainName = commonParametersByDomainEntry.getKey();
            Set<String> commonParameters = commonParametersByDomainEntry.getValue();
            List<Filter> filters = filtersByDomain.get(domainName);
            Map<String, String> commonScope = new HashMap<String, String>();

            for (String parameter : commonParameters) {
                // Check if all values associated with the parameters are the same
                String commonValue = null;
                boolean hasCommonValue = true;

                for (Filter f : filters) {
                    List<String> parameterValues = f.getParameterValues(parameter);

                    if (parameterValues.size() != 1
                            || (commonValue != null
                                    && !commonValue.equals(parameterValues.get(0)))) {
                        hasCommonValue = false;
                        break;
                    }
                    commonValue = parameterValues.get(0);
                }
                if (hasCommonValue) {
                    commonScope.put(parameter, commonValue);
                }
            }
            commonScopeByDomain.put(domainName, commonScope);
        }

        return commonScopeByDomain;
    }

    /**
     * Stringify a bean pattern.
     *
     * @param domain domain name
     * @param beanScope map of bean keys-> values
     * @return string pattern identifying the bean scope
     */
    private static String beanScopeToString(
            String domain, Map<String, String> beanScope) {
        String result = "";

        // Domain
        domain = (domain != null) ? domain : "*";
        result += domain + ":";

        // Scope parameters
        for (Entry<String, String> beanScopeEntry : beanScope.entrySet()) {
            String param = beanScopeEntry.getKey();
            String value = beanScopeEntry.getValue();

            result += param + "=" + value + ",";
        }
        result += "*";

        return result;
    }

    /**
     * Find, among the configuration list, a potential common bean pattern by domain name.
     *
     * @param configurationList the configuration list to process
     * @return common bean pattern strings
     */
    public static List<String> getGreatestCommonScopes(
            List<Configuration> configurationList) {
        List<Configuration> includeConfigList =
                getIncludeConfigurationList(configurationList);
        Map<String, List<Filter>> includeFiltersByDomain =
                getIncludeFiltersByDomain(includeConfigList);
        Map<String, Set<String>> parametersIntersectionByDomain =
                getCommonBeanKeysByDomain(includeFiltersByDomain);
        Map<String, Map<String, String>> commonBeanScopeByDomain =
                getCommonScopeByDomain(parametersIntersectionByDomain, includeFiltersByDomain);

        List<String> result = new ArrayList<String>(commonBeanScopeByDomain.entrySet().size());

        for (Entry<String, Map<String, String>> beanScopeEntry :
                commonBeanScopeByDomain.entrySet()) {
            String domain = beanScopeEntry.getKey();
            Map<String, String> beanScope = beanScopeEntry.getValue();

            result.add(beanScopeToString(domain, beanScope));
        }

        return result;
    }
}
