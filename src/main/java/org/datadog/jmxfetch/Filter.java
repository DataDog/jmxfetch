package org.datadog.jmxfetch;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Set;
import java.lang.ClassCastException;


class Filter {
    LinkedHashMap<String, Object> filter;

    /**
     * A simple class to manipulate include/exclude filter elements more easily
     * A filter may contain:
     * - A domain (key: 'domain')
     * - Bean names (key: 'bean' or 'bean_name')
     * - Attributes (key: 'attribute')
     * - Additional bean parameters (other keys)
     */

    @SuppressWarnings("unchecked")
    public Filter(Object filter) {
        LinkedHashMap<String, Object> castFilter;
        if (filter != null) {
            castFilter = (LinkedHashMap<String, Object>) filter;
        } else{
            castFilter = new LinkedHashMap<String, Object>();
        }
        this.filter = castFilter;
    }

    public String toString() {
        return this.filter.toString();
    }

    public Set<String> keySet() {
        return filter.keySet();
    }


    @SuppressWarnings({ "unchecked", "serial" })
    private static ArrayList<String> toStringArrayList(final Object toCast) {
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
        try{
            return (ArrayList<String>) toCast;
        } catch (ClassCastException e){
            return new ArrayList<String>() {{
                add(((String) toCast));
            }};
        }
    }


    public ArrayList<String> getBeanNames() {
        if (isEmptyBeanName()){
            return new ArrayList<String>();
        }
        final Object beanNames = (filter.get("bean") != null) ? filter.get("bean") : filter.get("bean_name");
        // Return bean names as an ArrayList wherever it's defined as
        // list or not
        //
        // ### Example
        // bean:
        //                  - org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
        //                  - org.datadog.jmxfetch.test:type=type=SimpleTestJavaApp
        // ### OR
        // bean: org.datadog.jmxfetch.test:type=type=SimpleTestJavaApp
        // ###
        return toStringArrayList(beanNames);
    }

    public String getDomain() {
        return (String) filter.get("domain");
    }

    public Object getAttribute() {
        return filter.get("attribute");
    }

    public ArrayList<String> getParameterValues(String parameterName) {
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
        return toStringArrayList(beanValues);
    }

    public boolean isEmptyBeanName() {
        return (filter.get("bean") == null && filter.get("bean_name") == null);
    }

}
