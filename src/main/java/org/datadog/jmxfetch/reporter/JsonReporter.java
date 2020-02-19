package org.datadog.jmxfetch.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class JsonReporter extends Reporter {

    private List<HashMap<String, Object>> metrics = new ArrayList<HashMap<String, Object>>();

    protected void sendMetricPoint(
            String metricType, String metricName, double value, String[] tags) {
        long currentTime = System.currentTimeMillis() / 1000L;
        List<Object> point = new ArrayList<Object>(2);
        point.add(currentTime);
        point.add(value);
        List<Object> points = new ArrayList<Object>(1);
        points.add(point);
        HashMap<String, Object> metric = new HashMap<String, Object>();
        metric.put("host", "default");
        metric.put("interval", 0);
        metric.put("source_type_name", "JMX");
        metric.put("metric", metricName);
        metric.put("points", points);
        metric.put("tags", tags);
        metric.put("type", metricType);
        metrics.add(metric);
    }

    /** Use the service check callback to display the JSON. */
    public void doSendServiceCheck(String checkName, String status, String message, String[] tags) {
        HashMap<String, Object> aggregator = new HashMap<String, Object>();
        aggregator.put("metrics", metrics);
        HashMap<String, Object> serie = new HashMap<String, Object>();
        serie.put("aggregator", aggregator);
        List<Map<String, Object>> series = new ArrayList<Map<String, Object>>(1);
        series.add(serie);

        System.out.println("=== JSON ===");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(System.out, series);
        } catch (IOException e) {
            log.error("Couln't produce JSON output");
        }
    }

    public void displayMetricReached() {
    }

    public void displayMatchingAttributeName(JmxAttribute jmxAttribute, int rank, int limit) {
    }

    public void displayNonMatchingAttributeName(JmxAttribute jmxAttribute) {
    }

    public void displayInstanceName(Instance instance) {
    }
}
