package org.datadog.jmxfetch.reporter;

import org.apache.log4j.Logger;
import org.datadog.jmxfetch.App;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JMXAttribute;

import java.util.Arrays;
import java.util.HashMap;
import java.lang.Integer;
import java.util.LinkedList;


public abstract class Reporter {

    private final static Logger LOGGER = Logger.getLogger(App.class.getName());

    private HashMap<String, Integer> serviceCheckCount;
    private HashMap<String, HashMap<String, HashMap<String, Object>>> ratesAggregator = new HashMap<String, HashMap<String, HashMap<String, Object>>>();

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

    public void clearRatesAggregator(String instanceName) {
        ratesAggregator.put(instanceName, new HashMap<String, HashMap<String, Object>>());
    }

    public void sendMetrics(LinkedList<HashMap<String, Object>> metrics, String instanceName) {
        HashMap<String, HashMap<String, Object>> instanceRatesAggregator;
        if (ratesAggregator.containsKey(instanceName)) {
            instanceRatesAggregator = ratesAggregator.get(instanceName);
        } else {
            instanceRatesAggregator = new HashMap<String, HashMap<String, Object>>();
        }

        int loopCounter = App.getLoopCounter();

        String sendingMessage = "Instance " + instanceName + " is sending " + metrics.size()
                + " metrics to the metrics reporter during collection #" + loopCounter;
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

            postProcess(metric);

            Double currentValue = (Double) metric.get("value");
            if (currentValue.isNaN() || currentValue.isInfinite()) {
                continue;
            }

            String metricName = (String) metric.get("alias");
            String metricType = (String) metric.get("metric_type");
            String[] tags = Arrays.asList((String[]) metric.get("tags")).toArray(new String[0]);

            // StatsD doesn't support rate metrics so we need to have our own aggregator to compute rates
            if (!"gauge".equals(metricType)) {
                String key = generateId(metric);
                if (!instanceRatesAggregator.containsKey(key)) {
                    HashMap<String, Object> rateInfo = new HashMap<String, Object>();
                    rateInfo.put("ts", System.currentTimeMillis());
                    rateInfo.put("value", currentValue);
                    instanceRatesAggregator.put(key, rateInfo);
                    continue;
                }

                long oldTs = (Long) instanceRatesAggregator.get(key).get("ts");
                double oldValue = (Double) instanceRatesAggregator.get(key).get("value");

                long now = System.currentTimeMillis();
                double rate = 1000 * (currentValue - oldValue) / (now - oldTs);

                if (!Double.isNaN(rate) && !Double.isInfinite(rate)) {
                    sendMetricPoint(metricName, rate, tags);
                }

                instanceRatesAggregator.get(key).put("ts", now);
                instanceRatesAggregator.get(key).put("value", currentValue);
            } else { // The metric is a gauge
                sendMetricPoint(metricName, currentValue, tags);
            }
        }

        ratesAggregator.put(instanceName, instanceRatesAggregator);
    }


    void postProcess(HashMap<String, Object> metric) {
        if (metric.get("check_name").equals("cassandra")) {
            postProcessCassandra(metric);
        }
    }

    public void sendServiceCheck(String checkName, String status, String message, String hostname, String[] tags){
        this.incrementServiceCheckCount(checkName);

        this.doSendServiceCheck(checkName, status, message, hostname, tags);
    }

    private void postProcessCassandra(HashMap<String, Object> metric) {
        metric.put("alias", ((String) metric.get("alias")).replace("jmx.org.apache.", ""));
    }

    public void incrementServiceCheckCount(String checkName){
        int scCount = this.getServiceCheckCount(checkName);
        this.getServiceCheckCountMap().put(checkName, new Integer(scCount+1));
    }

    public int getServiceCheckCount(String checkName){
        Integer scCount = this.serviceCheckCount.get(checkName);
        return (scCount == null) ? 0 : scCount.intValue();
    }
    
    public void resetServiceCheckCount(String checkName){
        this.serviceCheckCount.put(checkName, new Integer(0));
    }

    protected HashMap<String, Integer> getServiceCheckCountMap(){
        return this.serviceCheckCount;
    }

    protected abstract void sendMetricPoint(String metricName, double value, String[] tags);

    protected abstract void doSendServiceCheck(String checkName, String status, String message, String hostname, String[] tags);

    public abstract void displayMetricReached();

    public abstract void displayNonMatchingAttributeName(JMXAttribute jmxAttribute);

    public abstract void displayInstanceName(Instance instance);

    public abstract void displayMatchingAttributeName(JMXAttribute jmxAttribute, int rank, int limit);

}
