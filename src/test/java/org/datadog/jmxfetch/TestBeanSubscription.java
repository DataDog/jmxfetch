package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import org.datadog.jmxfetch.reporter.ConsoleReporter;

@Slf4j
public class TestBeanSubscription extends TestCommon {
    private static final int rmiPort = 9090;
    private static final int controlPort = 9091;
    private JMXServerControlClient controlClient;
    private static Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

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
    public void testJMXFetchBasic() throws IOException, InterruptedException {
        String testDomain = "test-domain";
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + cont.getHost(),
            "        port: " + cont.getMappedPort(rmiPort),
            "        min_collection_interval: null", // allow collections at arbitrary intervals since we trigger them manually in the tests
            "        enable_bean_subscription: true",
            "        refresh_beans: 5000", // effectively disable bean refresh
            "        collect_default_jvm_metrics: false",
            "        max_returned_metrics: 300000",
            "        conf:",
            "          - include:",
            "              domain: " + testDomain,
            "              attribute:",
            "                - DoubleValue",
            "                - NumberValue",
            "                - FloatValue",
            "                - BooleanValue"
        );

        log.info("hello");
        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(0, metrics.size());

        int numBeans = 2;
        int numAttributesPerBean = 4;

        this.controlClient.createMBeans(testDomain, numBeans);

        // Allow time for subscriptions to come through and be registered
        Thread.sleep(100);

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(numBeans * numAttributesPerBean, metrics.size());
    }
    
    @Test
    public void testJMXFetchManyBeans() throws IOException, InterruptedException {
        cont.followOutput(logConsumer);
        String testDomain = "test-domain";
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + cont.getHost(),
            "        port: " + cont.getMappedPort(rmiPort),
            "        min_collection_interval: null",
            "        enable_bean_subscription: true",
            "        refresh_beans: 5000", // effectively disable bean refresh
            "        collect_default_jvm_metrics: false",
            "        max_returned_metrics: 300000",
            "        conf:",
            "          - include:",
            "              domain: " + testDomain,
            "              attribute:",
            "                - DoubleValue",
            "                - NumberValue",
            "                - FloatValue",
            "                - BooleanValue"
        );

        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(0, metrics.size());

        int numBeans = 200;
        int numAttributesPerBean = 4;

        this.controlClient.createMBeans(testDomain, numBeans);

        // Time for subscriptions to come through and be registered
        Thread.sleep(2000);

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        assertEquals(numBeans * numAttributesPerBean, metrics.size());
    }

    @Test
    public void testConcurrentCollectionWithSubscriptionUpdates() throws IOException, InterruptedException {
        String testDomain = "test-domain";
        cont.followOutput(logConsumer);
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + cont.getHost(),
            "        port: " + cont.getMappedPort(rmiPort),
            "        min_collection_interval: null",
            "        enable_bean_subscription: true",
            "        refresh_beans: 5000", // effectively disable bean refresh
            "        collect_default_jvm_metrics: false",
            "        max_returned_metrics: 300000",
            "        conf:",
            "          - include:",
            "              domain: " + testDomain,
            "              attribute:",
            "                - DoubleValue",
            "                - NumberValue",
            "                - FloatValue",
            "                - BooleanValue"
        );

        this.app.doIteration();
        List<Map<String, Object>> metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();
        // Sanity check, no beans exist, none should be found
        assertEquals(0, metrics.size());

        int numBeans = 200;
        int numAttributesPerBean = 4;
        int expectedMetrics = numBeans * numAttributesPerBean;

        // This call blocks until the beans actually exist in the remote application
        this.controlClient.createMBeans(testDomain, numBeans);

        // Intentionally leaving no time for subscriptions to be processed to test how
        // a collection behaves when interleaved with bean subscription traffic

        this.app.doIteration();
        // Iteration is done, don't care how many metrics were collected
        // (almost certainly less than numBeans * numAttributesPerBean)
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();

        // Sleep to ensure bean subscriptions get processed (ie, attribute matching correctly takes place)
        Thread.sleep(2000);

        // Now, without doing an iteration, we should see the correct number for
        // how many metrics are about to be collected
        // This is effectively testing "Did the attribute matching execute correctly for all bean notifications"
        assertEquals("getCurrentNumberOfMetrics returns the correct value _before_ running a collection", expectedMetrics, this.app.getInstances().get(0).getCurrentNumberOfMetrics());

        // Do an actual collection to ensure validate the metrics collected
        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();

        assertEquals("actual metrics collected matches expectedMetrics", expectedMetrics, metrics.size());
    }
}
