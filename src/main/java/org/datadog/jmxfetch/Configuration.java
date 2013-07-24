package org.datadog.jmxfetch;

import java.util.LinkedHashMap;

public class Configuration {
	
	LinkedHashMap<String, Object> conf;
	LinkedHashMap<String, Object> include;
	LinkedHashMap<String, Object> exclude;

	public Configuration(LinkedHashMap<String, Object> conf)
	{
		this.conf = conf;
		this.include = (LinkedHashMap<String, Object>)(conf.get("include"));
		this.exclude = (LinkedHashMap<String, Object>)(conf.get("exclude"));
		if (this.exclude == null)
		{
			this.exclude = new LinkedHashMap<String, Object>();
		}
	}
	
}
