package org.datadog.jmxfetch.reporter;

import com.fasterxml.jackson.jr.ob.JSON;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;

@Slf4j
public class JsonReporter extends Reporter {

  private List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();

  protected void sendMetricPoint(
      String metricType, String metricName, double value, String[] tags) {
    long currentTime = System.currentTimeMillis() / 1000L;
    List<Object> point = new ArrayList<Object>(2);
    point.add(currentTime);
    point.add(value);
    List<Object> points = new ArrayList<Object>(1);
    points.add(point);
    Map<String, Object> metric = new HashMap<String, Object>();
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
  public void doSendServiceCheck(
      String serviceCheckName, String status, String message, String[] tags) {
    log.debug("Displaying JSON output");
    Map<String, Object> sc = new HashMap<String, Object>();
    sc.put("check", serviceCheckName);
    sc.put("host_name", "default");
    sc.put("timestamp", System.currentTimeMillis() / 1000);
    sc.put("status", this.statusToServiceCheckStatusValue(status));
    sc.put("message", message);
    sc.put("tags", tags);

    Map<String, Object> aggregator = new HashMap<String, Object>();
    aggregator.put("metrics", metrics);
    List<Object> serviceChecks = new ArrayList<Object>();
    serviceChecks.add(sc);
    aggregator.put("service_checks", serviceChecks);
    Map<String, Object> serie = new HashMap<String, Object>();
    serie.put("aggregator", aggregator);
    List<Map<String, Object>> series = new ArrayList<Map<String, Object>>(1);
    series.add(serie);

    System.out.println("=== JSON ===");
    try {
      System.out.println(
          JSON.std
              .with(JSON.Feature.PRETTY_PRINT_OUTPUT, JSON.Feature.WRITE_NULL_PROPERTIES)
              .asString(series));
    } catch (IOException e) {
      log.error("Couln't produce JSON output");
    }
    metrics.clear();
  }

  public void displayMetricReached() {}

  public void displayMatchingAttributeName(JmxAttribute jmxAttribute, int rank, int limit) {}

  public void displayNonMatchingAttributeName(JmxAttribute jmxAttribute) {}

  public void displayInstanceName(Instance instance) {}
}
