package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.nio.file.Paths;
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
    private static final String RMI_PORT = "RMI_PORT";
    private static final String CONTROL_PORT = "CONTROL_PORT";
    private static final String SUPERVISOR_PORT = "SUPERVISOR_PORT";
    public static final String JAVA_OPTS = "JAVA_OPTS";
    private final String jdkImage;

    private final String javaOpts;
    private final int controlPort;
    private final int supervisorPort;
    private final GenericContainer<?> server;
    private JMXServerControlClient controlClient;
    private JMXServerSupervisorClient supervisorClient;

    public MisbehavingJMXServer(
        final int rmiPort,
        final int controlPort,
        final int supervisorPort) {
        this(DEFAULT_JDK_IMAGE, "", rmiPort, controlPort, supervisorPort);
    }

    public MisbehavingJMXServer(
        final String jdkImage,
        final String javaOpts,
        final int rmiPort,
        final int controlPort,
        final int supervisorPort) {
        this.javaOpts = javaOpts;
        this.controlPort = controlPort;
        this.supervisorPort = supervisorPort;
        this.jdkImage = jdkImage;
        final ImageFromDockerfile img = new ImageFromDockerfile()
            .withFileFromPath(".", Paths.get("./tools/misbehaving-jmx-server/"))
            .withBuildArg("FINAL_JRE_IMAGE", this.jdkImage);
        this.server = new GenericContainer<>(img)
            .withEnv(RMI_PORT, String.valueOf(rmiPort))
            .withEnv(CONTROL_PORT, String.valueOf(controlPort))
            .withEnv(SUPERVISOR_PORT, String.valueOf(supervisorPort))
            .withEnv(JAVA_OPTS, this.javaOpts)
            .waitingFor(Wait.forLogMessage(
                ".*Supervisor HTTP Server Started. Waiting for initialization payload POST to /init.*",
                1));
    }

    @Override
    public void start() {
        log.info("Starting MisbehavingJMXServer with Docker image '{}' with JAVA_OPTS '{}'", this.jdkImage, this.javaOpts);
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

    public void cutNetwork() throws IOException {
        this.controlClient.jmxCutNetwork();
    }

    public void restoreNetwork() throws IOException {
        this.controlClient.jmxRestoreNetwork();
    }
}
