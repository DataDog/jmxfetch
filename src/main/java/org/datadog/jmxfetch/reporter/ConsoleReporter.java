package org.datadog.jmxfetch.reporter;

import com.google.common.base.Joiner;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ConsoleReporter extends Reporter {

    private List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> serviceChecks = new ArrayList<Map<String, Object>>();

    @Override
    protected void sendMetricPoint(
            String metricType, String metricName, double value, String[] tags) {
        String tagString = "[" + Joiner.on(",").join(tags) + "]";
        log.info(
                metricName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + value);

        Map<String, Object> metric = new HashMap<String, Object>();
        metric.put("name", metricName);
        metric.put("value", value);
        metric.put("tags", tags);
        metric.put("type", metricType);
        metrics.add(metric);
    }

    /** Returns list of metrics to report and clears stored metric map. */
    public List<Map<String, Object>> getMetrics() {
        List<Map<String, Object>> returnedMetrics =
                new ArrayList<Map<String, Object>>(metrics.size());
        for (Map<String, Object> map : metrics) {
            returnedMetrics.add(new HashMap<String, Object>(map));
        }
        metrics.clear();
        return returnedMetrics;
    }

    /** Adds service check to report on. */
    public void doSendServiceCheck(
            String serviceCheckName, String status, String message, String[] tags) {
        String tagString = "";
        if (tags != null && tags.length > 0) {
            tagString = "[" + Joiner.on(",").join(tags) + "]";
        }
        log.info(serviceCheckName + tagString + " - " + System.currentTimeMillis() / 1000
                 + " = " + status);

        Map<String, Object> sc = new HashMap<String, Object>();
        sc.put("name", serviceCheckName);
        sc.put("status", status);
        sc.put("message", message);
        sc.put("tags", tags);
        serviceChecks.add(sc);
    }

    /** Returns list of service checks to report and clears stored service check map.. */
    public List<Map<String, Object>> getServiceChecks() {
        List<Map<String, Object>> returnedServiceChecks =
                new ArrayList<Map<String, Object>>(serviceChecks.size());
        for (Map<String, Object> map : serviceChecks) {
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
