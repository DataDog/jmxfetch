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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Paths;

import org.datadog.jmxfetch.reporter.ConsoleReporter;


public class TestReconnectContainer extends TestCommon {
    private static final int rmiPort = 9090;
    private static final int controlPort = 9091;
    private JMXServerControlClient controlClient;

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

    @Before
    public void setup() {
        this.controlClient = new JMXServerControlClient(cont.getHost(), cont.getMappedPort(controlPort));
    }

    @Rule
    public GenericContainer<?> cont = new GenericContainer<>(img)
        .withExposedPorts(rmiPort, controlPort)
        .withEnv(Collections.singletonMap("RMI_PORT", "" + rmiPort))
        .withEnv(Collections.singletonMap("CONTROL_PORT", "" + controlPort))
        .waitingFor(Wait.forLogMessage(".*IAMREADY.*", 1));

    @Test
    public void testJMXDirectBasic() throws Exception {
        // Connect directly via JMXConnector
        String remoteJmxServiceUrl = String.format(
            "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
            cont.getHost(), cont.getMappedPort(rmiPort)
        );

        JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
        JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
        MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();

        assertEquals(true, isDomainPresent("Bohnanza", mBeanServerConnection));
    }

    @Test
    public void testJMXDirectReconnect() throws Exception {
        // Connect directly via JMXConnector
        String remoteJmxServiceUrl = String.format(
            "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
            cont.getHost(), cont.getMappedPort(rmiPort)
        );

        JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
        JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
        MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();

        assertEquals(true, isDomainPresent("Bohnanza", mBeanServerConnection));

        this.controlClient.jmxCutNetwork();

        assertEquals(false, isDomainPresent("Bohnanza", mBeanServerConnection));

        this.controlClient.jmxRestoreNetwork();

        assertEquals(true, isDomainPresent("Bohnanza", mBeanServerConnection));
    }

    @Test
    public void testJMXFetchBasic() throws IOException, InterruptedException {
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + cont.getHost(),
            "        collect_default_jvm_metrics: false",
            "        max_returned_metrics: 300000",
            "        port: " + cont.getMappedPort(rmiPort),
            "        conf:",
            "          - include:",
            "              domain: Bohnanza"
        );

        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(1, metrics.size());
    }

    @Test
    public void testJMXFetchManyMetrics() throws IOException, InterruptedException {
        int numBeans = 100;
        int numAttributesPerBean = 4;

        String testDomain = "test-domain";
        this.controlClient.createMBeans(testDomain, numBeans);
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + cont.getHost(),
            "        collect_default_jvm_metrics: false",
            "        max_returned_metrics: 300000",
            "        port: " + cont.getMappedPort(rmiPort),
            "        conf:",
            "          - include:",
            "              domain: " + testDomain
        );

        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        int expectedMetrics =  numBeans * numAttributesPerBean;
        assertEquals(expectedMetrics, metrics.size());
    }

    @Test
    public void testJMXFetchReconnect() throws IOException, InterruptedException {
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + cont.getHost(),
            "        collect_default_jvm_metrics: false",
            "        max_returned_metrics: 300000",
            "        port: " + cont.getMappedPort(rmiPort),
            "        conf:",
            "          - include:",
            "              domain: Bohnanza"
        );


        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(1, metrics.size());

        this.controlClient.jmxCutNetwork();

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(0, metrics.size());

        // After previous iteration where network was cut off, instance will be marked as broken
        // Restore network
        this.controlClient.jmxRestoreNetwork();

        // Do iteration to recover instance
        this.app.doIteration();

        // Previous iteration recovered, which just means the new instance has been 'init'ed
        // But no metrics have been collected.
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(0, metrics.size());

        // Next iteration will actually collect metrics
        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(1, metrics.size());
    }
}
