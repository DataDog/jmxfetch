package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;

@Slf4j
public class TestContainerSanity {


    private static boolean isHttpOk(String host, int port) throws IOException {
        String url = "http://" + host + ":" + port;
        log.info("Requesting url: {}", url);
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
            .withExposedPorts(80)
            .waitingFor(Wait.forSuccessfulCommand("hostname"));
            //.waitingFor(Wait.forHttp("/").forPort(80).forStatusCode(200));
        cont.start();
        Thread.sleep(2000);
        log.info("Network mode: {}, id: {}, host: {}, mappedPort(80): {}, exposedPorts: {}",
                cont.getNetworkMode(),
                cont.getContainerId(),
                cont.getHost(),
                cont.getMappedPort(80),
                cont.getExposedPorts());

        log.info("Bindings: {}", cont.getPortBindings());
        InspectContainerResponse ic = cont.getDockerClient().inspectContainerCmd(cont.getContainerId()).exec();
        log.info("Inspect container: {}", ic);
        cont.waitingFor(Wait.forListeningPort());
        assertTrue(isHttpOk(cont.getHost(), 80));

        cont.close();
    }


    @Test
    public void testExposedPort() throws Exception {
        ImageFromDockerfile simpleHttpPortEighty = new ImageFromDockerfile("eighty", false)
                .withDockerfileFromBuilder( builder -> {
                    builder
                        .from("python:3-buster")
                        .expose(80)
                        .entryPoint("python3", "-m", "http.server", "80")
                        .build();
                    });

        // Start the container using the built image
        GenericContainer container = new GenericContainer<>(simpleHttpPortEighty)
                .withExposedPorts(80)
                .waitingFor(Wait.forHttp("/").forStatusCode(200));

        container.start();
        assertTrue(isHttpOk(container.getHost(), container.getMappedPort(80)));
    }

}
