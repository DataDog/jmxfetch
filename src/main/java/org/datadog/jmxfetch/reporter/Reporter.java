package org.datadog.jmxfetch.reporter;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.datadog.jmxfetch.App;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public abstract class Reporter {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    public static final String VALUE = "value";

    private HashMap<String, Integer> serviceCheckCount;
    private HashMap<String, HashMap<String, HashMap<String, Object>>> ratesAggregator =
            new HashMap<String, HashMap<String, HashMap<String, Object>>>();

    /**
     * Reporter constructor. 
     * */
    public Reporter() {
        this.serviceCheckCount = new HashMap<String, Integer>();
    }

    String generateId(HashMap<String, Object> metric) {
        String key = (String) metric.get("alias");
        for (String tag : (String[]) metric.get("tags")) {
            key += tag;
        }
        return key;
    }

    /**
     * Clears the rate aggregator for the provided instance name. 
     * */
    public void clearRatesAggregator(String instanceName) {
        ratesAggregator.put(instanceName, new HashMap<String, HashMap<String, Object>>());
    }

    /**
     * Submits the metrics in the implementing reporter. 
     * */
    public void sendMetrics(
            LinkedList<HashMap<String, Object>> metrics,
            String instanceName,
            boolean canonicalRate) {
        HashMap<String, HashMap<String, Object>> instanceRatesAggregator;
        if (ratesAggregator.containsKey(instanceName)) {
            instanceRatesAggregator = ratesAggregator.get(instanceName);
        } else {
            instanceRatesAggregator = new HashMap<String, HashMap<String, Object>>();
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
            LOGGER.info(sendingMessage);
            if (loopCounter == 5) {
                LOGGER.info("Next collections will be logged only every 10 collections.");
            }
        } else {
            LOGGER.debug(sendingMessage);
        }

        for (HashMap<String, Object> m : metrics) {
            // We need to edit metrics for legacy reasons (rename metrics, etc)
            HashMap<String, Object> metric = new HashMap<String, Object>(m);

            Double currentValue = (Double) metric.get(VALUE);
            if (currentValue.isNaN() || currentValue.isInfinite()) {
                continue;
            }

            String metricName = (String) metric.get("alias");
            String metricType = (String) metric.get("metric_type");
            String[] tags = Arrays.asList((String[]) metric.get("tags")).toArray(new String[0]);

            // StatsD doesn't support rate metrics so we need to have our own aggregator to compute
            // rates
            if ("gauge".equals(metricType) || "histogram".equals(metricType)) {
                sendMetricPoint(metricType, metricName, currentValue, tags);
            } else { // The metric should be 'counter'
                String key = generateId(metric);
                if (!instanceRatesAggregator.containsKey(key)) {
                    HashMap<String, Object> rateInfo = new HashMap<String, Object>();
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
                    LOGGER.info(
                            "Canonical rate option set, and negative rate (counter reset)" 
                            + "not submitting.");
                }

                instanceRatesAggregator.get(key).put("ts", now);
                instanceRatesAggregator.get(key).put(VALUE, currentValue);
            }
        }

        ratesAggregator.put(instanceName, instanceRatesAggregator);
    }

    /**
     * Submits service check. 
     * */
    public void sendServiceCheck(String checkName, String status, String message, String[] tags) {
        this.incrementServiceCheckCount(checkName);
        String dataName = Reporter.formatServiceCheckPrefix(checkName);

        this.doSendServiceCheck(dataName, status, message, tags);
    }

    /**
     * Increments the service check count - for book-keeping purposes. 
     * */
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

    protected HashMap<String, Integer> getServiceCheckCountMap() {
        return this.serviceCheckCount;
    }

    /**
     * Formats the service check prefix. 
     * */
    public static String formatServiceCheckPrefix(String fullname) {
        String[] chunks = fullname.split("\\.");
        chunks[0] = chunks[0].replaceAll("[A-Z0-9:_\\-]", "");
        return StringUtils.join(chunks, ".");
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
