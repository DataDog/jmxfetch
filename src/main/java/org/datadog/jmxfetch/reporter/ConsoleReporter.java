package org.datadog.jmxfetch.reporter;

import com.google.common.base.Joiner;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;

import java.util.HashMap;
import java.util.LinkedList;

@Slf4j
public class ConsoleReporter extends Reporter {

    private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
    private LinkedList<HashMap<String, Object>> serviceChecks =
            new LinkedList<HashMap<String, Object>>();

    @Override
    protected void sendMetricPoint(
            String metricType, String metricName, double value, String[] tags) {
        String tagString = "[" + Joiner.on(",").join(tags) + "]";
        log.info(
                metricName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + value);

        HashMap<String, Object> metric = new HashMap<String, Object>();
        metric.put("name", metricName);
        metric.put("value", value);
        metric.put("tags", tags);
        metric.put("type", metricType);
        metrics.add(metric);
    }

    /** Returns list of metrics to report and clears stored metric map. */
    public LinkedList<HashMap<String, Object>> getMetrics() {
        LinkedList<HashMap<String, Object>> returnedMetrics =
                new LinkedList<HashMap<String, Object>>();
        for (HashMap<String, Object> map : metrics) {
            returnedMetrics.add(new HashMap<String, Object>(map));
        }
        metrics.clear();
        return returnedMetrics;
    }

    /** Adds service check to report on. */
    public void doSendServiceCheck(String checkName, String status, String message, String[] tags) {
        String tagString = "";
        if (tags != null && tags.length > 0) {
            tagString = "[" + Joiner.on(",").join(tags) + "]";
        }
        log.info(
                checkName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + status);

        HashMap<String, Object> sc = new HashMap<String, Object>();
        sc.put("name", checkName);
        sc.put("status", status);
        sc.put("message", message);
        sc.put("tags", tags);
        serviceChecks.add(sc);
    }

    /** Returns list of service checks to report and clears stored service check map.. */
    public LinkedList<HashMap<String, Object>> getServiceChecks() {
        LinkedList<HashMap<String, Object>> returnedServiceChecks =
                new LinkedList<HashMap<String, Object>>();
        for (HashMap<String, Object> map : serviceChecks) {
            returnedServiceChecks.add(new HashMap<String, Object>(map));
        }
        serviceChecks.clear();
        return returnedServiceChecks;
    }

    @Override
    public void displayMetricReached() {
        log.info(
                "       ------- METRIC LIMIT REACHED: ATTRIBUTES BELOW WON'T BE COLLECTED -------");
    }

    @Override
    public void displayMatchingAttributeName(JmxAttribute jmxAttribute, int rank, int limit) {
        log.info("       Matching: " + rank + "/" + limit + ". " + jmxAttribute);
    }

    @Override
    public void displayNonMatchingAttributeName(JmxAttribute jmxAttribute) {
        log.info("       Not Matching: " + jmxAttribute);
    }

    @Override
    public void displayInstanceName(Instance instance) {
        log.info("#####################################");
        log.info("Instance: " + instance);
        log.info("#####################################");
    }
}
