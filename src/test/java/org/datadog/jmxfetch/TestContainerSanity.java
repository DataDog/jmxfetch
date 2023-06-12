package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import com.github.dockerjava.api.command.InspectContainerCmd;

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
        log.info("Network mode: {}, id: {}, host: {}, mappedPort(80): {}, exposedPorts: {}", cont.getNetworkMode(), cont.getHost(), cont.getMappedPort(80), cont.getExposedPorts());
        InspectContainerCmd ic = cont.getDockerClient().inspectContainerCmd(cont.getContainerId());
        log.info("Inspect container: {}", ic);
        cont.waitingFor(Wait.forListeningPort());
        assertTrue(isHttpOk(cont.getHost(), cont.getMappedPort(80)));

        cont.close();
    }

}
