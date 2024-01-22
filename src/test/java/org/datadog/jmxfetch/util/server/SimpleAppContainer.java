package org.datadog.jmxfetch.util.server;

import java.nio.file.Paths;
import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.lifecycle.Startable;

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
                .waitingFor(new WaitOrStrategy(
                    Wait.forLogMessage(".*Sample app started..*", 1),
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
