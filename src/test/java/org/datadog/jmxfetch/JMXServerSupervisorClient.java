package org.datadog.jmxfetch;

import java.io.IOException;

public class JMXServerSupervisorClient extends JMXServerClient {
    public JMXServerSupervisorClient(String host, int port) {
        super(host, port);
    }

    public void initializeJMXServer(String rmiHostname) throws IOException {
        String endpoint = "/init";
        String jsonPayload = "{\"rmiHostname\": \""  + rmiHostname + "\"}";
        sendPostRequestWithPayload(endpoint, jsonPayload);
    }
}
