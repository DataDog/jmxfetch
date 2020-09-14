package org.datadog.jmxfetch;


import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.System;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;


public class Status {

    public static final String STATUS_WARNING = "WARNING";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    private static final Logger LOGGER = Logger.getLogger(Status.class.getName());
    private static final String INITIALIZED_CHECKS = "initialized_checks";
    private static final String FAILED_CHECKS = "failed_checks";
    private HashMap<String, Object> instanceStats;
    private String statusFileLocation;
    private boolean isEnabled;

    /** Default constructor. */
    public Status() {
        this(null);
    }

    public Status(String statusFileLocation) {
        configure(statusFileLocation);
    }

    void configure(String statusFileLocation) {
        this.statusFileLocation = statusFileLocation;
        this.instanceStats = new HashMap<String, Object>();
        this.isEnabled = (this.statusFileLocation != null);
        this.clearStats();
    }

    private void clearStats() {
        instanceStats.put(INITIALIZED_CHECKS, new HashMap<String, Object>());
        instanceStats.put(FAILED_CHECKS, new HashMap<String, Object>());
    }

    /** Adds instance stats to the status. */
    public void addInstanceStats(
            String checkName,
            String instance,
            int metricCount,
            int serviceCheckCount,
            String message,
            String status) {
        addStats(
                checkName,
                instance,
                metricCount,
                serviceCheckCount,
                message,
                status,
                INITIALIZED_CHECKS);
    }

    @SuppressWarnings("unchecked")
    private void addStats(
            String checkName,
            String instance,
            int metricCount,
            int serviceCheckCount,
            String message,
            String status,
            String key) {
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
        if (serviceCheckCount != -1) {
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

    private String generateJson() {
        Gson gson = new Gson();
        HashMap<String, Object> status = new HashMap<String, Object>();
        status.put("timestamp", System.currentTimeMillis());
        status.put("checks", this.instanceStats);
        return gson.toJson(status);
    }

    /** flush the status. */
    public void flush() {
        if (isEnabled()) {
            String yaml = generateYaml();
            try {
                File statusFile = new File(this.statusFileLocation);
                LOGGER.debug(
                        "Writing status to temp yaml file: " + statusFile.getAbsolutePath());
                FileUtils.writeStringToFile(statusFile, yaml);
            } catch (Exception e) {
                LOGGER.warn("Cannot write status to temp file: " + e.getMessage());
            }
        }
        this.clearStats();
    }

    public String getStatusFileLocation() {
        return statusFileLocation;
    }

    /** Described if the status is actually configured and enabled. */
    public boolean isEnabled() {
        return isEnabled;
    }
}
