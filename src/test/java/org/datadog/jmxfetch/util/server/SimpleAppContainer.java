package org.datadog.jmxfetch.util.server;

import java.nio.file.Paths;
import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import org.datadog.jmxfetch.util.TimerWaitStrategy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleAppContainer implements Startable {

    private static final String JAVA_OPTS = "JAVA_OPTS";
    private static final String RMI_PORT = "RMI_PORT";
    private final String jreDockerImage;
    private final String javaOpts;
    private final int rmiPort;
    private final GenericContainer<?> server;

    public SimpleAppContainer() {
        this("eclipse-temurin:17", "", MisbehavingJMXServer.DEFAULT_RMI_PORT);

    }

    public SimpleAppContainer(final String jreDockerImage, final String javaOpts, final int rmiPort) {
        this.jreDockerImage = jreDockerImage;
        this.javaOpts = javaOpts;
        this.rmiPort = rmiPort;
        final ImageFromDockerfile img = new ImageFromDockerfile()
                .withFileFromPath("app.java", Paths.get("./src/test/java/org/datadog/jmxfetch/util/server/SimpleApp.java"))
                .withFileFromClasspath("Dockerfile", "org/datadog/jmxfetch/util/server/Dockerfile-SimpleApp")
                .withFileFromClasspath("run.sh", "org/datadog/jmxfetch/util/server/run-SimpleApp.sh")
                .withBuildArg("JRE_DOCKER_IMAGE", jreDockerImage);
        this.server = new GenericContainer<>(img)
                .withEnv(JAVA_OPTS, this.javaOpts)
                .withEnv(RMI_PORT, Integer.toString(this.rmiPort))
                .withExposedPorts(this.rmiPort)
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
                        .withStrategy(Wait.forLogMessage(".*Sample app started..*", 1))
                        .withStrategy(new TimerWaitStrategy(5000)),
                    Wait.forListeningPorts(this.rmiPort)
                ));
    }

    @Override
    public void start() {
        log.info("Starting SimpleApp with Docker image '{}' with JAVA_OPTS '{}' in port '{}'",
                this.jreDockerImage, this.javaOpts, this.rmiPort);
        this.server.start();
        log.info(this.server.getLogs());
    }

    @Override
    public void stop() {
        this.server.stop();
    }

    public void close() {
        this.stop();
    }

    public String getIp() {
        return this.server.getContainerInfo().getNetworkSettings().getIpAddress();
    }

    public int getRMIPort() {
        return this.rmiPort;
    }
}
