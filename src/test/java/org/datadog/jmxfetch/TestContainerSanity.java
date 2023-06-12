package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.Network.NetworkImpl;
import org.testcontainers.containers.wait.strategy.Wait;

@Slf4j
public class TestContainerSanity {

    private static boolean isHttpOk(String host, int port) throws IOException {
        String url = "http://" + host + ":" + port;
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(500); // fail fast, only half a second to respond

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Test
    public void testSimple() throws Exception {
        GenericContainer<?> cont = new GenericContainer<>("strm/helloworld-http")
            .withExposedPorts(80);
            //.waitingFor(Wait.forHttp("/").forPort(80).forStatusCode(200));
        Thread.sleep(1000);
        NetworkImpl n = (NetworkImpl) cont.getNetwork();
        log.info("Network mode: {}, id: {}, driver: {}, name: {}", cont.getNetworkMode(), n.getId(), n.getDriver(), n.getName());
        cont.waitingFor(Wait.forListeningPort());
        assertTrue(isHttpOk(cont.getHost(), cont.getMappedPort(80)));

        cont.close();
    }

}
