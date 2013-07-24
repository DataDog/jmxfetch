package org.datadog.jmxfetch;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class MetricReporter {
	
	private final static Logger LOGGER = Logger.getLogger(App.class.getName()); 
	private StatsDClient statsd_client;
	private HashMap<String, HashMap<String, Object>> rates_aggregator = new HashMap<String, HashMap<String, Object>>();
	private int loop_counter;
	
	public MetricReporter(int statsd_port) {
		this.statsd_client = new NonBlockingStatsDClient(null, "localhost", statsd_port, new String[] {});
		this.loop_counter = 0;
		
	}
	
	private String generate_id(HashMap<String, Object> m)
	{
		String key = (String) m.get("alias");
		for (String tag : (String[]) m.get("tags"))
		{
			key += tag;
		}
		return key;
	}

	public void sendMetrics(LinkedList<HashMap<String, Object>> metrics) {
		this.loop_counter++;
		
		if(this.loop_counter <= 5 || this.loop_counter % 10 == 0)
		{
			LOGGER.info("Collection #" + this.loop_counter + " is sending " + metrics.size() + " metrics to the dogstatsd server");
			if (this.loop_counter == 5)
			{
				LOGGER.info("Next collections will be logged only every 10 collections.");
			}
		}
		else
		{
			LOGGER.fine("Collection #" + this.loop_counter + " is sending " + metrics.size() + " metrics to the dogstatsd server");
		}
		
		for (HashMap<String, Object> m : metrics)
		{

			// We need to edit metrics for legacy reasons (rename metrics, etc)
			HashMap<String, Object> metric = postprocess(m);
			
			// StatsD doesn't support rate metrics so we need to have our own agregator to compute rates
			if(!metric.get("metric_type").equals("gauge"))
			{
				String key = generate_id(metric);
				if (!rates_aggregator.containsKey(key))
				{
					HashMap<String, Object> rate_info = new HashMap<String, Object>();
					rate_info.put("ts", System.currentTimeMillis());
					rate_info.put("value", metric.get("value"));
					rates_aggregator.put(key, rate_info);
					continue;
				}
				
				long old_ts = (Long) rates_aggregator.get(key).get("ts");
				double old_value = (Double) rates_aggregator.get(key).get("value");
				
				long now = System.currentTimeMillis();
				double rate = 1000 * ((Double) metric.get("value") - old_value) / (now - old_ts);
				
				statsd_client.gauge((String) metric.get("alias"), rate, (String[]) metric.get("tags"));
				
				rates_aggregator.get(key).put("ts", now);
				rates_aggregator.get(key).put("value", metric.get("value"));
			}
			else {
				statsd_client.gauge((String) metric.get("alias"), (Double) metric.get("value"), (String[]) metric.get("tags"));
			}
			
		}
		
		
	}

	private HashMap<String, Object> postProcessCassandra(HashMap<String, Object> m)
	{
		m.put("alias", ((String) m.get("alias")).replace("jmx.org.apache.", ""));
		return m;
	}
	

	private HashMap<String, Object> postprocess(HashMap<String, Object> m) {
		
		if (m.get("check_name").equals("cassandra"))
		{
				return postProcessCassandra(m);
		}
		
		return m;
	}

	


}
