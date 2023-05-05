package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.datadog.jmxfetch.reporter.ConsoleReporter;

@Slf4j
class JMXFailServerClient {
    private final String host;
    private final int port;

    public JMXFailServerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void jmxCutNetwork() throws IOException {
        sendPostRequest("/cutNetwork");
    }

    public void jmxRestoreNetwork() throws IOException {
        sendPostRequest("/restoreNetwork");
    }

    private void sendPostRequest(String endpoint) throws IOException {
        URL url = new URL("http://" + host + ":" + port + endpoint);
        log.info("Sending POST to {} ", url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println(response.toString());
        } else {
            System.out.println("HTTP POST request failed with status code: " + responseCode);
        }
    }
}


public class TestReconnectContainer extends TestRemoteAppCommon {
    private static final int rmiPort = 9090;
    private static final int controlPort = 9091;

    private static boolean isDomainPresent(String domain, MBeanServerConnection mbs) {
        boolean found = false;
        try {
            String[] domains = mbs.getDomains();
            for (int i = 0; i < domains.length; i++) {
                if (domains[i].equals(domain)) {
                    found = true;
                }
            }
        } catch (IOException e) {
            found = false;
        }
        return found;
    }

    private static ImageFromDockerfile img = new ImageFromDockerfile()
        .withFileFromPath(".", Paths.get("./tools/misbehaving-jmx-server/"));

    @Rule
    public GenericContainer<?> cont = new GenericContainer<>(img)
        .withExposedPorts(rmiPort, controlPort)
        .withEnv(Collections.singletonMap("RMI_PORT", "" + rmiPort))
        .withEnv(Collections.singletonMap("CONTROL_PORT", "" + controlPort))
        .waitingFor(Wait.forLogMessage(".*IAMREADY.*", 1));

    @Test
    public void testJMXDirectBasic() throws Exception {
        final int runtimeRmiPort = cont.getMappedPort(rmiPort);

        // Connect directly via JMXConnector
        String remoteJmxServiceUrl = String.format(
            "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
            cont.getHost(), runtimeRmiPort
        );

        log.info("Connecting to jmx url: {}", remoteJmxServiceUrl);
        JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
        JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
        MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();

        assertEquals(true, isDomainPresent("Bohnanza", mBeanServerConnection));
    }

    @Test
    public void testJMXDirectReconnect() throws Exception {
        final int runtimeControlPort = cont.getMappedPort(controlPort);
        final int runtimeRmiPort = cont.getMappedPort(rmiPort);

        // Connect directly via JMXConnector
        String remoteJmxServiceUrl = String.format(
            "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
            cont.getHost(), runtimeRmiPort
        );

        log.info("Connecting to jmx url: {}", remoteJmxServiceUrl);
        JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
        JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
        MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();

        assertEquals(true, isDomainPresent("Bohnanza", mBeanServerConnection));

        JMXFailServerClient client = new JMXFailServerClient(cont.getHost(), runtimeControlPort);

        client.jmxCutNetwork();

        assertEquals(false, isDomainPresent("Bohnanza", mBeanServerConnection));

        client.jmxRestoreNetwork();

        assertEquals(true, isDomainPresent("Bohnanza", mBeanServerConnection));
    }

    @Test
    public void testJMXFetchBasic() throws IOException, InterruptedException {
        this.initApplication("jmxint_container.yaml", cont.getHost(), cont.getMappedPort(rmiPort));

        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(true, metrics.size() >= 1);
    }
    @Test
    public void testJMXFetchReconnect() throws IOException, InterruptedException {
        this.initApplication("jmxint_container.yaml", cont.getHost(), cont.getMappedPort(rmiPort));

        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(1, metrics.size());

        JMXFailServerClient client = new JMXFailServerClient(cont.getHost(), cont.getMappedPort(controlPort));
        client.jmxCutNetwork();

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(0, metrics.size());

        // After previous iteration where network was cut off, instance will be marked as broken
        // Restore network
        client.jmxRestoreNetwork();

        // Do iteration to recover instance
        this.app.doIteration();

        // Previous iteration recovered, which just means the new instance has been 'init'ed
        // So no metrics are available yet
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(0, metrics.size());

        // Next iteration will actually collect metrics
        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(1, metrics.size());

    }
}
