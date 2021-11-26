package org.datadog.jmxfetch.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ConfigServiceNameProviderTest {

    @Parameterized.Parameters
    public static Iterable<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                {
                    Collections.singletonMap("service", "foo-instance"),
                    Collections.singletonMap("service", "foo-init"),
                    Arrays.asList("foo-instance")
                },
                {
                    Collections.singletonMap("not-service", "foo-instance"),
                    Collections.singletonMap("service", "foo-init"),
                    Arrays.asList("foo-init")
                },
                {
                    Collections.singletonMap("not-service", "foo-instance"),
                    Collections.singletonMap("not-service", "foo-init"),
                    new ArrayList<>()
                },
                {
                    Collections.singletonMap("service", Arrays.asList("foo1-instance", "foo2-instance")),
                    Collections.singletonMap("service", "foo-init"),
                    Arrays.asList("foo1-instance", "foo2-instance")
                },
        });
    }

    private final Map<String, Object> instanceMap;
    private final Map<String, Object> initConfigMap;
    private final List<String> expected;

    public ConfigServiceNameProviderTest(Map<String, Object> instanceMap, Map<String, Object> initConfigMap, List<String> expected) {
        this.instanceMap = instanceMap;
        this.initConfigMap = initConfigMap;
        this.expected = expected;
    }

    @Test
    public void testConfigServiceNameProvider() {
        ConfigServiceNameProvider configServiceNameProvider = new ConfigServiceNameProvider(
            instanceMap,
            initConfigMap
        );

        assertEquals(expected, configServiceNameProvider.getServiceNames());
    }
}
