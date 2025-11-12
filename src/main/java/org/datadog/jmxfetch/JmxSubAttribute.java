package org.datadog.jmxfetch;

import org.datadog.jmxfetch.service.ServiceNameProvider;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;

abstract class JmxSubAttribute extends JmxAttribute {
    private Map<String, Metric> cachedMetrics = new HashMap<String, Metric>();

    public JmxSubAttribute(
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

    public Metric getCachedMetric(String name) {
        Metric metric = cachedMetrics.get(name);
        if (metric != null) {
            return metric;
        }
        String alias = getAlias(name);
        String metricType = getMetricType(name);
        String[] tags = getTags();
        metric = new Metric(alias, metricType, tags, checkName);
        cachedMetrics.put(name, metric);
        return metric;
    }

    /**
     * Check if a sub-attribute matches the filter parameters.
     *
     * @param params the filter parameters
     * @param subAttributeName the sub-attribute name to check
     * @param matchOnEmpty whether to match if the attribute filter is null
     * @return true if the sub-attribute matches
     */
    protected boolean matchSubAttribute(
            Filter params, String subAttributeName, boolean matchOnEmpty) {
        if ((params.getAttribute() instanceof Map<?, ?>)
                && ((Map<String, Object>) (params.getAttribute()))
                        .containsKey(subAttributeName)) {
            return true;
        } else if ((params.getAttribute() instanceof List<?>
                && ((List<String>) (params.getAttribute())).contains(subAttributeName))) {
            return true;
        } else if (params.getAttribute() == null) {
            return matchOnEmpty;
        }
        return false;
    }

    /**
     * Get the attribute configuration for a specific metric key.
     *
     * @param key the metric key
     * @return the attribute configuration map, or null if not found
     */
    protected Map<String, ?> getAttributesFor(String key) {
        Filter include = getMatchingConf().getInclude();
        if (include != null) {
            Object includeAttribute = include.getAttribute();
            if (includeAttribute instanceof Map<?, ?>) {
                return (Map<String, ?>) ((Map) includeAttribute).get(key);
            }
        }
        return null;
    }

    /**
     * Sort and filter metrics based on limit and sort order.
     *
     * @param metricKey the metric key
     * @param metrics the list of metrics to sort and filter
     * @return the sorted and filtered list of metrics
     */
    protected List<Metric> sortAndFilter(String metricKey, List<Metric> metrics) {
        Map<String, ?> attributes = getAttributesFor(metricKey);
        if (attributes == null || !attributes.containsKey("limit")) {
            return metrics;
        }
        Integer limit = (Integer) attributes.get("limit");
        if (metrics.size() <= limit) {
            return metrics;
        }
        MetricComparator comp = new MetricComparator();
        Collections.sort(metrics, comp);
        String sort = (String) attributes.get("sort");
        if (sort == null || sort.equals("desc")) {
            metrics.subList(0, limit).clear();
        } else {
            metrics.subList(metrics.size() - limit, metrics.size()).clear();
        }
        return metrics;
    }

    /**
     * Comparator for sorting metrics by value.
     */
    protected static class MetricComparator implements Comparator<Metric> {
        public int compare(Metric o1, Metric o2) {
            return Double.compare(o1.getValue(), o2.getValue());
        }
    }
}
