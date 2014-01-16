package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.LinkedList;

public class ConsoleReporter extends MetricReporter{

	private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();

	@Override
	protected void _sendMetricPoint(String metricName, double value, String[] tags) {
		String tagString = "[" + join(", ", tags) + "]";
		System.out.println(metricName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + value);

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

}
