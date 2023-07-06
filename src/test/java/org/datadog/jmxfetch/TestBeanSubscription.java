package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.junit.runner.Description;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import org.datadog.jmxfetch.reporter.ConsoleReporter;

@Slf4j
public class TestBeanSubscription extends TestCommon {
    private static final int rmiPort = 9090;
    private static final int controlPort = 9091;
    private static final int supervisorPort = 9092;
    private JMXServerControlClient controlClient;
    private JMXServerSupervisorClient supervisorClient;
    private static Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

    private static ImageFromDockerfile img = new ImageFromDockerfile()
        .withFileFromPath(".", Paths.get("./tools/misbehaving-jmx-server/"));

    @Rule(order = 0)
    public GenericContainer<?> cont = new GenericContainer<>(img)
        .withEnv(Collections.singletonMap("RMI_PORT", "" + rmiPort))
        .withEnv(Collections.singletonMap("CONTROL_PORT", "" + controlPort))
        .withEnv(Collections.singletonMap("SUPERVISOR_PORT", "" + supervisorPort))
        .waitingFor(Wait.forLogMessage(".*Supervisor HTTP Server Started. Waiting for initialization payload POST to /init.*", 1));

    @Rule(order = 1)
    public TestRule setupRule = new TestRule() {
        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
                    controlClient = new JMXServerControlClient(ipAddress, controlPort);
                    supervisorClient = new JMXServerSupervisorClient(ipAddress, supervisorPort);
                    cont.followOutput(logConsumer);
                    try {
                        log.info("Initializing JMX Server with RMI hostname {}", ipAddress);
                        supervisorClient.initializeJMXServer(ipAddress);
                    } catch (IOException e) {
                        log.warn("Supervisor call to set rmi hostname failed, tests may fail in some environments, e: ", e);
                    }
                    base.evaluate();
                }
            };
        }
    };

    @Test
    public void testJMXFetchBasic() throws IOException, InterruptedException {
        String testDomain = "test-domain";
        String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + ipAddress,
            "        port: " + rmiPort,
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
        String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + ipAddress,
            "        port: " + rmiPort,
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
        String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + ipAddress,
            "        port: " + rmiPort,
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

    @Test
    public void testBeanRemoval() throws IOException, InterruptedException {
        String testDomain = "test-domain";
        cont.followOutput(logConsumer);
        String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + ipAddress,
            "        port: " + rmiPort,
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
        assertEquals("Sanity check, no beans/metrics exist at beginning of test", 0, metrics.size());

        int numBeans = 20;
        int numAttributesPerBean = 4;
        int expectedMetrics = numBeans * numAttributesPerBean;

        this.controlClient.createMBeans(testDomain, numBeans);

        Thread.sleep(500);

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();

        assertEquals("After creating beans, correct metrics collected", expectedMetrics, metrics.size());

        numBeans = 10;
        expectedMetrics = numBeans * numAttributesPerBean;

        this.controlClient.createMBeans(testDomain, numBeans);

        Thread.sleep(500);

        assertEquals("Number of metrics to be collected properly updated", expectedMetrics, this.app.getInstances().get(0).getCurrentNumberOfMetrics());

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();

        assertEquals("After removing beans, correct metrics collected", expectedMetrics, metrics.size());
    }

    @Test
    public void testNetworkFailure() throws IOException, InterruptedException {
        String testDomain = "test-domain-nwkfail";
        cont.followOutput(logConsumer);
        String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + ipAddress,
            "        port: " + rmiPort,
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
        assertEquals("Sanity check, no beans/metrics exist at beginning of test", 0, metrics.size());

        this.controlClient.jmxCutNetwork();
        // In testing, this needs a slight delay for the connection to "fail"
        // via JMXConnectionNotification.
        // Artificially sleep to allow time for this since that is the point of this test
        Thread.sleep(50);
        this.controlClient.jmxRestoreNetwork();

        int numBeans = 20;
        int numAttributesPerBean = 4;
        int expectedMetrics = numBeans * numAttributesPerBean;

        this.controlClient.createMBeans(testDomain, numBeans);

        // One iteration to recover instance, no metrics are actually collected
        this.app.doIteration();
        // Second iteration should collect metrics
        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();

        assertEquals("Number of metrics to be collected properly updated", expectedMetrics, this.app.getInstances().get(0).getCurrentNumberOfMetrics());

        assertEquals("Network recovered, did we collect correct metrics?", expectedMetrics, metrics.size());
    }

    @Test
    public void testDisconnectDuringBeanCreation() throws IOException, InterruptedException {
        String testDomain = "test-domain-dsc-bn-creat";
        cont.followOutput(logConsumer);
        String ipAddress = cont.getContainerInfo().getNetworkSettings().getIpAddress();
        this.initApplicationWithYamlLines(
            "init_config:",
            "  is_jmx: true",
            "",
            "instances:",
            "    -   name: jmxint_container",
            "        host: " + ipAddress,
            "        port: " + rmiPort,
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
        assertEquals("Sanity check, no beans/metrics exist at beginning of test", 0, metrics.size());

        this.controlClient.jmxCutNetwork();

        int numBeans = 20;
        int numAttributesPerBean = 4;
        int expectedMetrics = numBeans * numAttributesPerBean;

        this.controlClient.createMBeans(testDomain, numBeans);

        // once beans created, restore network
        this.controlClient.jmxRestoreNetwork();

        // When attempting to collect metrics, instance is marked as broken due to an unhealthy network
        // _and_ it is added to brokenInstances so the broken instance is recovered in the _same_ iteration
        // Note in other "reconnection" tests (see TestReconnection) there are two iterations required
        // The first marks it as broken and the second recovers it. This test only needs one since getMetrics
        // fails so quickly. So this is technically a race and if this test ever becomes flakey this is why.
        this.app.doIteration();

        // Now create more beans which triggers subscription updates
        numBeans = 22;
        expectedMetrics = numBeans * numAttributesPerBean;
        this.controlClient.createMBeans(testDomain, numBeans);

        // Allow subscription updates to be processed
        Thread.sleep(500);

        assertEquals("Number of metrics to be collected properly updated", expectedMetrics, this.app.getInstances().get(0).getCurrentNumberOfMetrics());

        this.app.doIteration();
        metrics = ((ConsoleReporter) this.appConfig.getReporter()).getMetrics();

        assertEquals("Network recovered, did we collect correct metrics?", expectedMetrics, metrics.size());

    }
}
