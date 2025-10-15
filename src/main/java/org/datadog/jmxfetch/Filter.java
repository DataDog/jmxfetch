package org.datadog.jmxfetch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class Filter {
    Map<String, Object> filter;
    Pattern domainRegex;
    Pattern classNameRegex;
    List<Pattern> beanRegexes = null;
    List<String> excludeTags = null;
    Map<String, String> additionalTags = null;
    List<DynamicTag> dynamicTags = null;
    boolean tagsParsed = false;

    /**
     * A simple class to manipulate include/exclude filter elements more easily A filter may
     * contain: - A domain (key: 'domain') or a domain regex (key: 'domain_regex') - Bean names
     * (key: 'bean' or 'bean_name') or bean regexes (key: 'bean_regex') - Attributes (key:
     * 'attribute') - Additional bean parameters (other keys).
     */
    @SuppressWarnings("unchecked")
    public Filter(Object filter) {
        Map<String, Object> castFilter;
        if (filter != null) {
            castFilter = (Map<String, Object>) filter;
        } else {
            castFilter = new HashMap<String, Object>();
        }
        this.filter = castFilter;
    }

    public String toString() {
        return this.filter.toString();
    }

    public Set<String> keySet() {
        return filter.keySet();
    }

    @SuppressWarnings({"unchecked"})
    private static List<String> toStringList(final Object toCast) {
        // Return object as an ArrayList wherever it's defined as
        // list or not
        //
        // ### Example
        // object:
        //                  - firstValue
        //                  - secondValue
        // ### OR
        // object: singleValue
        // ###
        if (toCast instanceof List) {
            return (List<String>) toCast;
        }
        return new ArrayList<String>(Arrays.asList((String) toCast));
    }

    public List<String> getBeanNames() {
        if (isEmptyBeanName()) {
            return Collections.emptyList();
        }
        final Object beanNames =
                (filter.get("bean") != null) ? filter.get("bean") : filter.get("bean_name");
        // Return bean names as an ArrayList wherever it's defined as
        // list or not
        //
        // ### Example
        // bean:
        //                  -
        // org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
        //                  - org.datadog.jmxfetch.test:type=type=SimpleTestJavaApp
        // ### OR
        // bean: org.datadog.jmxfetch.test:type=type=SimpleTestJavaApp
        // ###
        return toStringList(beanNames);
    }

    private static List<Pattern> toPatternList(final Object toCast) {
        List<Pattern> patternList = new ArrayList<Pattern>();
        List<String> stringList = toStringList(toCast);
        for (String string : stringList) {
            patternList.add(Pattern.compile(string));
        }
        return patternList;
    }

    public List<Pattern> getBeanRegexes() {
        // Return bean regexes as an ArrayList of Pattern whether it's defined as
        // a list or not

        if (this.beanRegexes == null) {
            if (filter.get("bean_regex") == null) {
                this.beanRegexes = Collections.emptyList();
            } else {
                final Object beanRegexNames = filter.get("bean_regex");
                this.beanRegexes = toPatternList(beanRegexNames);
            }
        }

        return this.beanRegexes;
    }

    public List<String> getExcludeTags() {
        // Return excluded tags  as an ArrayList whether it's defined as a list or not

        if (this.excludeTags == null) {
            if (filter.get("exclude_tags") == null) {
                this.excludeTags = Collections.emptyList();
            } else {
                final Object exclude_tags = filter.get("exclude_tags");
                this.excludeTags = toStringList(exclude_tags);
            }
        }

        return this.excludeTags;
    }

    private void parseTags() {
        if (tagsParsed) {
            return;
        }
        
        tagsParsed = true;
        this.additionalTags = new HashMap<String, String>();
        this.dynamicTags = new ArrayList<DynamicTag>();
        
        if (filter.get("tags") == null) {
            return;
        }
        
        Map<String, String> allTags = (Map<String, String>) filter.get("tags");
        
        for (Map.Entry<String, String> entry : allTags.entrySet()) {
            String tagName = entry.getKey();
            String tagValue = entry.getValue();
            
            if (tagValue != null && tagValue.contains("#") && tagValue.startsWith("$")) {
                try {
                    DynamicTag dynamicTag = DynamicTag.parse(tagName, tagValue);
                    this.dynamicTags.add(dynamicTag);
                } catch (Exception e) {
                    this.additionalTags.put(tagName, tagValue);
                }
            } else {
                this.additionalTags.put(tagName, tagValue);
            }
        }
    }
    
    public Map<String, String> getAdditionalTags() {
        if (this.additionalTags == null) {
            parseTags();
        }
        
        return this.additionalTags;
    }
    
    public List<DynamicTag> getDynamicTags() {
        if (this.dynamicTags == null) {
            parseTags();
        }
        
        return this.dynamicTags;
    }

    public String getDomain() {
        return (String) filter.get("domain");
    }

    public Pattern getDomainRegex() {
        if (this.filter.get("domain_regex") == null) {
            return null;
        }

        if (this.domainRegex == null) {
            this.domainRegex = Pattern.compile((String) this.filter.get("domain_regex"));
        }

        return this.domainRegex;
    }

    public String getClassName() {
        return (String) filter.get("class");
    }

    public Pattern getClassNameRegex() {
        if (this.filter.get("class_regex") == null) {
            return null;
        }

        if (this.classNameRegex == null) {
            this.classNameRegex = Pattern.compile((String) this.filter.get("class_regex"));
        }

        return this.classNameRegex;
    }


    public Object getAttribute() {
        return filter.get("attribute");
    }

    public List<String> getParameterValues(String parameterName) {
        // Return bean attributes values as an ArrayList wherever it's defined as
        // list or not
        //
        // ### Example
        // bean_parameter:
        //                  - exampleType1
        //                  - exampleType2
        // ### OR
        // bean_parameter: onlyOneType
        // ###
        final Object beanValues = filter.get(parameterName);
        return toStringList(beanValues);
    }

    public boolean isEmptyBeanName() {
        return (filter.get("bean") == null && filter.get("bean_name") == null);
    }

    public boolean isEmptyFilter() {
        return filter.isEmpty();
    }
}
