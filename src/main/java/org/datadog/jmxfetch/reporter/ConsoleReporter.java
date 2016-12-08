package org.datadog.jmxfetch.reporter;

import java.util.HashMap;
import java.util.LinkedList;

import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JMXAttribute;

import com.google.common.base.Joiner;

public class ConsoleReporter extends Reporter {

    private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
    private LinkedList<HashMap<String, Object>> serviceChecks = new LinkedList<HashMap<String, Object>>();

    @Override
    protected void sendMetricPoint(String metricType, String metricName, double value, String[] tags) {
        String tagString = "[" + Joiner.on(",").join(tags) + "]";
        System.out.println(metricName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + value);

        HashMap<String, Object> m = new HashMap<String, Object>();
        m.put("name", metricName);
        m.put("value", value);
        m.put("tags", tags);
        m.put("type", metricType);
        metrics.add(m);
    }

    public LinkedList<HashMap<String, Object>> getMetrics() {
        LinkedList<HashMap<String, Object>> returnedMetrics = new LinkedList<HashMap<String, Object>>();
        for (HashMap<String, Object> map : metrics) {
            returnedMetrics.add(new HashMap<String, Object>(map));
        }
        metrics.clear();
        return returnedMetrics;
    }

    public void doSendServiceCheck(String checkName, String status, String message, String[] tags) {
        String tagString = "";
        if (tags != null && tags.length > 0) {
            tagString = "[" + Joiner.on(",").join(tags) + "]";
        }
        System.out.println(checkName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + status);

        HashMap<String, Object> sc = new HashMap<String, Object>();
        sc.put("name", checkName);
        sc.put("status", status);
        sc.put("message", message);
        sc.put("tags", tags);
        serviceChecks.add(sc);
    }

    public LinkedList<HashMap<String, Object>> getServiceChecks() {
        LinkedList<HashMap<String, Object>> returnedServiceChecks = new LinkedList<HashMap<String, Object>>();
        for (HashMap<String, Object> map : serviceChecks) {
            returnedServiceChecks.add(new HashMap<String, Object>(map));
        }
        serviceChecks.clear();
        return returnedServiceChecks;
    }

    @Override
    public void displayMetricReached() {
        System.out.println("\n\n\n       ------- METRIC LIMIT REACHED: ATTRIBUTES BELOW WON'T BE COLLECTED -------\n\n\n");
    }

    @Override
    public void displayMatchingAttributeName(JMXAttribute jmxAttribute, int rank, int limit) {
        System.out.println("       Matching: " + rank + "/" + limit + ". " + jmxAttribute);
    }

    @Override
    public void displayNonMatchingAttributeName(JMXAttribute jmxAttribute) {
        System.out.println("       Not Matching: " + jmxAttribute);
    }

    @Override
    public void displayInstanceName(Instance instance) {
        System.out.println("\n#####################################");
        System.out.println("Instance: " + instance);
        System.out.println("#####################################\n");
    }

}
