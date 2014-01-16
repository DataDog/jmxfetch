package org.datadog.jmxfetch;

import java.util.LinkedHashMap;

public class Configuration {
    
    public LinkedHashMap<String, Object> conf;
    public LinkedHashMap<String, Object> include;
    public LinkedHashMap<String, Object> exclude;

    /**
     * A simple class to access configuration elements more easily
     */
    @SuppressWarnings("unchecked")
    public Configuration(LinkedHashMap<String, Object> conf)
    {
        this.conf = conf;
        this.include = (LinkedHashMap<String, Object>)(conf.get("include"));
        this.exclude = (LinkedHashMap<String, Object>)(conf.get("exclude"));
        if (this.exclude == null) {
            this.exclude = new LinkedHashMap<String, Object>();
        }
    }
    
    public String toString() {
    	return "include: " + this.include + " - exclude: " + this.exclude;
    }
    
}
