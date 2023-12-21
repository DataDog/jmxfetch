package org.datadog.jmxfetch;

import static org.datadog.jmxfetch.util.MetricsAssert.assertDomainPresent;
import static org.datadog.jmxfetch.util.server.app.org.datadog.jmxfetch.util.server.JDKImage.JDK_11;
import static org.datadog.jmxfetch.util.server.app.org.datadog.jmxfetch.util.server.JDKImage.JDK_17;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.util.MetricsAssert;
import org.datadog.jmxfetch.util.server.MisbehavingJMXServer;
import org.datadog.jmxfetch.util.server.SimpleAppContainer;

@Slf4j
public class TestGCMetrics extends TestCommon {

    @Test
    public void testJMXDirectBasic() throws Exception {
        try (final SimpleAppContainer container = new SimpleAppContainer()) {
            container.start();
            final String ipAddress = container.getIp();
            final String remoteJmxServiceUrl = String.format(
                "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", ipAddress, container.getRMIPort());
            final JMXServiceURL jmxUrl = new JMXServiceURL(remoteJmxServiceUrl);
            final JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
            final MBeanServerConnection mBeanServerConnection = conn.getMBeanServerConnection();
            assertDomainPresent("java.lang", mBeanServerConnection);
        }
    }

    @Test
    public void testDefaultOldGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer.Builder().build()) {
            final List<Map<String, Object>> actualMetrics = startAndGetMetrics(server, false);
            List<String> gcGenerations = Arrays.asList(
                "G1 Old Generation",
                "G1 Young Generation");
            assertGCMetric(actualMetrics, "jvm.gc.cms.count", gcGenerations);
            assertGCMetric(actualMetrics, "jvm.gc.parnew.time", gcGenerations);
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseParallelGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer.Builder().withJDKImage(
            JDK_11).appendJavaOpts("-XX:+UseParallelGC").build()) {
            final List<Map<String, Object>> actualMetrics = startAndGetMetrics(server, true);
            assertThat(actualMetrics, hasSize(13));
            assertGCMetric(actualMetrics,
                "jvm.gc.minor_collection_count", "PS Scavenge", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.minor_collection_time", "PS Scavenge", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.major_collection_count", "PS MarkSweep", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.major_collection_time", "PS MarkSweep", "counter");
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseConcMarkSweepGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer.Builder().withJDKImage(
            JDK_11).appendJavaOpts("-XX:+UseConcMarkSweepGC").build()) {
            final List<Map<String, Object>> actualMetrics = startAndGetMetrics(server, true);
            assertThat(actualMetrics, hasSize(13));
            assertGCMetric(actualMetrics,
                "jvm.gc.minor_collection_count", "ParNew", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.minor_collection_time", "ParNew", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.major_collection_count", "ConcurrentMarkSweep", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.major_collection_time", "ConcurrentMarkSweep", "counter");
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseG1GC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer.Builder().withJDKImage(
            JDK_17).appendJavaOpts("-XX:+UseG1GC").build()) {
            final List<Map<String, Object>> actualMetrics = startAndGetMetrics(server, true);
            assertThat(actualMetrics, hasSize(13));
            assertGCMetric(actualMetrics,
                "jvm.gc.minor_collection_count", "G1 Young Generation", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.minor_collection_time", "G1 Young Generation", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.major_collection_count", "G1 Old Generation", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.major_collection_time", "G1 Old Generation", "counter");
        }
    }

    @Test
    public void testDefaultNewGCMetricsUseZGC() throws IOException {
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer.Builder().withJDKImage(
            JDK_17).appendJavaOpts("-XX:+UseZGC").build()) {
            final List<Map<String, Object>> actualMetrics = startAndGetMetrics(server, true);
            assertThat(actualMetrics, hasSize(13));
            assertGCMetric(actualMetrics,
                "jvm.gc.zgc_pauses_collection_count", "ZGC Pauses", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.zgc_pauses_collection_time", "ZGC Pauses", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.zgc_cycles_collection_count", "ZGC Cycles", "counter");
            assertGCMetric(actualMetrics,
                "jvm.gc.zgc_cycles_collection_time", "ZGC Cycles", "counter");
        }
    }

    private List<Map<String, Object>> startAndGetMetrics(final MisbehavingJMXServer server,
        final boolean newGCMetrics) throws IOException {
        server.start();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "  new_gc_metrics: " + newGCMetrics,
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
        return ((ConsoleReporter) appConfig.getReporter()).getMetrics();
    }

    private static void assertGCMetric(final List<Map<String, Object>> actualMetrics,
        final String expectedMetric,
        final String gcGeneration,
        final String metricType) {
        MetricsAssert.assertMetric(
            expectedMetric,
            -1,
            -1,
            10.0,
            Collections.singletonList(String.format("name:%s", gcGeneration)),
            Arrays.asList(
                "instance:jmxint_container",
                "jmx_domain:java.lang",
                "type:GarbageCollector"),
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
