package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;


public abstract class Reporter {

    private final static Logger LOGGER = Logger.getLogger(App.class.getName());

    protected HashMap<String, HashMap<String, HashMap<String, Object>>> ratesAggregator = new HashMap<String, HashMap<String, HashMap<String, Object>>>();

    protected String _generateId(HashMap<String, Object> metric) {
        String key = (String) metric.get("alias");
        for (String tag : (String[]) metric.get("tags")) {
            key += tag;
        }
        return key;
    }

    public void clearRatesAggregator(String instance_name){
        ratesAggregator.put(instance_name, new HashMap<String, HashMap<String, Object>>());
    }

    public void sendMetrics(LinkedList<HashMap<String, Object>> metrics, String instance_name) {

        HashMap<String, HashMap<String, Object>> instanceRatesAggregator;
        if (ratesAggregator.containsKey(instance_name)) {
            instanceRatesAggregator = ratesAggregator.get(instance_name);
        } else {
            instanceRatesAggregator = new HashMap<String, HashMap<String, Object>>();
        }

        int loopCounter = App.getLoopCounter();

        if(loopCounter <= 5 || loopCounter % 10 == 0) {
            LOGGER.info("Instance " + instance_name + " is sending " + metrics.size() + " metrics to the metrics reporter during collection #" + loopCounter);
            if (loopCounter == 5) {
                LOGGER.info("Next collections will be logged only every 10 collections.");
            }
        } else {
            LOGGER.debug("Instance " + instance_name + " is sending " + metrics.size() + " metrics to the metrics reporter during collection #" + loopCounter);
        }

        for (HashMap<String, Object> m : metrics) {
            // We need to edit metrics for legacy reasons (rename metrics, etc)
            HashMap<String, Object> metric = _postprocess(m);

            Double current_value = (Double) metric.get("value");
            if (current_value.isNaN() || current_value.isInfinite()) {
                continue;
            }

            String metric_name = (String) metric.get("alias");
            String metric_type = (String) metric.get("metric_type");
            String[] tags = (String[]) metric.get("tags");

            // StatsD doesn't support rate metrics so we need to have our own aggregator to compute rates
            if(!metric_type.equals("gauge")) {
                String key = _generateId(metric);
                if (!instanceRatesAggregator.containsKey(key)) {
                    HashMap<String, Object> rate_info = new HashMap<String, Object>();
                    rate_info.put("ts", System.currentTimeMillis());
                    rate_info.put("value", current_value);
                    instanceRatesAggregator.put(key, rate_info);
                    continue;
                }

                long old_ts = (Long) instanceRatesAggregator.get(key).get("ts");
                double old_value = (Double) instanceRatesAggregator.get(key).get("value");

                long now = System.currentTimeMillis();
                double rate = 1000 * ((Double) current_value - old_value) / (now - old_ts);

                if(!Double.isNaN(rate) && !Double.isInfinite(rate)) {
                    _sendMetricPoint(metric_name, rate, tags);
                }

                instanceRatesAggregator.get(key).put("ts", now);
                instanceRatesAggregator.get(key).put("value", current_value);
            }

            else { // The metric is a gauge
                _sendMetricPoint(metric_name, current_value, tags);
            }
        }

        ratesAggregator.put(instance_name, instanceRatesAggregator);
    }


    protected HashMap<String, Object> _postprocess(HashMap<String, Object> metric) {
        if (metric.get("check_name").equals("cassandra")) {
            return _postProcessCassandra(metric);
        }
        return metric;
    }

    private HashMap<String, Object> _postProcessCassandra(HashMap<String, Object> metric) {
        metric.put("alias", ((String) metric.get("alias")).replace("jmx.org.apache.", ""));
        return metric;
    }


    protected abstract void _sendMetricPoint(String metricName, double value, String[] tags);

    public abstract void displayMetricReached();

    public abstract void displayNonMatchingAttributeName(JMXAttribute jmxAttribute);

    public abstract void displayInstanceName(Instance instance);

    public abstract void displayMatchingAttributeName(JMXAttribute jmxAttribute, int rank, int limit);

}
