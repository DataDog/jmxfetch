package org.datadog.jmxfetch;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class MetricReporter {
	
	private final static Logger LOGGER = Logger.getLogger(App.class.getName()); 
	private final StatsDClient STATSD_CLIENT;
	private HashMap<String, HashMap<String, Object>> ratesAggregator = new HashMap<String, HashMap<String, Object>>();

	public MetricReporter(int statsd_port) {
		this.STATSD_CLIENT = new NonBlockingStatsDClient(null, "localhost", statsd_port, new String[] {});		
	}
	
	private String _generateId(HashMap<String, Object> metric) {
		String key = (String) metric.get("alias");
		for (String tag : (String[]) metric.get("tags")) {
			key += tag;
		}
		return key;
	}

	public void sendMetrics(LinkedList<HashMap<String, Object>> metrics, String instance_name) {
		
		int loopCounter = App.getLoopCounter();
		
		if(loopCounter <= 5 || loopCounter % 10 == 0) {
			LOGGER.info("Instance " + instance_name + " is sending " + metrics.size() + " metrics to the dogstatsd server during collection #" + loopCounter);
			if (loopCounter == 5) {
				LOGGER.info("Next collections will be logged only every 10 collections.");
			}
		} else {
			LOGGER.fine("Instance " + instance_name + " is sending " + metrics.size() + " metrics to the dogstatsd server during collection #" + loopCounter);
		}
		
		for (HashMap<String, Object> m : metrics) {

			// We need to edit metrics for legacy reasons (rename metrics, etc)
			HashMap<String, Object> metric = _postprocess(m);
			
			// StatsD doesn't support rate metrics so we need to have our own agregator to compute rates
			if(!metric.get("metric_type").equals("gauge")) {
				String key = _generateId(metric);
				if (!ratesAggregator.containsKey(key)) {
					HashMap<String, Object> rate_info = new HashMap<String, Object>();
					rate_info.put("ts", System.currentTimeMillis());
					rate_info.put("value", metric.get("value"));
					ratesAggregator.put(key, rate_info);
					continue;
				}
				
				long old_ts = (Long) ratesAggregator.get(key).get("ts");
				double old_value = (Double) ratesAggregator.get(key).get("value");
				
				long now = System.currentTimeMillis();
				double rate = 1000 * ((Double) metric.get("value") - old_value) / (now - old_ts);
				
				STATSD_CLIENT.gauge((String) metric.get("alias"), rate, (String[]) metric.get("tags"));
				
				ratesAggregator.get(key).put("ts", now);
				ratesAggregator.get(key).put("value", metric.get("value"));
			}
			else {
				STATSD_CLIENT.gauge((String) metric.get("alias"), (Double) metric.get("value"), (String[]) metric.get("tags"));
			}
		}
	}

	private HashMap<String, Object> _postProcessCassandra(HashMap<String, Object> metric) {
		metric.put("alias", ((String) metric.get("alias")).replace("jmx.org.apache.", ""));
		return metric;
	}
	

	private HashMap<String, Object> _postprocess(HashMap<String, Object> metric) {
		if (metric.get("check_name").equals("cassandra")) {
				return _postProcessCassandra(metric);
		}
		return metric;
	}

	


}
