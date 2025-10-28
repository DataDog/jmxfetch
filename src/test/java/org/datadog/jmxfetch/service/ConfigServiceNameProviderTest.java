package org.datadog.jmxfetch.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ConfigServiceNameProviderTest {

    private static class CustomConfigServiceNameProvider implements ServiceNameProvider {
        public Iterable<String> getServiceNames() {
            return new ArrayList<String>(Arrays.asList("foo-provider"));
        }
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                {
                    Collections.singletonMap("service", "foo-instance"),
                    Collections.singletonMap("service", "foo-init"),
                    null,
                    Arrays.asList("foo-instance")
                },
                {
                    Collections.singletonMap("not-service", "foo-instance"),
                    Collections.singletonMap("service", "foo-init"),
                    null,
                    Arrays.asList("foo-init")
                },
                {
                    Collections.singletonMap("not-service", "foo-instance"),
                    Collections.singletonMap("not-service", "foo-init"),
                    null,
                    new ArrayList<>()
                },
                {
                    Collections.singletonMap("service", Arrays.asList("foo1-instance", "foo2-instance")),
                    Collections.singletonMap("service", "foo-init"),
                    null,
                    Arrays.asList("foo1-instance", "foo2-instance")
                },
                {
                    Collections.singletonMap("service", Arrays.asList("foo1-instance", "foo2-instance")),
                    Collections.singletonMap("service", "foo-init"),
                    new ConfigServiceNameProviderTest.CustomConfigServiceNameProvider(),
                    Arrays.asList("foo1-instance", "foo2-instance", "foo-provider")
                },
        });
    }

    private final Map<String, Object> instanceMap;
    private final Map<String, Object> initConfigMap;
    private final ServiceNameProvider provider;
    private final List<String> expected;

    public ConfigServiceNameProviderTest(Map<String, Object> instanceMap, Map<String, Object> initConfigMap, ServiceNameProvider provider, List<String> expected) {
        this.instanceMap = instanceMap;
        this.initConfigMap = initConfigMap;
        this.provider = provider;
        this.expected = expected;
    }

    @Test
    public void testConfigServiceNameProvider() {
        ConfigServiceNameProvider configServiceNameProvider = new ConfigServiceNameProvider(
            instanceMap,
            initConfigMap,
            provider
        );

        assertEquals(expected, configServiceNameProvider.getServiceNames());
    }
}
