package org.datadog.jmxfetch.util.server;

import java.io.IOException;
import java.nio.file.Paths;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.lifecycle.Startable;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.JMXServerControlClient;
import org.datadog.jmxfetch.JMXServerSupervisorClient;
import org.datadog.jmxfetch.util.server.WaitOrStrategy;
import org.datadog.jmxfetch.util.TimerWaitStrategy;

@Slf4j
public class MisbehavingJMXServer implements Startable {

    public static final int DEFAULT_RMI_PORT = 9090;
    public static final int DEFAULT_CONTROL_PORT = 9091;
    public static final int DEFAULT_SUPERVISOR_PORT = 9092;
    private static final String DEFAULT_JDK_IMAGE = "base";
    private static final String DEFAULT_MISBEHAVING_OPTS = "-Xmx128M -Xms128M";
    private static final String RMI_PORT = "RMI_PORT";
    private static final String CONTROL_PORT = "CONTROL_PORT";
    private static final String SUPERVISOR_PORT = "SUPERVISOR_PORT";
    public static final String MISBEHAVING_OPTS = "MISBEHAVING_OPTS";
    private final String jdkImage;

    private final String javaOpts;
    private final int rmiPort;
    private final int controlPort;
    private final int supervisorPort;
    private final GenericContainer<?> server;
    private JMXServerControlClient controlClient;
    private JMXServerSupervisorClient supervisorClient;

    public MisbehavingJMXServer(
        final String jdkImage,
        final String javaOpts,
        final int rmiPort,
        final int controlPort,
        final int supervisorPort) {
        this.javaOpts = javaOpts;
        this.rmiPort = rmiPort;
        this.controlPort = controlPort;
        this.supervisorPort = supervisorPort;
        this.jdkImage = jdkImage;
        final ImageFromDockerfile img = new ImageFromDockerfile()
            .withFileFromPath(".", Paths.get("./tools/misbehaving-jmx-server/"))
            .withBuildArg("FINAL_JRE_IMAGE", this.jdkImage);
        this.server = new GenericContainer<>(img)
            .withExposedPorts(rmiPort, controlPort, supervisorPort)
            .withEnv(RMI_PORT, String.valueOf(rmiPort))
            .withEnv(CONTROL_PORT, String.valueOf(controlPort))
            .withEnv(SUPERVISOR_PORT, String.valueOf(supervisorPort))
            .withEnv(MISBEHAVING_OPTS, this.javaOpts)
            // Waiting is a bit tricky here, so lets explain
            // There are two cases that need to be supported by this code
            // 1. Environments where port checks work correctly
            // 2. Environments where port checks never succeed
            // If the listening port is ever detected, then that is a valid signal
            // that the container has started.
            // If the log message is observed, we impose an artificial 5s delay
            // to allow the networking stack to "catch up" to the container logs
            // This is the fix for observed flakey tests in CI.
            .waitingFor(new WaitOrStrategy(
                new WaitAllStrategy()
                    .withStrategy(Wait.forLogMessage(".*Supervisor HTTP Server Started. Waiting for initialization payload POST to /init.*", 1))
                    .withStrategy(new TimerWaitStrategy(5000)),
                Wait.forListeningPorts(supervisorPort)
            ));
    }

    @Override
    public void start() {
        log.info("Starting MisbehavingJMXServer with Docker image '{}' with MISBEHAVING_OPTS '{}'",
            this.jdkImage, this.javaOpts);
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

    public int getRMIPort() {
        return this.rmiPort;
    }

    public static class Builder {

        private String jdkImage;
        private String javaOpts;
        private int rmiPort;
        private int controlPort;
        private int supervisorPort;

        public Builder() {
            this.jdkImage = MisbehavingJMXServer.DEFAULT_JDK_IMAGE;
            this.javaOpts = MisbehavingJMXServer.DEFAULT_MISBEHAVING_OPTS;
            this.rmiPort = MisbehavingJMXServer.DEFAULT_RMI_PORT;
            this.controlPort = MisbehavingJMXServer.DEFAULT_CONTROL_PORT;
            this.supervisorPort = MisbehavingJMXServer.DEFAULT_SUPERVISOR_PORT;
        }

        public Builder withJDKImage(final String jdkImage) {
            this.jdkImage = jdkImage;
            return this;
        }

        public Builder withJDKImage(final JDKImage jdkImage) {
            this.jdkImage = jdkImage.toString();
            return this;
        }

        public Builder withJavaOpts(String javaOpts) {
            this.javaOpts = javaOpts;
            return this;
        }

        public Builder appendJavaOpts(String javaOpts) {
            this.javaOpts = String.format("%s %s", javaOpts, this.javaOpts);
            return this;
        }

        public Builder withRmiPort(int rmiPort) {
            this.rmiPort = rmiPort;
            return this;
        }

        public Builder withControlPort(int controlPort) {
            this.controlPort = controlPort;
            return this;
        }

        public Builder withSupervisorPort(int supervisorPort) {
            this.supervisorPort = supervisorPort;
            return this;
        }

        public MisbehavingJMXServer build() {
            return new MisbehavingJMXServer(
                this.jdkImage,
                this.javaOpts,
                this.rmiPort,
                this.controlPort,
                this.supervisorPort
            );
        }
    }
}
