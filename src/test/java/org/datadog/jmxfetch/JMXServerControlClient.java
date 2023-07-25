package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class JMXServerControlClient extends JMXServerClient{
    public JMXServerControlClient(String host, int port) {
        super(host, port);
    }

    public List<String> getMBeans(String domain) throws IOException {
        String endpoint = "/beans/" + domain;
        String response = sendGetRequest(endpoint);
        return Arrays.asList(response.toString().split(","));

    }

    public void createMBeans(String domain, int numDesiredBeans) throws IOException {
        String endpoint = "/beans/" + domain;
        String jsonPayload = "{\"numDesiredBeans\": " + numDesiredBeans + "}";
        sendPostRequestWithPayload(endpoint, jsonPayload);
    }

    public void jmxCutNetwork() throws IOException {
        sendPostRequestWithPayload("/cutNetwork", "");
    }

    public void jmxRestoreNetwork() throws IOException {
        sendPostRequestWithPayload("/restoreNetwork", "");
    }
}