package org.datadog.jmxfetch;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

public class Status {
	
	HashMap<String, HashMap<String, Object>> instanceStats;
	
	private final static Logger LOGGER = Logger.getLogger(Status.class.getName());
	
	public Status() {
		this._clearStats();
		
	}
	
	private void _clearStats() {
		this.instanceStats = new HashMap<String, HashMap<String, Object>>();
	}
	
	public void addInstanceStats(String instance, int metricCount, String message) {
		HashMap<String, Object> stats = new HashMap<String, Object>();
		stats.put("metric_count", metricCount);
		stats.put("message", message);
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
		LOGGER.fine("Writing status to temp yaml file");
		String yaml = _generateYaml();
		try {
			String path;
			path = Status.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			path = path.substring(0, path.lastIndexOf('/')+1);
			File root = new File(path);
			File f = new File(root, "jmx_status.yaml");
			FileUtils.writeStringToFile(f, yaml);
			
		} catch (Exception e) {
			LOGGER.warning("Cannot write status to temp file: " + e.getMessage());
		} 
		this._clearStats();
		
	}

}
