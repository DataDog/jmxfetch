package org.datadog.jmxfetch;

import java.io.File;
import java.util.HashMap;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

public class Status {
	
	HashMap<String, HashMap<String, Object>> instanceStats;
	
	private final static Logger LOGGER = Logger.getLogger(Status.class.getName());
	private String status_file_location;
	
	public final static String STATUS_WARNING = "WARNING";
	public final static String STATUS_OK = "OK";
	public final static String STATUS_ERROR = "ERROR";
	
	public Status(String status_file_location) {
		this._clearStats();
		this.status_file_location = status_file_location;
		
	}
	
	private void _clearStats() {
		this.instanceStats = new HashMap<String, HashMap<String, Object>>();
	}
	
	public void addInstanceStats(String instance, int metricCount, String message, String status) {
		HashMap<String, Object> stats = new HashMap<String, Object>();
		stats.put("metric_count", metricCount);
		stats.put("message", message);
		stats.put("status", status);
		this.instanceStats.put(instance, stats);
	}
	
	private String _generateYaml() {
		Yaml yaml = new Yaml();
		HashMap<String, Object> status = new HashMap<String, Object>();
		status.put("timestamp", System.currentTimeMillis());
		status.put("instances", this.instanceStats);
	    return yaml.dump(status);
		
	}
	
	public void flush() {
		
		String yaml = _generateYaml();
		try {
			File f = new File(this.status_file_location);
			LOGGER.fine("Writing status to temp yaml file: " + f.getAbsolutePath());
			FileUtils.writeStringToFile(f, yaml);
			
		} catch (Exception e) {
			LOGGER.warning("Cannot write status to temp file: " + e.getMessage());
		} 
		this._clearStats();
		
	}

}
