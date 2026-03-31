package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.service.ServiceNameProvider;
import org.datadog.jmxfetch.util.JeeStatisticsAttributes;
import org.datadog.jmxfetch.util.PmiStatisticsAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * Handles WebSphere PMI Stats objects with subCollections.
 * Each subCollection contains a named group of statistics that should be tagged
 * with the subCollection name.
 */
@Slf4j
public class PmiSubCollectionAttribute extends JmxSubAttribute {
    // Map from subCollection name to list of statistic attribute names (e.g., "statName.count")
    private final Map<String, List<String>> subCollectionAttributes;

    /** Constructor for PmiSubCollectionAttribute. */
    public PmiSubCollectionAttribute(
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
        subCollectionAttributes = new HashMap<String, List<String>>();
    }

    /**
     * Check if this attribute is a WebSphere Stats object with subCollections.
     * Performs comprehensive checks:
     * 1. Domain must be "WebSphere"
     * 2. Attribute type must be Stats
     * 3. Connection used to fetch and verify the attribute has subCollections
     */
    public static boolean matchAttribute(
            String attributeType,
            ObjectName beanName,
            MBeanAttributeInfo attributeInfo,
            Connection connection) {
        // First check: domain must be WebSphere
        if (!"WebSphere".equals(beanName.getDomain())) {
            return false;
        }

        // Second check: attribute type must be Stats
        if (!"javax.management.j2ee.statistics.Stats".equals(attributeType)) {
            return false;
        }

        // Third and fourth check: fetch the attribute value and verify it has
        // subCollections
        try {
            Object value = connection.getAttribute(beanName, attributeInfo.getName());
            if (value == null) {
                return false;
            }

            // Fourth check: must be a JEE Stat with subCollections
            if (!JeeStatisticsAttributes.isJeeStat(value)) {
                return false;
            }

            return PmiStatisticsAttributes.hasSubCollections(value);
        } catch (Exception e) {
            log.debug(
                "Unable to fetch attribute {} from {} to check for subCollections: {}",
                attributeInfo.getName(),
                beanName,
                e.getMessage());
            return false;
        }
    }

    private boolean matchAttribute(Configuration configuration) {
        if (matchSubAttribute(configuration.getInclude(), getAttributeName(), true)) {
            return true;
        }

        Iterator<String> it1 = subCollectionAttributes.keySet().iterator();
        while (it1.hasNext()) {
            String subCollectionName = it1.next();
            List<String> attributes = subCollectionAttributes.get(subCollectionName);
            Iterator<String> it2 = attributes.iterator();
            while (it2.hasNext()) {
                String attrKey = it2.next();
                if (!matchSubAttribute(
                        configuration.getInclude(), getAttributeName() + "." + attrKey, true)) {
                    it2.remove();
                }
            }
            if (attributes.isEmpty()) {
                it1.remove();
            }
        }

        return !subCollectionAttributes.isEmpty();
    }

    private void populateSubCollectionAttributes(Object value) {
        if (!JeeStatisticsAttributes.isJeeStat(value)) {
            return;
        }

        // Get the subCollections structure from JeeStatisticsAttributes
        Map<String, List<String>> subCollections =
                PmiStatisticsAttributes.getSubCollectionStatistics(value);

        if (!subCollections.isEmpty()) {
            subCollectionAttributes.putAll(subCollections);
            int totalAttributes = 0;
            for (Map.Entry<String, List<String>> entry : subCollections.entrySet()) {
                String subCollName = entry.getKey();
                List<String> attrs = entry.getValue();
                totalAttributes += attrs.size();
                log.trace("SubCollection '{}' attributes: {}", subCollName, attrs);
            }
            log.debug("Found {} subCollections in {} with total {} attributes",
                subCollections.size(), getBeanName(), totalAttributes);
        }

        // Also get regular statistics (not in subCollections)
        List<String> regularStats = JeeStatisticsAttributes.getStatisticNames(value);
        if (!regularStats.isEmpty()) {
            // Stats not in a subCollection go under a default key
            subCollectionAttributes.put("", regularStats);
            log.debug("Found {} regular statistics in {}", regularStats.size(), getBeanName());
            log.trace("Regular statistics attributes: {}", regularStats);
        }
    }

    protected String[] getTags(String subCollectionName, String subAttribute)
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {
        List<String> tagsList = new ArrayList<String>();
        String fullMetricKey = getAttributeName() + "." + subAttribute;
        Map<String, ?> attributeParams = getAttributesFor(fullMetricKey);

        if (attributeParams != null) {
            Map<String, String> yamlTags = (Map) attributeParams.get("tags");

            if (yamlTags != null) {
                for (String tagName : yamlTags.keySet()) {
                    String value = yamlTags.get(tagName);

                    // Support tag value substitution (e.g., $subCollectionName)
                    if (value.startsWith("$")) {
                        String varName = value.substring(1);
                        if ("subCollection".equals(varName) && !subCollectionName.isEmpty()) {
                            value = subCollectionName;
                        }
                    }

                    tagsList.add(tagName + ":" + value);
                }
            }
        }

        // Add subCollection name as a tag if it's not empty
        if (!subCollectionName.isEmpty()) {
            tagsList.add("subcollection:" + subCollectionName);
        }

        String[] defaultTags = super.getTags();
        tagsList.addAll(Arrays.asList(defaultTags));

        String[] tags = new String[tagsList.size()];
        tags = tagsList.toArray(tags);
        return tags;
    }

    @Override
    public List<Metric> getMetrics()
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {
        Map<String, List<Metric>> subMetrics = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : subCollectionAttributes.entrySet()) {
            String subCollectionName = entry.getKey();
            List<String> statisticAttributes = entry.getValue();

            for (String metricKey : statisticAttributes) {
                String alias = getAlias(metricKey);
                String metricType = getMetricType(metricKey);
                String[] tags = getTags(subCollectionName, metricKey);
                Metric metric = new Metric(alias, metricType, tags, checkName);
                double value = castToDouble(getValue(subCollectionName, metricKey), null);
                metric.setValue(value);

                String fullMetricKey = getAttributeName() + "." + metricKey;
                if (!subMetrics.containsKey(fullMetricKey)) {
                    subMetrics.put(fullMetricKey, new ArrayList<Metric>());
                }
                subMetrics.get(fullMetricKey).add(metric);
            }
        }

        List<Metric> metrics = new ArrayList<>(subMetrics.size());
        for (String key : subMetrics.keySet()) {
            // Only add explicitly included metrics
            if (getAttributesFor(key) != null) {
                metrics.addAll(sortAndFilter(key, subMetrics.get(key)));
            }
        }

        return metrics;
    }

    private Object getValue(String subCollectionName, String subAttribute)
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {

        Object value = this.getJmxValue();

        if (JeeStatisticsAttributes.isJeeStat(value)) {
            // If this is from a subCollection, prefix with the subCollection name
            String fullPath = subCollectionName.isEmpty()
                ? subAttribute
                : subCollectionName + "." + subAttribute;
            return PmiStatisticsAttributes.getStatisticDataFor(value, fullPath);
        }

        throw new NumberFormatException();
    }

    @Override
    public boolean match(Configuration configuration) {
        if (!matchDomain(configuration)
                || !matchClassName(configuration)
                || !matchBean(configuration)
                || excludeMatchDomain(configuration)
                || excludeMatchClassName(configuration)
                || excludeMatchBean(configuration)) {
            return false;
        }

        try {
            Object value = getJmxValue();

            // Only match if this is a Stats object with subCollections
            if (!JeeStatisticsAttributes.isJeeStat(value)) {
                return false;
            }

            // Check if it has subCollections
            if (!PmiStatisticsAttributes.hasSubCollections(value)) {
                return false;
            }

            populateSubCollectionAttributes(value);
        } catch (Exception e) {
            log.debug("Failed to populate subCollection attributes for {}: {}",
                getBeanName(), e.getMessage());
            return false;
        }

        return matchAttribute(configuration);
    }
}
