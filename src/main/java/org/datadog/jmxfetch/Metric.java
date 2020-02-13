package org.datadog.jmxfetch;

/**
 * Metric carrier class.
 */
public class Metric {
    private final String alias;
    private final String metricType;
    private final double value;
    private final String[] tags;
    private String checkName;

    /**
     * Metric constructor.
     */
    public Metric(String alias, String metricType, double value, String[] tags) {
        this.alias = alias;
        this.metricType = metricType;
        this.value = value;
        this.tags = tags;
    }

    public String getAlias() {
        return alias;
    }

    public String getMetricType() {
        return metricType;
    }

    public double getValue() {
        return value;
    }

    public String[] getTags() {
        return tags;
    }

    public String getCheckName() {
        return checkName;
    }

    public void setCheckName(String checkName) {
        this.checkName = checkName;
    }
}
