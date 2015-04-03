package org.datadog.jmxfetch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

public class Configuration {

    private LinkedHashMap<String, Object> conf;
    private Filter include;
    private Filter exclude;

    /**
     * Access configuration elements more easily
     *
     * Also provides helper methods to extract common information among filters.
     */
    public Configuration(LinkedHashMap<String, Object> conf) {
        this.conf = conf;
        this.include = new Filter(conf.get("include"));
        this.exclude = new Filter(conf.get("exclude"));
    }

    public LinkedHashMap<String, Object> getConf() {
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

    private Boolean hasInclude(){
        return getInclude() != null;
    }

    /**
     * Filter a configuration list to keep the ones with `include` filters.
     *
     * @param configurationList         the configuration list to filter
     *
     * @return                          a configuration list
     */
    private static LinkedList<Configuration> getIncludeConfigurationList(LinkedList<Configuration> configurationList){
        LinkedList<Configuration> includeConfigList = new LinkedList<Configuration>(configurationList);
        Iterator<Configuration> confItr = includeConfigList.iterator();

        while(confItr.hasNext()) {
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
     * @param configurationList         the configuration list to process
     *
     * @return                          filters by domain name
     */
    private static HashMap<String, LinkedList<Filter>> getIncludeFiltersByDomain(LinkedList<Configuration> configurationList){
        HashMap<String, LinkedList<Filter>> includeFiltersByDomain = new HashMap<String, LinkedList<Filter>>();

        for (Configuration conf : configurationList) {
            Filter filter = conf.getInclude();
            LinkedList<Filter> filters = new LinkedList<Filter>();

            // Convert bean name, to a proper filter, i.e. a hash
            if (!filter.isEmptyBeanName()) {
                ArrayList<String> beanNames = filter.getBeanNames();

                for (String beanName : beanNames) {
                    String[] splitBeanName = beanName.split(":");
                    String domain = splitBeanName[0];
                    String rawBeanParameters = splitBeanName[1];
                    HashMap<String, String> beanParametersHash = JMXAttribute.getBeanParametersHash(rawBeanParameters);
                    beanParametersHash.put("domain", domain);
                    filters.add(new Filter(beanParametersHash));
                }
            } else {
                filters.add(filter);
            }

            for (Filter f: filters) {
                //  Retrieve the existing filters for the domain, add the new filters
                LinkedList<Filter> domainFilters;
                String domainName = f.getDomain();

                if (includeFiltersByDomain.containsKey(domainName)) {
                    domainFilters = includeFiltersByDomain.get(domainName);
                } else {
                    domainFilters = new LinkedList<Filter>();
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
     * @param filtersByDomain       filters by domain name
     *
     * @return                      common bean key parameters by domain name
     */
    private static HashMap<String, Set<String>> getCommonBeanKeysByDomain(HashMap<String, LinkedList<Filter>> filtersByDomain){
        HashMap<String, Set<String>> beanKeysIntersectionByDomain = new HashMap<String,Set<String>>();

        for (Entry<String, LinkedList<Filter>> filtersEntry : filtersByDomain.entrySet()) {
            String domainName = filtersEntry.getKey();
            LinkedList<Filter> mFilters= filtersEntry.getValue();

            // Compute keys intersection
            Set<String> keysIntersection = new HashSet<String>(mFilters.getFirst().keySet());

            for (Filter f: mFilters) {
                keysIntersection.retainAll(f.keySet());
            }

            // Remove special parameters
            for(String param : JMXAttribute.getExcludedBeanParams()){
                keysIntersection.remove(param);
            }

            beanKeysIntersectionByDomain.put(domainName, keysIntersection);
        }

        return beanKeysIntersectionByDomain;
    }

    /**
     * Build a map of common bean keys->values, with the specified bean keys, among the given filters.
     *
     * @param beanKeysByDomain      bean keys by domain name
     * @param filtersByDomain       filters by domain name
     *
     * @return                      bean pattern (keys->values) by domain name
     */
    private static HashMap<String, LinkedHashMap<String, String>> getCommonScopeByDomain(HashMap<String, Set<String>> beanKeysByDomain, HashMap<String, LinkedList<Filter>> filtersByDomain){
        // Compute a common scope a among filters by domain name
        HashMap<String, LinkedHashMap<String, String>> commonScopeByDomain = new HashMap<String, LinkedHashMap<String, String>>();

        for (Entry<String, Set<String>> commonParametersByDomainEntry : beanKeysByDomain.entrySet()) {
            String domainName = commonParametersByDomainEntry.getKey();
            Set<String> commonParameters = commonParametersByDomainEntry.getValue();
            LinkedList<Filter> filters = filtersByDomain.get(domainName);
            LinkedHashMap<String, String> commonScope = new LinkedHashMap<String, String>();

            for (String parameter : commonParameters) {
                // Check if all values associated with the parameters are the same
                String commonValue = null;
                Boolean hasCommonValue = true;

                for (Filter f : filters) {
                    ArrayList<String> parameterValues = f.getParameterValues(parameter);

                    if (parameterValues.size() != 1 || (commonValue != null && !commonValue.equals(parameterValues.get(0)))) {
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
     * @param domain                domain name
     * @param beanScope             map of bean keys-> values
     *
     * @return                      string pattern identifying the bean scope
     */
    private static String beanScopeToString(String domain, LinkedHashMap<String, String> beanScope){
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
     * @param configurationList         the configuration list to process
     *
     * @return                          common bean pattern strings
     */
    public static LinkedList<String> getGreatestCommonScopes(LinkedList<Configuration> configurationList){
        LinkedList<Configuration> includeConfigList = getIncludeConfigurationList(configurationList);
        HashMap<String, LinkedList<Filter>> includeFiltersByDomain = getIncludeFiltersByDomain(includeConfigList);
        HashMap<String, Set<String>> parametersIntersectionByDomain = getCommonBeanKeysByDomain(includeFiltersByDomain);
        HashMap<String, LinkedHashMap<String, String>> commonBeanScopeByDomain = getCommonScopeByDomain(parametersIntersectionByDomain, includeFiltersByDomain);

        LinkedList<String> result = new LinkedList<String>();

        for (Entry<String, LinkedHashMap<String, String>> beanScopeEntry: commonBeanScopeByDomain.entrySet()) {
            String domain = beanScopeEntry.getKey();
            LinkedHashMap<String, String> beanScope = beanScopeEntry.getValue();

            result.add(beanScopeToString(domain, beanScope));
        }

        return result;
    }

}
