package org.datadog.jmxfetch;

import java.util.LinkedHashMap;

public class Configuration {

    private LinkedHashMap<String, Object> conf;
    private Filter include;
    private Filter exclude;

    /**
     * A simple class to access configuration elements more easily
     */
    public Configuration(LinkedHashMap<String, Object> conf) {
        this.conf = conf;
        this.include = new Filter(conf.get("include"));
        this.exclude = new Filter(conf.get("exclude"));
    }

    public LinkedHashMap<String, Object> getConf() {
        return conf;
    }

    public Filter getInclude() {
        return include;
    }

    public Filter getExclude() {
        return exclude;
    }

    public String toString() {
        return "include: " + this.include + " - exclude: " + this.exclude;
    }

}
