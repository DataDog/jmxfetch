package org.datadog.jmxfetch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;


import java.util.List;
import java.util.Map;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.util.MisbehavingJMXServer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import lombok.extern.slf4j.Slf4j;
import org.junit.runners.Parameterized.Parameters;


@Slf4j
@RunWith(Parameterized.class)
public class TestJREs extends TestCommon{

    private static final int RMI_PORT = 9090;
    private static final int CONTROL_PORT = 9091;
    private static final int SUPERVISOR_PORT = 9092;
    private String jdkImage;

    @Parameters
    public static Object[] data() {
        return new Object[]{"eclipse-temurin:11", "eclipse-temurin:17", "eclipse-temurin:21"};
    }
    public TestJREs(String jdkImage) {
        this.jdkImage = jdkImage;
    }

    @Test
    public void testJRE() throws Exception {
        log.info("Testing on JRE Docker image '{}'", this.jdkImage);
        try (final MisbehavingJMXServer server = new MisbehavingJMXServer(this.jdkImage, RMI_PORT, CONTROL_PORT,
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
}
