package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.LinkedList;

public class ConsoleReporter extends Reporter{

    private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();

    @Override
    protected void _sendMetricPoint(String metricName, double value, String[] tags) {
        String tagString = "[" + join(", ", tags) + "]";
        System.out.println(metricName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + value);

        HashMap<String, Object> m = new HashMap<String, Object>();

        m.put("name", metricName);
        m.put("value", value);
        m.put("tags", tags);
        metrics.add(m);

    }


    public LinkedList<HashMap<String, Object>> getMetrics() {
        LinkedList<HashMap<String, Object>> returned_metrics = metrics;
        metrics = new LinkedList<HashMap<String, Object>>();
        return returned_metrics;
    }

    public String join (String delim, String ... data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(data[i]);
            if (i >= data.length-1) {break;}
            sb.append(delim);
        }
        return sb.toString();
    }

    @Override
    public void displayMetricReached() {
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("------- METRIC LIMIT REACHED: ATTRIBUTES BELOW WON'T BE COLLECTED -------");    
        System.out.println();
        System.out.println();
        System.out.println();
    }

    @Override
    public void displayMatchingAttributeName(JMXAttribute jmxAttribute, int rank) {
        System.out.println("       Matching: " + rank + ". " + jmxAttribute);

    }

    @Override
    public void displayNonMatchingAttributeName(JMXAttribute jmxAttribute) {
        System.out.println("       Not Matching: " + jmxAttribute);
    }

    @Override
    public void displayInstanceName(Instance instance) {
        System.out.println("Instance: " + instance);

    }

}
