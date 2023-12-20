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
import org.datadog.jmxfetch.util.server.MisbehavingJMXServer;
import org.datadog.jmxfetch.util.server.SimpleAppContainer;

@Slf4j
public class TestGCMetrics extends TestCommon {

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

    @Test
    public void testJMXDirectBasic() throws Exception {
        try (final SimpleAppContainer container = new SimpleAppContainer()){
            container.start();
            final String ipAddress = container.getIp();
            final String remoteJmxServiceUrl = String.format(
                "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", ipAddress, container.getRMIPort());
            final JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
            final JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
            final MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();
            assertThat(isDomainPresent("java.lang", mBeanServerConnection), is(true));
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseZGCSimple() throws IOException {
        try (final SimpleAppContainer container = new SimpleAppContainer(
            "eclipse-temurin:17",
            "-XX:+UseZGC -Xmx128M -Xms128M",
            MisbehavingJMXServer.DEFAULT_RMI_PORT
        )){
            container.start();
            this.initApplicationWithYamlLines("init_config:",
                "  is_jmx: true",
                "  new_gc_metrics: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + container.getIp(),
                "        collect_default_jvm_metrics: true",
                "        max_returned_metrics: 300000",
                "        port: " + container.getRMIPort());
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

    @Test
    public void testDefaultOldGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(MisbehavingJMXServer.DEFAULT_RMI_PORT, MisbehavingJMXServer.DEFAULT_CONTROL_PORT,
            MisbehavingJMXServer.DEFAULT_SUPERVISOR_PORT)) {
            server.start();
            this.initApplicationWithYamlLines(
                "init_config:",
                "  is_jmx: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + server.getIp(),
                "        collect_default_jvm_metrics: true",
                "        max_returned_metrics: 300000",
                "        port: " + server.getRMIPort());
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
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
    public void testDefaultNewGCMetricsUseParallelGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
                MisbehavingJMXServer.JDK_11,
                "-XX:+UseParallelGC -Xmx128M -Xms128M")) {
            server.start();
            this.initApplicationWithYamlLines("init_config:",
                "  is_jmx: true",
                "  new_gc_metrics: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + server.getIp(),
                "        collect_default_jvm_metrics: true",
                "        max_returned_metrics: 300000",
                "        port: " + server.getRMIPort());
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            assertThat(actualMetrics, hasSize(13));
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_count", "PS Scavenge", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_time", "PS Scavenge", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_count", "PS MarkSweep", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_time", "PS MarkSweep", "counter");
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseG1GC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
                MisbehavingJMXServer.JDK_17,
                "-XX:+UseG1GC -Xmx128M -Xms128M")) {
            server.start();
            this.initApplicationWithYamlLines(
                "init_config:",
                "  is_jmx: true",
                "  new_gc_metrics: true",
                "",
                "instances:",
                "    -   name: jmxint_container",
                "        host: " + server.getIp(),
                "        collect_default_jvm_metrics: true",
                "        max_returned_metrics: 300000",
                "        port: " + server.getRMIPort());
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
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_count", "G1 Young Generation", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.minor_collection_time", "G1 Young Generation", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_count", "G1 Old Generation", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.major_collection_time", "G1 Old Generation", "counter");
        }
    }
    
    @Test
    public void testDefaultNewGCMetricsUseZGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(
                MisbehavingJMXServer.JDK_17,
                "-XX:+UseZGC -Xmx128M -Xms128M")) {
            server.start();
            this.initApplicationWithYamlLines("init_config:",
                    "  is_jmx: true",
                    "  new_gc_metrics: true",
                    "",
                    "instances:",
                    "    -   name: jmxint_container",
                    "        host: " + server.getIp(),
                    "        collect_default_jvm_metrics: true",
                    "        max_returned_metrics: 300000",
                    "        port: " + server.getRMIPort());
            // Run one iteration first
            this.app.doIteration();
            // And then pull get the metrics or else reporter does not have correct number of metrics
            ((ConsoleReporter) appConfig.getReporter()).getMetrics();

            // Actual iteration we care about
            this.app.doIteration();
            final List<Map<String, Object>> actualMetrics = ((ConsoleReporter) appConfig.getReporter()).getMetrics();
            assertThat(actualMetrics, hasSize(13));
            assertGCMetric(actualMetrics, "jvm.gc.zgc_pauses_collection_count", "ZGC Pauses", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_pauses_collection_time", "ZGC Pauses", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_cycles_collection_count", "ZGC Cycles", "counter");
            assertGCMetric(actualMetrics, "jvm.gc.zgc_cycles_collection_time", "ZGC Cycles", "counter");
        }
    }
    private static void assertGCMetric(final List<Map<String, Object>> actualMetrics,
        final String expectedMetric,
        final String gcGeneration,
        final String metricType) {
        TestCommon.assertMetric(
            expectedMetric,
            -1,
            -1,
            10.0,
            Collections.singletonList(String.format("name:%s", gcGeneration)),
            Arrays.asList("instance:jmxint_container", "jmx_domain:java.lang", "type:GarbageCollector"),
            5,
            metricType,
            actualMetrics);
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
