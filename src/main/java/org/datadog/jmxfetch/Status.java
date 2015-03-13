package org.datadog.jmxfetch;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

public class Status {

    public final static int STATUS_WARNING = 1;
    public final static int STATUS_OK = 0;
    public final static int STATUS_ERROR = 2;
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

    public void addInstanceStats(String checkName, String instance, int metricCount, String message, int status) {
        addStats(checkName, instance, metricCount, message, status, INITIALIZED_CHECKS);
    }

    @SuppressWarnings("unchecked")
    private void addStats(String checkName, String instance, int metricCount, String message, int status, String key) {
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
        instStats.put("message", message);
        instStats.put("status", status);
        checkStats.add(instStats);
        initializedChecks.put(checkName, checkStats);
        this.instanceStats.put(key, initializedChecks);
    }

    public void addInitFailedCheck(String checkName, String message, int status) {
        addStats(checkName, null, -1, message, status, FAILED_CHECKS);
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
