package org.datadog.jmxfetch;

import java.util.LinkedHashMap;

public class Configuration {

    private LinkedHashMap<String, Object> conf;
    private LinkedHashMap<String, Object> include;
    private LinkedHashMap<String, Object> exclude;

    /**
     * A simple class to access configuration elements more easily
     */
    @SuppressWarnings("unchecked")
    public Configuration(LinkedHashMap<String, Object> conf) {
        this.conf = conf;
        this.include = (LinkedHashMap<String, Object>) (conf.get("include"));
        this.exclude = (LinkedHashMap<String, Object>) (conf.get("exclude"));
        if (this.exclude == null) {
            this.exclude = new LinkedHashMap<String, Object>();
        }
    }

    public LinkedHashMap<String, Object> getConf() {
        return conf;
    }

    public LinkedHashMap<String, Object> getInclude() {
        return include;
    }

    public LinkedHashMap<String, Object> getExclude() {
        return exclude;
    }

    public String toString() {
        return "include: " + this.include + " - exclude: " + this.exclude;
    }

}
