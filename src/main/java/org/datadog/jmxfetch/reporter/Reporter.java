package org.datadog.jmxfetch.reporter;

import com.timgroup.statsd.ServiceCheck;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import org.datadog.jmxfetch.App;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;
import org.datadog.jmxfetch.Metric;
import org.datadog.jmxfetch.Status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class Reporter {

    public static final String VALUE = "value";

    private Map<String, Integer> serviceCheckCount;
    private Map<String, Map<String, Map<String, Object>>> ratesAggregator =
            new HashMap<String, Map<String, Map<String, Object>>>();
    private Map<String, Map<String, Long>> countersAggregator =
            new HashMap<String, Map<String, Long>>();

    /** Reporter constructor. */
    public Reporter() {
        this.serviceCheckCount = new HashMap<String, Integer>();
    }

    String generateId(Metric metric) {
        String key = metric.getAlias();
        StringBuilder sb = new StringBuilder(key);
        for (String tag : metric.getTags()) {
            sb.append(tag);
        }
        return sb.toString();
    }

    /** Clears the rate aggregator for the provided instance name. */
    public void clearRatesAggregator(String instanceName) {
        ratesAggregator.put(instanceName, new HashMap<String, Map<String, Object>>());
    }

    /** Clears the counter aggregator for the provided instance name.  */
    public void clearCountersAggregator(String instanceName) {
        countersAggregator.put(instanceName, new HashMap<String, Long>());
    }

    /** Submits the metrics in the implementing reporter. */
    public void sendMetrics(List<Metric> metrics, String instanceName, boolean canonicalRate) {
        Map<String, Map<String, Object>> instanceRatesAggregator;
        Map<String, Long> instanceCountersAggregator;

        if (ratesAggregator.containsKey(instanceName)) {
            instanceRatesAggregator = ratesAggregator.get(instanceName);
        } else {
            instanceRatesAggregator = new HashMap<String, Map<String, Object>>();
        }

        if (countersAggregator.containsKey(instanceName)) {
            instanceCountersAggregator = countersAggregator.get(instanceName);
        } else {
            instanceCountersAggregator = new HashMap<String, Long>();
        }

        int loopCounter = App.getLoopCounter();

        String sendingMessage =
                "Instance "
                        + instanceName
                        + " is sending "
                        + metrics.size()
                        + " metrics to the metrics reporter during collection #"
                        + loopCounter;
        if (loopCounter <= 5 || loopCounter % 10 == 0) {
            log.info(sendingMessage);
            if (loopCounter == 5) {
                log.info("Next collections will be logged only every 10 collections.");
            }
        } else {
            log.debug(sendingMessage);
        }

        for (Metric metric : metrics) {
            Double currentValue = (Double) metric.getValue();
            if (currentValue.isNaN() || currentValue.isInfinite()) {
                continue;
            }

            String metricName = metric.getAlias();
            String metricType = metric.getMetricType();
            String[] tags = metric.getTags();

            // StatsD doesn't support rate metrics so we need to have our own aggregator to compute
            // rates
            if ("gauge".equals(metricType) || "histogram".equals(metricType)) {
                sendMetricPoint(metricType, metricName, currentValue, tags);
            } else if ("monotonic_count".equals(metricType)) {
                String key = generateId(metric);
                if (!instanceCountersAggregator.containsKey(key)) {
                    instanceCountersAggregator.put(key,  currentValue.longValue());
                    continue;
                }

                long oldValue = instanceCountersAggregator.get(key);
                long delta = currentValue.longValue() - oldValue;

                if (Double.isNaN(delta) || Double.isInfinite(delta)) {
                    continue;
                }

                instanceCountersAggregator.put(key, currentValue.longValue());

                if (delta < 0) {
                    log.info("Counter " + metricName + " has been reset - not submitting.");
                    continue;
                }
                sendMetricPoint(metricType, metricName, delta, tags);

            } else { // The metric should be 'counter'
                String key = generateId(metric);
                if (!instanceRatesAggregator.containsKey(key)) {
                    Map<String, Object> rateInfo = new HashMap<String, Object>();
                    rateInfo.put("ts", System.currentTimeMillis());
                    rateInfo.put(VALUE, currentValue);
                    instanceRatesAggregator.put(key, rateInfo);
                    continue;
                }

                long oldTs = (Long) instanceRatesAggregator.get(key).get("ts");
                double oldValue = (Double) instanceRatesAggregator.get(key).get(VALUE);

                long now = System.currentTimeMillis();
                double rate = 1000 * (currentValue - oldValue) / (now - oldTs);

                boolean sane = (!Double.isNaN(rate) && !Double.isInfinite(rate));
                boolean submit = (rate >= 0 || !canonicalRate);

                if (sane && submit) {
                    sendMetricPoint(metricType, metricName, rate, tags);
                } else if (sane) {
                    log.info(
                            "Canonical rate option set, and negative rate (counter reset)"
                                    + "not submitting.");
                }

                instanceRatesAggregator.get(key).put("ts", now);
                instanceRatesAggregator.get(key).put(VALUE, currentValue);
            }
        }

        ratesAggregator.put(instanceName, instanceRatesAggregator);
        countersAggregator.put(instanceName, instanceCountersAggregator);
    }

    /** Submits service check. */
    public void sendServiceCheck(String checkName, String status, String message, String[] tags) {
        this.incrementServiceCheckCount(checkName);
        String serviceCheckName = String.format(
            "%s.can_connect", Reporter.formatServiceCheckPrefix(checkName));

        this.doSendServiceCheck(serviceCheckName, status, message, tags);
    }

    /** Increments the service check count - for book-keeping purposes. */
    public void incrementServiceCheckCount(String checkName) {
        int scCount = this.getServiceCheckCount(checkName);
        this.getServiceCheckCountMap().put(checkName, new Integer(scCount + 1));
    }

    public int getServiceCheckCount(String checkName) {
        Integer scCount = this.serviceCheckCount.get(checkName);
        return (scCount == null) ? 0 : scCount.intValue();
    }

    public void resetServiceCheckCount(String checkName) {
        this.serviceCheckCount.put(checkName, new Integer(0));
    }

    protected Map<String, Integer> getServiceCheckCountMap() {
        return this.serviceCheckCount;
    }

    /** Formats the service check prefix. */
    public static String formatServiceCheckPrefix(String fullname) {
        String[] chunks = fullname.split("\\.");
        chunks[0] = chunks[0].replaceAll("[A-Z0-9:_\\-]", "");
        return StringUtils.join(chunks, ".");
    }

    protected ServiceCheck.Status statusToServiceCheckStatus(String status) {
        if (status == Status.STATUS_OK) {
            return ServiceCheck.Status.OK;
        } else if (status == Status.STATUS_WARNING) {
            return ServiceCheck.Status.WARNING;
        } else if (status == Status.STATUS_ERROR) {
            return ServiceCheck.Status.CRITICAL;
        }
        return ServiceCheck.Status.UNKNOWN;
    }

    protected abstract void sendMetricPoint(
            String metricType, String metricName, double value, String[] tags);

    protected abstract void doSendServiceCheck(
            String checkName, String status, String message, String[] tags);

    public abstract void displayMetricReached();

    public abstract void displayNonMatchingAttributeName(JmxAttribute jmxAttribute);

    public abstract void displayInstanceName(Instance instance);

    public abstract void displayMatchingAttributeName(
            JmxAttribute jmxAttribute, int rank, int limit);
}
