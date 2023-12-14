package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.JMXServerControlClient;
import org.datadog.jmxfetch.JMXServerSupervisorClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.lifecycle.Startable;

@Slf4j
public class MisbehavingJMXServer implements Startable {

    private static final String DEFAULT_JDK_IMAGE = "base";
    private final String jdkImage;
    private final int controlPort;
    private final int supervisorPort;
    private final GenericContainer<?> server;
    private JMXServerControlClient controlClient;
    private JMXServerSupervisorClient supervisorClient;

    public MisbehavingJMXServer(
        final int rmiPort,
        final int controlPort,
        final int supervisorPort) {
        this(DEFAULT_JDK_IMAGE, rmiPort, controlPort, supervisorPort);
    }

    public MisbehavingJMXServer(
        final String jdkImage,
        final int rmiPort,
        final int controlPort,
        final int supervisorPort) {
        this.controlPort = controlPort;
        this.supervisorPort = supervisorPort;
        this.jdkImage = jdkImage;
        final ImageFromDockerfile img = new ImageFromDockerfile()
            .withFileFromPath(".", Paths.get("./tools/misbehaving-jmx-server/"));
        this.server = new GenericContainer<>(img)
            .withEnv(Collections.singletonMap("RMI_PORT", "" + rmiPort))
            .withEnv(Collections.singletonMap("CONTROL_PORT", "" + controlPort))
            .withEnv(Collections.singletonMap("SUPERVISOR_PORT", "" + supervisorPort))
            .waitingFor(Wait.forLogMessage(
                ".*Supervisor HTTP Server Started. Waiting for initialization payload POST to /init.*",
                1));
    }

    @Override
    public void start() {
        this.server.start();
        final String ipAddress = this.getIp();
        this.controlClient = new JMXServerControlClient(ipAddress, this.controlPort);
        this.supervisorClient = new JMXServerSupervisorClient(ipAddress, this.supervisorPort);
        try {
            log.debug("Initializing JMXServer");
            this.supervisorClient.initializeJMXServer(ipAddress);
        } catch (IOException e) {
            log.error("Could not initialize JMX Server", e);
        }
    }

    @Override
    public void stop() {
        this.server.stop();
    }

    @Override
    public void close() {
        this.stop();
    }

    public String getIp() {
        return this.server.getContainerInfo().getNetworkSettings().getIpAddress();
    }
}
