package org.datadog.jmxfetch;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.lang.System;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import com.google.gson.Gson;

public class Status {

    public final static String STATUS_WARNING = "WARNING";
    public final static String STATUS_OK = "OK";
    public final static String STATUS_ERROR = "ERROR";
    private final static Logger LOGGER = Logger.getLogger(Status.class.getName());
    private final static String INITIALIZED_CHECKS = "initialized_checks";
    private final static String FAILED_CHECKS = "failed_checks";
    private HashMap<String, Object> instanceStats;
    private TrustManager[] dummyTrustManager;
    private SSLContext sc;
    private String statusFileLocation;
    private boolean isEnabled;
    private String token;

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
        try {
            this.token = System.getenv("SESSION_TOKEN");
            dummyTrustManager = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
            };
            sc = SSLContext.getInstance("SSL");
            sc.init(null, this.dummyTrustManager, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            LOGGER.debug("session token unavailable - not setting");
            this.token = "";
        }
        this.isEnabled = (this.statusFileLocation != null || this.token != "");
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

    private boolean postRequest(String body, int port) {
        int responseCode = 0;
        try {
            String url = "https://localhost:" + port + "/agent/jmxstatus";

            URL uri = new URL(url);
            HttpsURLConnection con = (HttpsURLConnection) uri.openConnection();

            //add reuqest header
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer "+ this.token);

            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(body);
            wr.flush();
            wr.close();

            responseCode = con.getResponseCode();

        } catch (Exception e) {
            LOGGER.info("problem creating http request: " + e.toString());
        }
        return (responseCode >= 200 && responseCode < 300);
    }

    public void flush(int port) {
        if (isEnabled()) {
            if (port > 0) {
                String json = generateJson();
                try {
                    if (!this.postRequest(json, port)) {
                        LOGGER.debug("Problem submitting JSON status: " + json);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not post status: " + e.getMessage());
                }
            } else {
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
