package org.datadog.jmxfetch;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.util.MisbehavingJMXServer;
import org.junit.Test;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@Slf4j
public class TestGCMetrics extends TestCommon {

    private static final int RMI_PORT = 9090;
    private static final int CONTROL_PORT = 9091;
    private static final int SUPERVISOR_PORT = 9092;
    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

    private static boolean isDomainPresent(final String domain, final MBeanServerConnection mbs) {
        boolean found = false;
        try {
            final String[] domains = mbs.getDomains();
            for (String s : domains)
                if(s.equals(domain)) {
                    found = true;
                    break;
                }
        } catch (IOException e) {
            log.warn("Got an exception checking if domain is present", e);
        }
        return found;
    }

    /*
    Just here to make sure I've not broken anything
     */
    @Test
    public void testJMXDirectBasic() throws Exception {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
            RMI_PORT,
            CONTROL_PORT,
            SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            final String remoteJmxServiceUrl = String.format(
                "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi",
                ipAddress, RMI_PORT
            );
            final JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
            final JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
            final MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();
            assertTrue(isDomainPresent("Bohnanza", mBeanServerConnection));
        }
    }
}
