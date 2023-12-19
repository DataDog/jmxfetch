package org.datadog.jmxfetch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.util.*;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.util.MisbehavingJMXServer;
import org.datadog.jmxfetch.util.server.SimpleAppContainer;

@Slf4j
public class TestGCMetrics extends TestCommon {

    private static final int RMI_PORT = 9090;
    private static final int CONTROL_PORT = 9091;
    private static final int SUPERVISOR_PORT = 9092;

    private static boolean isDomainPresent(final String domain, final MBeanServerConnection mbs) {
        boolean found = false;
        try {
            final String[] domains = mbs.getDomains();
            for (String s : domains) {
                if(s.equals(domain)) {
                    found = true;
                    break;
                }
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
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(RMI_PORT, CONTROL_PORT,
            SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            final String remoteJmxServiceUrl = String.format(
                "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", ipAddress, RMI_PORT);
            final JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
            final JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
            final MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();
            assertThat(isDomainPresent("Bohnanza", mBeanServerConnection), is(true));
        }
    }

    @Test
    public void testJMXFetchBasic() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(RMI_PORT, CONTROL_PORT,
            SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            this.initApplicationWithYamlLines(
                "init_config:",
                "  is_jmx: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + ipAddress,
                "        collect_default_jvm_metrics: false",
                "        max_returned_metrics: 300000",
                "        port: " + RMI_PORT,
                "        conf:",
                "          - include:",
                "              domain: Bohnanza"
            );
            this.app.doIteration();
            final List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
            assertThat(metrics, hasSize(1));
        }
    }

    @Test
    public void testDefaultOldGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(RMI_PORT, CONTROL_PORT,
            SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            this.initApplicationWithYamlLines("init_config:",
                "  is_jmx: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + ipAddress,
                "        collect_default_jvm_metrics: true",
                "        max_returned_metrics: 300000",
                "        port: " + RMI_PORT);
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            List<String> gcGenerations = Arrays.asList(
                "G1 Old Generation",
                "G1 Young Generation");
            assertGCMetric(actualMetrics, "jvm.gc.cms.count", gcGenerations);
            assertGCMetric(actualMetrics, "jvm.gc.parnew.time", gcGenerations);
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseParallelGC() throws IOException, InterruptedException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
                MisbehavingJMXServer.JDK_11,
                "-XX:+UseParallelGC",
                RMI_PORT,
                CONTROL_PORT,
                SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            this.initApplicationWithYamlLines("init_config:",
                "  is_jmx: true",
                "  new_gc_metrics: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + ipAddress,
                "        collect_default_jvm_metrics: true",
                "        max_returned_metrics: 300000",
                "        port: " + RMI_PORT);
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            assertThat(actualMetrics, hasSize(13));
            final List<String> gcYoungGenerations = Collections.singletonList(
                    "PS Scavenge");
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_count", gcYoungGenerations);
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_time", gcYoungGenerations);
            final List<String> gcOldGenerations = Collections.singletonList(
                    "PS MarkSweep");
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_count", gcOldGenerations);
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_time", gcOldGenerations);
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseG1GC() throws IOException, InterruptedException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
                MisbehavingJMXServer.JDK_17,
                "-XX:+UseG1GC",
                RMI_PORT,
                CONTROL_PORT,
                SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            this.initApplicationWithYamlLines("init_config:",
                    "  is_jmx: true",
                    "  new_gc_metrics: true",
                    "",
                    "instances:",
                    "    -   name: jmxint_container",
                    "        host: " + ipAddress,
                    "        collect_default_jvm_metrics: true",
                    "        max_returned_metrics: 300000",
                    "        port: " + RMI_PORT);
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            assertThat(actualMetrics, hasSize(13));
            final List<String> gcYoungGenerations = Collections.singletonList(
                    "G1 Young Generation");
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_count", gcYoungGenerations);
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_time", gcYoungGenerations);
            final List<String> gcOldGenerations = Collections.singletonList(
                    "G1 Old Generation");
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_count", gcOldGenerations);
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_time", gcOldGenerations);
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseZGC() throws IOException {
        try (final SimpleAppContainer container = new SimpleAppContainer(
                "eclipse-temurin:17",
                "-XX:+UseZGC -Xmx128M -Xms128M",
                RMI_PORT
        )){
            container.start();
            final String ipAddress = container.getIp();
            this.initApplicationWithYamlLines("init_config:",
                    "  is_jmx: true",
                    "  new_gc_metrics: true",
                    "",
                    "instances:",
                    "    -   name: jmxint_container",
                    "        host: " + ipAddress,
                    "        collect_default_jvm_metrics: true",
                    "        max_returned_metrics: 300000",
                    "        port: " + RMI_PORT);
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            assertThat(actualMetrics, hasSize(13));
            final List<String> zgcPause = Collections.singletonList(
                    "ZGC Pauses");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_pauses_collection_count", zgcPause);
            assertGCMetric(actualMetrics, "jvm.gc.zgc_pauses_collection_time", zgcPause);
            final List<String> zgcCycles = Collections.singletonList(
                    "ZGC Cycles");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_cycles_collection_count", zgcCycles);
            assertGCMetric(actualMetrics, "jvm.gc.zgc_cycles_collection_time", zgcCycles);
        }
    }
    
//    @Ignore("Can not force ZGC to work using MisbehavingJMXServer")
    @Test
    public void testDefaultNewGCMetricsUseZGCOld() throws IOException, InterruptedException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
                MisbehavingJMXServer.JDK_17,
                "-XX:+UseZGC",
                RMI_PORT,
                CONTROL_PORT,
                SUPERVISOR_PORT)) {
            server.start();
            final String ipAddress = server.getIp();
            this.initApplicationWithYamlLines("init_config:",
                    "  is_jmx: true",
                    "  new_gc_metrics: true",
                    "",
                    "instances:",
                    "    -   name: jmxint_container",
                    "        host: " + ipAddress,
                    "        collect_default_jvm_metrics: true",
                    "        max_returned_metrics: 300000",
                    "        port: " + RMI_PORT);
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            assertThat(actualMetrics, hasSize(13));
            final List<String> zgcPause = Collections.singletonList(
                    "ZGC Pauses");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_pauses_collection_count", zgcPause);
            assertGCMetric(actualMetrics, "jvm.gc.zgc_pauses_collection_time", zgcPause);
            final List<String> zgcCycles = Collections.singletonList(
                    "ZGC Cycles");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_cycles_collection_count", zgcCycles);
            assertGCMetric(actualMetrics, "jvm.gc.zgc_cycles_collection_time", zgcCycles);
        }
    }

    private static void assertGCMetric(final List<Map<String, Object>> actualMetrics,
        final String expectedMetric,
        final List<String> gcGenerations) {
        final List<Map<String, Object>> filteredMetrics = new ArrayList<>();
        for (Map<String, Object> actualMetric : actualMetrics) {
            final String name = (String) actualMetric.get("name");
            if(expectedMetric.equals(name)) {
                filteredMetrics.add(actualMetric);
            }
        }
        assertThat(filteredMetrics, hasSize(gcGenerations.size()));
        for (final String name : gcGenerations) {
            log.debug("Asserting for metric '{}'", name);
            boolean found = false;
            for (Map<String, Object> filteredMetric : filteredMetrics) {
                final Set<String> mTags = new HashSet<>(
                    Arrays.asList((String[]) (filteredMetric.get("tags"))));

                if(mTags.contains(String.format("name:%s", name))) {
                    assertThat(mTags, not(empty()));
                    assertThat(mTags, hasSize(5));
                    log.debug("mTags '{}' has size: {}\n{}", name, mTags.size(), mTags);
                    assertThat(mTags, hasItems(
                        "instance:jmxint_container",
                        "jmx_domain:java.lang",
                        "type:GarbageCollector",
                        String.format("name:%s", name)));
                    found = true;
                }
            }
            assertThat(String.format("Did not find metric '%s'", name), found, is(true));
        }
    }
}
