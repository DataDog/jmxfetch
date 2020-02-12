package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestConfiguration {
    static List<Configuration> configurations = new ArrayList<Configuration>();
    static JsonParser adConfigs;

    /**
     * Setup Configuration tests
     *
     * @throws FileNotFoundException
     */
    @SuppressWarnings("unchecked")
    @BeforeClass
    public static void init() throws FileNotFoundException, IOException {
        File f = new File("src/test/resources/", "jmx_bean_scope.yaml");
        String yamlPath = f.getAbsolutePath();
        FileInputStream yamlInputStream = new FileInputStream(yamlPath);
        YamlParser fileConfig = new YamlParser(yamlInputStream);
        List<Map<String, Object>> configInstances =
                ((List<Map<String, Object>>) fileConfig.getYamlInstances());

        for (Map<String, Object> config : configInstances) {
            Object yamlConf = config.get("conf");
            for (Map<String, Object> conf :
                    (List<Map<String, Object>>) (yamlConf)) {
                configurations.add(new Configuration(conf));
            }
        }

        // lets also collect auto-discovery configs
        f = new File("src/test/resources/", "auto_discovery_configs.json");
        String jsonPath = f.getAbsolutePath();
        FileInputStream jsonInputStream = new FileInputStream(jsonPath);
        adConfigs = new JsonParser(jsonInputStream);
    }

    /**
     * Stringify a bean pattern to comply with the representation of a MBean
     *
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    @Test
    public void testAutoDiscoveryConfigs()
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException {
        HashMap<String, Object> configs = (HashMap<String, Object>) adConfigs.getJsonConfigs();

        assertEquals(configurations.size(), 4);
        int nconfigs = 0;
        for (String check : configs.keySet()) {
            List<Map<String, Object>> configInstances =
                    ((List<Map<String, Object>>) adConfigs.getJsonInstances(check));
            for (Map<String, Object> config : configInstances) {
                Object jsonConf = config.get("conf");
                for (Map<String, Object> conf :
                        (List<Map<String, Object>>) (jsonConf)) {
                    configurations.add(new Configuration(conf));
                    nconfigs++;
                }
            }
        }
        assertEquals(configurations.size(), 4 + nconfigs);
    }

    /**
     * Extract filters from the configuration list and index by domain name
     *
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testFiltersByDomain()
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException {
        // Private method reflection
        Method getIncludeFiltersByDomain =
                Configuration.class.getDeclaredMethod(
                        "getIncludeFiltersByDomain", List.class);
        getIncludeFiltersByDomain.setAccessible(true);

        // Assert
        HashMap<String, List<Filter>> filtersByDomain =
                (HashMap<String, List<Filter>>)
                        getIncludeFiltersByDomain.invoke(null, configurations);

        // Only contains 'org.datadog.jmxfetch.test' domain
        assertEquals(filtersByDomain.size(), 1);
        assertTrue(filtersByDomain.containsKey("org.datadog.jmxfetch.test"));

        // 5 filters associated: 4 `include`s (but the last one is split in two)
        assertEquals(filtersByDomain.get("org.datadog.jmxfetch.test").size(), 5);
    }

    /**
     * Extract common bean keys among a given list of filters.
     *
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCommonBeanKeys()
            throws FileNotFoundException, NoSuchMethodException, SecurityException,
                    IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        // Private method reflection
        Method getIncludeFiltersByDomain =
                Configuration.class.getDeclaredMethod(
                        "getIncludeFiltersByDomain", List.class);
        getIncludeFiltersByDomain.setAccessible(true);

        Method getCommonBeanKeysByDomain =
                Configuration.class.getDeclaredMethod("getCommonBeanKeysByDomain", Map.class);
        getCommonBeanKeysByDomain.setAccessible(true);

        // Assert
        HashMap<String, List<Filter>> filtersByDomain =
                (HashMap<String, List<Filter>>)
                        getIncludeFiltersByDomain.invoke(null, configurations);
        HashMap<String, Set<String>> parametersIntersectionByDomain =
                (HashMap<String, Set<String>>)
                        getCommonBeanKeysByDomain.invoke(null, filtersByDomain);

        // Only contains 'org.datadog.jmxfetch.test' domain
        assertEquals(parametersIntersectionByDomain.size(), 2);
        assertTrue(parametersIntersectionByDomain.containsKey("org.datadog.jmxfetch.test"));

        // Parameters intersection should match: 'param', 'scope' and 'type'
        Set<String> parameters = parametersIntersectionByDomain.get("org.datadog.jmxfetch.test");
        assertEquals(parameters.size(), 3);
        assertTrue(parameters.contains("param"));
        assertTrue(parameters.contains("scope"));
        assertTrue(parameters.contains("type"));
    }

    /**
     * Extract common bean keys among a given list of filters.
     *
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCommonScope()
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException {
        // Private method reflection
        Method getIncludeFiltersByDomain =
                Configuration.class.getDeclaredMethod(
                        "getIncludeFiltersByDomain", List.class);
        getIncludeFiltersByDomain.setAccessible(true);

        Method getCommonBeanKeysByDomain =
                Configuration.class.getDeclaredMethod("getCommonBeanKeysByDomain", Map.class);
        getCommonBeanKeysByDomain.setAccessible(true);

        Method getCommonScopeByDomain =
                Configuration.class.getDeclaredMethod(
                        "getCommonScopeByDomain", Map.class, Map.class);
        getCommonScopeByDomain.setAccessible(true);

        // Assert
        Map<String, List<Filter>> filtersByDomain =
                (Map<String, List<Filter>>)
                        getIncludeFiltersByDomain.invoke(null, configurations);
        Map<String, Set<String>> parametersIntersectionByDomain =
                (Map<String, Set<String>>)
                        getCommonBeanKeysByDomain.invoke(null, filtersByDomain);
        Map<String, Map<String, String>> commonBeanScopeByDomain =
                (Map<String, Map<String, String>>)
                        getCommonScopeByDomain.invoke(
                                null, parametersIntersectionByDomain, filtersByDomain);

        // Only contains 'org.datadog.jmxfetch.test' domain
        assertEquals(commonBeanScopeByDomain.size(), 2);
        assertTrue(commonBeanScopeByDomain.containsKey("org.datadog.jmxfetch.test"));
        Map<String, String> beanScope =
                commonBeanScopeByDomain.get("org.datadog.jmxfetch.test");

        // Bean scope contains 'scope' parameter only
        assertEquals(beanScope.size(), 1);
        assertTrue(beanScope.containsKey("scope"));

        // Common value is 'sameScope'
        assertEquals(beanScope.get("scope"), "sameScope");
    }

    /**
     * Stringify a bean pattern to comply with the representation of a MBean
     *
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    @Test
    public void testBeanScopeToString()
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException {
        // Private method reflection
        Method beanScopeToString =
                Configuration.class.getDeclaredMethod(
                        "beanScopeToString", String.class, Map.class);
        beanScopeToString.setAccessible(true);

        // Mock parameters
        Map<String, String> beanScope = new HashMap<String, String>();
        beanScope.put("type", "someType");
        beanScope.put("param", "someParam");

        // No domain name, no parameters
        assertEquals(
                (String) beanScopeToString.invoke(null, null, new HashMap<String, String>()),
                "*:*");

        // No domain but parameters
        String scopeStr = (String) beanScopeToString.invoke(null, null, beanScope);
        // order is not guaranteed, so just checking we find it into the result string
        assertTrue(scopeStr.contains("type=someType"));
        assertTrue(scopeStr.contains("param=someParam"));

        // Domain name with no parameters
        assertEquals(
                (String)
                        beanScopeToString.invoke(
                                null, "org.datadog.com", new HashMap<String, String>()),
                "org.datadog.com:*");

        // Domain name with parameters
        scopeStr = (String) beanScopeToString.invoke(null, "org.datadog.com", beanScope);
        // order is not guaranteed, so just checking we find it into the result string
        assertTrue(scopeStr.startsWith("org.datadog.com:"));
        assertTrue(scopeStr.contains("type=someType"));
        assertTrue(scopeStr.contains("param=someParam"));
    }
}
