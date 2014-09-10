package org.datadog.jmxfetch;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

public class Status {

    HashMap<String, Object> instanceStats;

    private static volatile Status _instance = null;

    private final static Logger LOGGER = Logger.getLogger(Status.class.getName());
    private String status_file_location;
    private boolean isEnabled;

    public final static String STATUS_WARNING = "WARNING";
    public final static String STATUS_OK = "OK";
    public final static String STATUS_ERROR = "ERROR";
    private final static String INITIALIZED_CHECKS = "initialized_checks";
    private final static String FAILED_CHECKS = "failed_checks";

    private Status() {
        this.configure(null);
    }

    public static Status getInstance() {
        if (_instance == null) {
            synchronized (Status .class){
                if (_instance == null) {
                    _instance = new Status ();
                }
            }
        }
        return _instance;
    }

    public void configure(String status_file_location) {
        this.status_file_location = status_file_location;
        this.isEnabled = this.status_file_location != null;
        this.instanceStats = new HashMap<String, Object>();
        this._clearStats();

    }

    private void _clearStats() {
        this.instanceStats.put(INITIALIZED_CHECKS,new HashMap<String, Object>());
    }

    public void addInstanceStats(String checkName, String instance, int metricCount, String message, String status) {
        addStats(checkName, instance, metricCount, message, status, INITIALIZED_CHECKS);
     }
    
    @SuppressWarnings("unchecked")
    private void addStats(String checkName, String instance, int metricCount, String message, String status, String key) {
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
        
    public void addInitFailedCheck(String checkName, String message, String status) {
        addStats(checkName, null, -1, message, status, FAILED_CHECKS);        
    }

    private String _generateYaml() {
        Yaml yaml = new Yaml();
        HashMap<String, Object> status = new HashMap<String, Object>();
        status.put("timestamp", System.currentTimeMillis());
        status.put("checks", this.instanceStats);
        return yaml.dump(status);

    }

    public void deleteStatus() {
        if(this.isEnabled) {
            try {
                File f = new File(this.status_file_location);
                LOGGER.info("Deleting status file");
                if(f.delete()) {
                    LOGGER.info("Status file properly deleted");
                } else {
                    LOGGER.warn("Couldn't delete status file");
                }
            } catch (Exception e) {
                LOGGER.warn("Couldn't delete status file", e);
            }
        }

    }

    public void flush() {
        if(this.isEnabled) {
            String yaml = _generateYaml();
            try {
                File f = new File(this.status_file_location);
                LOGGER.debug("Writing status to temp yaml file: " + f.getAbsolutePath());
                FileUtils.writeStringToFile(f, yaml);

            } catch (Exception e) {
                LOGGER.warn("Cannot write status to temp file: " + e.getMessage());
            } 
            this._clearStats();
        }

    }

}
