package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

@Slf4j
public class JMXServerControlClient {
    private final String host;
    private final int port;

    public JMXServerControlClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public List<String> getMBeans(String domain) throws IOException {
        String endpoint = "/beans/" + domain;
        return sendGetRequest(endpoint);
    }

    public void createMBeans(String domain, int numDesiredBeans) throws IOException {
        String endpoint = "/beans/" + domain;
        String jsonPayload = "{\"numDesiredBeans\": " + numDesiredBeans + "}";
        sendPostRequestWithPayload(endpoint, jsonPayload);
    }

    public void jmxCutNetwork() throws IOException {
        sendPostRequest("/cutNetwork");
    }

    public void jmxRestoreNetwork() throws IOException {
        sendPostRequest("/restoreNetwork");
    }

    private List<String> sendGetRequest(String endpoint) throws IOException {
        URL url = new URL("http://" + host + ":" + port + endpoint);
        log.info("Sending GET to {} ", url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            // currently tied to the only GET request made from this client, list the mbeans for a domain
            return Arrays.asList(response.toString().split(","));
        } else {
            log.warn("HTTP GET request failed with status code: {}", responseCode);
            return Collections.emptyList();
        }
    }

    private void sendPostRequestWithPayload(String endpoint, String jsonPayload) throws IOException {
        URL url = new URL("http://" + host + ":" + port + endpoint);
        log.info("Sending POST to {} with payload {}", url, jsonPayload);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            log.info("HTTP Resp: {}", response.toString());
        } else {
            log.warn("HTTP POST request failed with status code: {}", responseCode);
        }
    }

    private void sendPostRequest(String endpoint) throws IOException {
        URL url = new URL("http://" + host + ":" + port + endpoint);
        log.info("Sending POST to {} ", url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            log.info("HTTP Resp: {}", response.toString());
        } else {
            log.warn("HTTP POST request failed with status code: {}", responseCode);
        }
    }
}