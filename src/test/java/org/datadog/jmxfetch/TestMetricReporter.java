package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.LinkedList;

public class TestMetricReporter extends MetricReporter{
    
    private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();

    @Override
    protected void _sendMetricPoint(String metricName, double value,
            String[] tags) {
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
    
}
