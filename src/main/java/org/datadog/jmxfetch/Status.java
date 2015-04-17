package org.datadog.jmxfetch;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.apache.commons.io.FileUtils;

public class Status {

    public final static String STATUS_WARNING = "WARNING";
    public final static String STATUS_OK = "OK";
    public final static String STATUS_ERROR = "ERROR";
    private final static Logger LOGGER = Logger.getLogger(Status.class.getName());
    private final static String INITIALIZED_CHECKS = "initialized_checks";
    private final static String FAILED_CHECKS = "failed_checks";
    private HashMap<String, Object> instanceStats;
    private String statusFileLocation;
    private boolean isEnabled;

    public Status() {
        this(null);
    }

    public Status(String statusFileLocation) {
        configure(statusFileLocation);
    }

    void configure(String statusFileLocation) {
        this.statusFileLocation = statusFileLocation;
        this.isEnabled = this.statusFileLocation != null;
        this.instanceStats = new HashMap<String, Object>();
        this.clearStats();
    }

    private void clearStats() {
        instanceStats.put(INITIALIZED_CHECKS, new HashMap<String, Object>());
        instanceStats.put(FAILED_CHECKS, new HashMap<String, Object>());
    }

    public void addInstanceStats(String checkName, String instance, int metricCount, 
                                 int serviceCheckCount, String message, String status) {
        addStats(checkName, instance, metricCount, serviceCheckCount, message, 
                 status, INITIALIZED_CHECKS);
    }

    @SuppressWarnings("unchecked")
    private void addStats(String checkName, String instance, int metricCount, int serviceCheckCount, String message, String status, String key) {
        LinkedList<HashMap<String, Object>> checkStats;
        HashMap<String, Object> initializedChecks;
        initializedChecks = (HashMap<String, Object>) this.instanceStats.get(key);
        if (initializedChecks == null) {
            initializedChecks = new HashMap<String, Object>();
        }
        checkStats = (LinkedList<HashMap<String, Object>>) initializedChecks.get(checkName);
        if (checkStats == null) {
            checkStats = new LinkedList<HashMap<String, Object>>();
        }
        HashMap<String, Object> instStats = new HashMap<String, Object>();
        if (instance != null) {
            instStats.put("instance_name", instance);
        }
        if (metricCount != -1) {
            instStats.put("metric_count", metricCount);
        }
        if(serviceCheckCount != -1){
            instStats.put("service_check_count", serviceCheckCount);
        }
        instStats.put("message", message);
        instStats.put("status", status);
        checkStats.add(instStats);
        initializedChecks.put(checkName, checkStats);
        this.instanceStats.put(key, initializedChecks);
    }

    public void addInitFailedCheck(String checkName, String message, String status) {
        addStats(checkName, null, -1, -1, message, status, FAILED_CHECKS);
    }

    private String generateYaml() {
        Yaml yaml = new Yaml();
        HashMap<String, Object> status = new HashMap<String, Object>();
        status.put("timestamp", System.currentTimeMillis());
        status.put("checks", this.instanceStats);
        return yaml.dump(status);
    }

    public void flush() {
        if (isEnabled()) {
            String yaml = generateYaml();
            try {
                File f = new File(this.statusFileLocation);
                LOGGER.debug("Writing status to temp yaml file: " + f.getAbsolutePath());
                FileUtils.writeStringToFile(f, yaml);
            } catch (Exception e) {
                LOGGER.warn("Cannot write status to temp file: " + e.getMessage());
            }
        }
        this.clearStats();
    }

    public String getStatusFileLocation() {
        return statusFileLocation;
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}
