package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;

import org.junit.Before;
import org.junit.Rule;

public class TestSolrContainer extends TestRemoteAppCommon {
    
    private static final int jmxPort = 8799;
    private static final int firstToxiproxyPort = 8666;

    private static boolean isDomainPresent(String domain, MBeanServerConnection mbs) throws IOException {
        String[] domains = mbs.getDomains();
        boolean found = false;
        for (int i = 0; i < domains.length; i++) {
            if (domains[i].equals(domain)) {
                found = true;
            }
        }
        return found;
    }

    @Test
    public void testSolrMetricsAreCollected() throws Exception {
        try (
            Network network = Network.NetworkImpl.builder().build();

            GenericContainer<?> solr = new GenericContainer<>(DockerImageName.parse("solr:8"))
                .withExposedPorts(jmxPort)
                .withEnv(Collections.singletonMap("ENABLE_REMOTE_JMX_OPTS", "true"))
                .withEnv(Collections.singletonMap("RMI_PORT", ""+jmxPort))
                .withNetwork(network)
                .withNetworkAliases("solr")
                .waitingFor(Wait.forLogMessage(".*o\\.e\\.j\\.s\\.Server Started.*", 1));

            ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:latest")
                .withNetwork(network);
        ) {
            solr.start();
            toxiproxy.start();

            // Setup proxy connection
            final ToxiproxyClient toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
            final Proxy proxy = toxiproxyClient.createProxy("solr", "0.0.0.0:"+firstToxiproxyPort, "solr:" + jmxPort);

            final String ipAddressViaToxiproxy = toxiproxy.getHost();
            final int portViaToxiproxy = toxiproxy.getMappedPort(firstToxiproxyPort);

            // Connect directly via JMXConnector
            String remoteJmxServiceUrl = String.format(
                "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
                ipAddressViaToxiproxy, portViaToxiproxy
            );

            log.info("Connecting to jmx url: {}", remoteJmxServiceUrl);
            JMXConnector conn = JMXConnectorFactory.connect(new JMXServiceURL(remoteJmxServiceUrl));
            MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();

            assertEquals(true, isDomainPresent("solr", mBeanServerConnection));

            // Connect and collect via JMXFetch
            log.info("Trying to connect to solr via host: {} and port: {}", ipAddressViaToxiproxy, portViaToxiproxy);
            this.initApplication("jmxint_solr.yaml", ipAddressViaToxiproxy, portViaToxiproxy);

            this.app.doIteration();
            List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
            assertTrue(metrics.size() > 5);

            log.info("Applying Toxics to proxy, isEnabled {} listen {} name {}", proxy.isEnabled(), proxy.getListen(), proxy.getName());
            proxy.toxics().resetPeer("RESET", ToxicDirection.DOWNSTREAM, 0);
            proxy.toxics().bandwidth("CUT_CONNECTION_DOWNSTREAM", ToxicDirection.DOWNSTREAM, 0);
            proxy.toxics().bandwidth("CUT_CONNECTION_UPSTREAM", ToxicDirection.UPSTREAM, 0);

            Throwable e = null;
            try {
                mBeanServerConnection.getDomains();
            } catch (Throwable ee) {
                e = ee;
            }
            assertNotNull("GetDomains should fail since network is cut", e);
            assertEquals(true, isDomainPresent("solr", mBeanServerConnection));


            log.info("Doing first with network disconnected, should fail and mark instance as broken");
            this.app.doIteration();
            metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
            // TODO This test fails! So why is jmxfetch still able to collect metrics?
            assertEquals("Expected no metrics because network is disconnected", 0, metrics.size());

            log.info("Doing second iteration, should recover broken instance");
            // Second iteration should succeed
            this.app.doIteration();
            metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
            assertTrue(metrics.size() > 5);
        }
    }
}
