package org.datadog.jmxfetch;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class StatsdMetricReporter extends MetricReporter{
	
	private final StatsDClient STATSD_CLIENT;
	
	public StatsdMetricReporter(int statsd_port) {
		this.STATSD_CLIENT = new NonBlockingStatsDClient(null, "localhost", statsd_port, new String[] {});		
	}
	
	protected void _sendMetricPoint(String metricName, double value, String[] tags) {
		STATSD_CLIENT.gauge(metricName, value, tags);
	}
	
}
