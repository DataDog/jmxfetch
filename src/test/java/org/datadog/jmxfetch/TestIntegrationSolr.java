package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.datadog.jmxfetch.reporter.ConsoleReporter;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import org.junit.Before;
import org.junit.Rule;

public class TestIntegrationSolr extends TestIntegration {
    
    private static final int jmxPort = 8799;
    private MBeanServerConnection mBeanServerConnection;
    @Rule
    public GenericContainer<?> solr = new GenericContainer<>(DockerImageName.parse("solr:8"))
        .withExposedPorts(jmxPort)
        .withEnv(Collections.singletonMap("ENABLE_REMOTE_JMX_OPTS", "true"))
        .withEnv(Collections.singletonMap("RMI_PORT", "8799"))
        .waitingFor(Wait.forLogMessage(".*o\\.e\\.j\\.s\\.Server Started.*", 1));

    @Before
    public void setUp() throws Exception{
        String host = solr.getHost();
        int port = solr.getMappedPort(jmxPort);
        String remoteJmxServiceUrl = String.format(
            "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
            host, port
        );

        log.info("Connecting to jmx url: {}", remoteJmxServiceUrl);
        JMXConnector conn = JMXConnectorFactory.connect(new JMXServiceURL(remoteJmxServiceUrl));

        this.mBeanServerConnection = conn.getMBeanServerConnection();
        
        this.initApplication("jmxint_solr.yaml", host, port);
    }

    @Test
    public void testSolrJMXDomainFound() throws Exception {
        String[] domains = this.mBeanServerConnection.getDomains();
        boolean found = false;
        for (int i = 0; i < domains.length; i++) {
            if (domains[i].equals("solr")) {
                found = true;
            }
        }
        assertEquals(true, found);
    }
    
    @Test
    public void testSolrMetricsAreCollected() {
        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertTrue(metrics.size() > 5);
    }
}
