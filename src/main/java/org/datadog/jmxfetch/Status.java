package org.datadog.jmxfetch;

import com.fasterxml.jackson.jr.ob.JSON;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.util.MetadataHelper;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Status {

    public static final String STATUS_WARNING = "WARNING";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    private static final String INITIALIZED_CHECKS = "initialized_checks";
    private static final String FAILED_CHECKS = "failed_checks";
    private static final String API_STATUS_PATH = "agent/jmx/status";
    private Map<String, Object> instanceStats;
    private Map<String, Object> info;
    private String statusFileLocation;
    private HttpClient client;
    private boolean isEnabled;

    /** Default constructor. */
    public Status() {
        this(null);
    }

    /** Status constructor for remote configuration host. */
    public Status(String host, int port) {
        client = new HttpClient(host, port, false);
        configure(null, host, port);
    }

    /** status constructor for provided status file location. */
    public Status(String statusFileLocation) {
        configure(statusFileLocation, null, 0);
    }

    void configure(String statusFileLocation, String host, int port) {
        this.statusFileLocation = statusFileLocation;
        this.instanceStats = new HashMap<String, Object>();
        this.info = new HashMap<String, Object>();
        this.isEnabled = (this.statusFileLocation != null || this.client != null);
        this.clearStats();
        this.addInfo();
    }

    private void clearStats() {
        instanceStats.put(INITIALIZED_CHECKS, new HashMap<String, Object>());
        instanceStats.put(FAILED_CHECKS, new HashMap<String, Object>());
    }

    private void addInfo() {
        this.info.put("version", MetadataHelper.getVersion());
        this.info.put("runtime_version", System.getProperty("java.version"));
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
        List<Map<String, Object>> checkStats;
        Map<String, Object> initializedChecks;
        initializedChecks = (Map<String, Object>) this.instanceStats.get(key);
        if (initializedChecks == null) {
            initializedChecks = new HashMap<String, Object>();
        }
        checkStats = (List<Map<String, Object>>) initializedChecks.get(checkName);
        if (checkStats == null) {
            checkStats = new ArrayList<Map<String, Object>>();
        }
        Map<String, Object> instStats = new HashMap<String, Object>();
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
        Map<String, Object> status = new HashMap<String, Object>();
        status.put("info", this.info);
        status.put("timestamp", System.currentTimeMillis());
        status.put("checks", this.instanceStats);
        return new Yaml().dump(status);
    }

    private String generateJson() throws IOException {
        Map<String, Object> status = new HashMap<String, Object>();
        status.put("info", this.info);
        status.put("timestamp", System.currentTimeMillis());
        status.put("checks", this.instanceStats);
        return JSON.std.asString(status);
    }

    /** Flushes current status. */
    public void flush() {
        if (isEnabled()) {
            if (this.client != null) {
                try {
                    String json = generateJson();
                    HttpClient.HttpResponse response =
                            this.client.request("POST", json, API_STATUS_PATH);
                    if (!response.isResponse2xx()) {
                        log.debug("Problem submitting JSON status: " + json);
                    }
                } catch (Exception e) {
                    log.warn("Could not post status: " + e.getMessage());
                }
            } else {
                String yaml = generateYaml();
                try {
                    File statusFile = new File(this.statusFileLocation);
                    log.debug(
                            "Writing status to temp yaml file: " + statusFile.getAbsolutePath());
                    writeStringToFile(statusFile, yaml);
                } catch (Exception e) {
                    log.warn("Cannot write status to temp file: " + e.getMessage());
                }
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

    private static void writeStringToFile(File file, String string) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(out);
        try {
            bos.write(string.getBytes(Charset.forName("UTF-8")));
        } finally {
            bos.close();
            out.close();
        }
    }
}
