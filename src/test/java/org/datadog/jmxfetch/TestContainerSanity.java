package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.command.InspectContainerResponse;

@Slf4j
public class TestContainerSanity {


    private static boolean isHttpOk(String host, String port) {
        String url = "http://" + host + ":" + port;
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            return false;
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
        assertTrue(isHttpOk(cont.getHost(),""+80));

        cont.close();
    }


    @Test
    public void testExposedPort() throws Exception {
        int originalPort = 80;
        String strOriginalPort = "" + originalPort;
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withDockerfileFromBuilder( builder -> {
                    builder
                        .from("python:3-buster")
                        .expose(originalPort)
                        .entryPoint("python3", "-m", "http.server", strOriginalPort)
                        .build();
                    });

        GenericContainer container = new GenericContainer<>(image)
                .withExposedPorts(originalPort)
                .waitingFor(Wait.forSuccessfulCommand("hostname"));

        container.start();
        Thread.sleep(1000);
        log.info("Inspect container: {}", container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec());
        log.info("exec ip addr: {}", container.execInContainer("ip", "addr"));
        String ipAddress = container.getContainerInfo().getNetworkSettings().getIpAddress();
        log.info("Container: getHost(): {}, getContainerIp(): {}, ipAddress: {}", container.getHost(), container.getContainerIpAddress(), ipAddress);

        Process process = Runtime.getRuntime().exec("ip addr");

        // Read the output of the command
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        System.out.println("IP ADDR output follows:");
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        // Wait for the command to complete
        int exitCode = process.waitFor();
        System.out.println("'ip addr' Command exited with code: " + exitCode);

        log.info("Container Network Settings: {}", container.getContainerInfo().getNetworkSettings().toString());
        log.info("Container Port Configuration: {}", container.getContainerInfo().getNetworkSettings().getPorts().toString());
        log.info("HostConfig Port Bindings: {}", container.getContainerInfo().getHostConfig().getPortBindings());
        log.info("HostConfig Port Bindings (getBindings): {}", container.getContainerInfo().getHostConfig().getPortBindings().getBindings());
        String mappedPort = ""+container.getMappedPort(originalPort);

        String[][] hostPortTuples = {
            { "container IP + mapped"   , ipAddress           , mappedPort}      ,
            { "container IP + original" , ipAddress           , strOriginalPort} ,
            { "getHost + mapped"        , container.getHost() , mappedPort}      , // according to docs , this is the winner
            { "getHost + original"      , container.getHost() , strOriginalPort} ,
            { "localhost + mapped"      , "localhost"         , mappedPort}      ,
            { "localhost + original"    , "localhost"         , strOriginalPort}
        };

        for (String[] tuple : hostPortTuples) {
            log.info("Check against {}:{} ({}) is: {}", tuple[1], tuple[2], tuple[0], isHttpOk(tuple[1], tuple[2]));
        }
    }

}
