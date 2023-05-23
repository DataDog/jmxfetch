package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TestInstance extends TestCommon {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Test Instance");

    // This delay exists for the subscription thread to recieve the MBeanNotification
    // and call into `Instance`. Conceptually this is just a Thread.yield()
    private static int subscriptionDelayMs = 50;

    @Test
    public void testMinCollectionInterval() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_min_collection_period.yml");

        run();
        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(15, metrics.size());

        run();
        metrics = getMetrics();
        assertEquals(0, metrics.size());

        log.info("sleeping before the next collection");
        Thread.sleep(5000);
        run();
        metrics = getMetrics();
        assertEquals(15, metrics.size());
    }

    // assertHostnameTags is used by testEmptyDefaultHostname
    private void assertHostnameTags(List<String> tagList) throws Exception {
        // Fixed instance tag
        assertTrue(tagList.contains(new String("jmx:fetch")));

        if (tagList.contains(new String("instance:jmx_test_default_hostname"))) {
            // Nominal case
            assertFalse(tagList.contains(new String("host:")));
        } else if (tagList.contains(new String("instance:jmx_test_no_hostname"))) {
            // empty_default_hostname case
            assertTrue(tagList.contains(new String("host:")));
        } else {
            fail("unexpected instance tag");
        }
    }

    @Test
    public void testEmptyDefaultHostname() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_empty_default_hostname.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertHostnameTags(Arrays.asList(tags));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertHostnameTags(Arrays.asList(tags));
        }
    }

    // assertServiceTags is used by testServiceTagGlobal and testServiceTagInstanceOverride
    private void assertServiceTag(List<String> tagList, List<String> services) throws Exception {
        for (String service: services) {
            assertTrue(tagList.contains(new String("service:" + service)));
        }
    }

    @Test
    public void testServiceTagGlobal() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_service_tag_global.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global"));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global"));
        }
    }

    @Test
    public void testServiceTagGlobalList() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_service_tag_global_list.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global", "foo", "bar"));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("global", "foo", "bar"));
        }
    }

    @Test
    public void testServiceTagInstanceOverride() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
        initApplication("jmx_service_tag_instance_override.yaml");
        run();

        List<Map<String, Object>> metrics = getMetrics();
        assertEquals(28, metrics.size());
        for (Map<String, Object> metric : metrics) {
            String[] tags = (String[]) metric.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("override"));
        }

        List<Map<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(4, serviceChecks.size());
        for (Map<String, Object> sc : serviceChecks) {
            String[] tags = (String[]) sc.get("tags");
            this.assertServiceTag(Arrays.asList(tags), Arrays.asList("override"));
        }
    }

    @Test
    public void testLoadMetricConfigFiles() throws Exception {
        URL defaultConfig = Instance.class.getResource("default-jmx-metrics.yaml");
        AppConfig config = mock(AppConfig.class);
        when(config.getMetricConfigFiles()).thenReturn(Collections.singletonList(defaultConfig.getPath()));
        List<Configuration> configurationList = new ArrayList<Configuration>();
        Instance.loadMetricConfigFiles(config, configurationList);

        assertEquals(2, configurationList.size());
    }

    @Test
    public void testLoadMetricConfigResources() throws Exception {
        URL defaultConfig = Instance.class.getResource("sample-metrics.yaml");
        String configResource = defaultConfig.getPath().split("test-classes/")[1];
        AppConfig config = mock(AppConfig.class);
        when(config.getMetricConfigResources()).thenReturn(Collections.singletonList(configResource));
        List<Configuration> configurationList = new ArrayList<Configuration>();
        Instance.loadMetricConfigResources(config, configurationList);

        assertEquals(2, configurationList.size());
    }

    /** Tests refresh_beans_initial and the following refresh_beans */
    @Test
    public void testRefreshBeans() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        initApplication("jmx_refresh_beans.yaml");

        // We do a first collection
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // 13 metrics from java.lang
        assertEquals(13, metrics.size());

        // We register an additional mbean
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        log.info("sleeping before the next collection");
        Thread.sleep(1500);

        // We run a second collection. refresh_beans_initial should be due.
        run();
        metrics = getMetrics();

        // 15 = 13 metrics from java.lang + 2 iteration=one
        assertEquals(15, metrics.size());

        // We register additional mbean
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=two");
        log.info("sleeping before the next collection");
        Thread.sleep(1500);

        // We run a third collection. No change expected; refresh_beans not due.
        run();
        metrics = getMetrics();

        // 15 = 13 metrics from java.lang + 2 iteration=one
        assertEquals(15, metrics.size());

        log.info("sleeping before the next collection");
        Thread.sleep(1500);

        // We run the last collection. refresh_beans should be due.
        run();
        metrics = getMetrics();

        // 17 = 13 metrics from java.lang + 2 iteration=one + 2 iteration=two
        assertEquals(17, metrics.size());
    }

    /** Tests bean_subscription */
    @Test
    public void testBeanSubscription() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        // initial fetch
        initApplication("jmx_bean_subscription.yaml");

        // We do a first collection
        run();
        List<Map<String, Object>> metrics = getMetrics();
        int numRegisteredAttributes = 0;

        assertEquals(numRegisteredAttributes, metrics.size());

        // We register first mbean with 2 matching attributes
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        numRegisteredAttributes += 2;

        Thread.sleep(subscriptionDelayMs);

        run();
        metrics = getMetrics();

        assertEquals(numRegisteredAttributes, metrics.size());

        // We register additional mbean
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=two");
        numRegisteredAttributes += 2;
        Thread.sleep(subscriptionDelayMs);

        // We run a third collection.
        run();
        metrics = getMetrics();

        assertEquals(numRegisteredAttributes, metrics.size());
    }

    @Test
    public void testBeanUnsubscription() throws Exception {
        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        // Bean-refresh interval is to to 50s, so the only bean refresh will be
        // initial fetch
        initApplication("jmx_bean_subscription.yaml");
        int numRegisteredAttributes = 0;

        // We do a first collection
        run();
        List<Map<String, Object>> metrics = getMetrics();

        // Sanity check -- no beans exist yet
        assertEquals(numRegisteredAttributes, metrics.size());

        // We register an additional mbean
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        numRegisteredAttributes += 2;

        Thread.sleep(subscriptionDelayMs);

        run();
        metrics = getMetrics();

        assertEquals(numRegisteredAttributes, metrics.size());

        // We UN-register first mbean
        unregisterMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        numRegisteredAttributes -= 2;
        Thread.sleep(subscriptionDelayMs);

        // We run a third collection.
        run();
        metrics = getMetrics();

        // Note that the collected metrics size is correct even without any special work for bean
        // unregistration as the `iteration=one` metric will fail to be found and simply not be included
        assertEquals(numRegisteredAttributes, metrics.size());
        // Which is why this second check exists, it reflects the running total of metrics
        // collected which is used to determine if new attributes can be added
        Instance i = app.getInstances().get(0);
        assertEquals(numRegisteredAttributes, i.getCurrentNumberOfMetrics());
    }

    @Test
    public void testBeanSubscriptionAttributeCounting() throws Exception {
        // This test only looks at the instance's getCurrentNumberOfMetrics as this
        // should be updated on bean registration/deregistration

        SimpleTestJavaApp testApp = new SimpleTestJavaApp();
        initApplication("jmx_bean_subscription.yaml"); // max returned metrics is 10

        int numRegisteredAttributes = 0;
        assertEquals(numRegisteredAttributes, app.getInstances().get(0).getCurrentNumberOfMetrics());

        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        numRegisteredAttributes += 2;
        Thread.sleep(subscriptionDelayMs);
        assertEquals(numRegisteredAttributes, app.getInstances().get(0).getCurrentNumberOfMetrics());

        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=two");
        numRegisteredAttributes += 2;
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=three");
        numRegisteredAttributes += 2;
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=four");
        numRegisteredAttributes += 2;
        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=five");
        numRegisteredAttributes += 2;

        Thread.sleep(subscriptionDelayMs * 2); // wait longer bc more beans
        assertEquals(numRegisteredAttributes, app.getInstances().get(0).getCurrentNumberOfMetrics());


        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=six");
        // no change to numRegisteredAttributes as this registration should FAIL due to max number of metrics

        Thread.sleep(subscriptionDelayMs);
        assertEquals(numRegisteredAttributes, app.getInstances().get(0).getCurrentNumberOfMetrics());

        unregisterMBean(testApp, "org.datadog.jmxfetch.test:iteration=one");
        numRegisteredAttributes -= 2;
        Thread.sleep(subscriptionDelayMs);
        assertEquals(numRegisteredAttributes, app.getInstances().get(0).getCurrentNumberOfMetrics());

        registerMBean(testApp, "org.datadog.jmxfetch.test:iteration=seven");
        numRegisteredAttributes += 2;
        Thread.sleep(subscriptionDelayMs);
        assertEquals(numRegisteredAttributes, app.getInstances().get(0).getCurrentNumberOfMetrics());
    }
}
