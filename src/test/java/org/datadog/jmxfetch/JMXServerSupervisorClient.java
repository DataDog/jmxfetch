package org.datadog.jmxfetch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JMXServerSupervisorClient {
    private final String host;
    private final int port;

    public JMXServerSupervisorClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeJMXServer(String rmiHostname) throws IOException {
        String endpoint = "/init";
        String jsonPayload = "{\"rmiHostname\": \""  + rmiHostname + "\"}";
        sendPostRequestWithPayload(endpoint, jsonPayload);
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

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            log.info("HTTP Resp: {}", response.toString());
        } else {
            log.warn("HTTP POST request failed with status code: {} err: {}", responseCode, response.toString());
        }
    }

}
