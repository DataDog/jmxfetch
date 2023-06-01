package org.datadog.jmxfetch;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JMXServerSupervisorClient {
    private final String host;
    private final int port;

    public JMXServerSupervisorClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setRmiHostname(String rmiHostname) throws IOException {
        sendPostRequestWithFormParams("/config", Collections.singletonMap("rmiHostname", rmiHostname));
    }

    private void sendPostRequestWithFormParams(String endpoint, Map<String, String> formParams) throws IOException {
        URL url = new URL("http://" + host + ":" + port + endpoint);
        log.info("Sending POST to {} ", url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        // Enable output and input streams for the connection
        con.setDoOutput(true);
        con.setDoInput(true);


        // Create the form parameter string
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : formParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        String formParamsString = sb.toString();

        // Set the content type to form-urlencoded
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        // Convert the form parameters to bytes
        byte[] postData = formParamsString.getBytes(StandardCharsets.UTF_8);

        // Set the content length of the request
        con.setRequestProperty("Content-Length", String.valueOf(postData.length));

        // Write the form parameters to the output stream
        try (DataOutputStream outputStream = new DataOutputStream(con.getOutputStream())) {
            outputStream.write(postData);
            outputStream.flush();
        }


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
