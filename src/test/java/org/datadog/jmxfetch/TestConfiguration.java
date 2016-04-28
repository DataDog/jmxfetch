package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

public class TestConfiguration {
	static LinkedList<Configuration> configurations = new LinkedList<Configuration>();

	/**
	 * Setup Configuration tests
	 * @throws FileNotFoundException
	 */
	@SuppressWarnings("unchecked")
	@BeforeClass
    public static void init() throws FileNotFoundException {
		File f = new File("src/test/resources/", "jmx_bean_scope.yaml");
		String yamlPath = f.getAbsolutePath();
		FileInputStream yamlInputStream = new FileInputStream(yamlPath);
		YamlParser fileConfig = new YamlParser(yamlInputStream);
		ArrayList<HashMap<String, Object>> configInstances = ((ArrayList<HashMap<String, Object>>) fileConfig.getYamlInstances());

		for (HashMap<String, Object> config : configInstances) {
			Object yamlConf = config.get("conf");
			for (HashMap<String, Object> conf : (ArrayList<HashMap<String, Object>>) (yamlConf)) {
			    configurations.add(new Configuration(conf));
			}
		}
    }

	/**
	 * Extract filters from the configuration list and index by domain name
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testFiltersByDomain() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		// Private method reflection
		Method getIncludeFiltersByDomain = Configuration.class.getDeclaredMethod("getIncludeFiltersByDomain", LinkedList.class);
		getIncludeFiltersByDomain.setAccessible(true);

		// Assert
		HashMap<String, LinkedList<Filter>> filtersByDomain = (HashMap<String, LinkedList<Filter>>) getIncludeFiltersByDomain.invoke(null, configurations);

		// Only contains 'org.datadog.jmxfetch.test' domain
		assertEquals(filtersByDomain.size(), 1);
		assertTrue(filtersByDomain.containsKey("org.datadog.jmxfetch.test"));

		// 5 filters associated: 4 `include`s (but the last one is split in two)
		assertEquals(filtersByDomain.get("org.datadog.jmxfetch.test").size(), 5);
	}


	/**
	 * Extract common bean keys among a given list of filters.
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testCommonBeanKeys() throws FileNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		// Private method reflection
		Method getIncludeFiltersByDomain = Configuration.class.getDeclaredMethod("getIncludeFiltersByDomain", LinkedList.class);
		getIncludeFiltersByDomain.setAccessible(true);

		Method getCommonBeanKeysByDomain = Configuration.class.getDeclaredMethod("getCommonBeanKeysByDomain", HashMap.class);
		getCommonBeanKeysByDomain.setAccessible(true);

		// Assert
		HashMap<String, LinkedList<Filter>> filtersByDomain = (HashMap<String, LinkedList<Filter>>) getIncludeFiltersByDomain.invoke(null, configurations);
		HashMap<String, Set<String>> parametersIntersectionByDomain = (HashMap<String, Set<String>>) getCommonBeanKeysByDomain.invoke(null, filtersByDomain);

		// Only contains 'org.datadog.jmxfetch.test' domain
		assertEquals(parametersIntersectionByDomain.size(), 1);
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
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testCommonScope() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		// Private method reflection
		Method getIncludeFiltersByDomain = Configuration.class.getDeclaredMethod("getIncludeFiltersByDomain", LinkedList.class);
		getIncludeFiltersByDomain.setAccessible(true);

		Method getCommonBeanKeysByDomain = Configuration.class.getDeclaredMethod("getCommonBeanKeysByDomain", HashMap.class);
		getCommonBeanKeysByDomain.setAccessible(true);

		Method getCommonScopeByDomain = Configuration.class.getDeclaredMethod("getCommonScopeByDomain", HashMap.class, HashMap.class);
		getCommonScopeByDomain.setAccessible(true);

		// Assert
		HashMap<String, LinkedList<Filter>> filtersByDomain = (HashMap<String, LinkedList<Filter>>) getIncludeFiltersByDomain.invoke(null, configurations);
		HashMap<String, Set<String>> parametersIntersectionByDomain = (HashMap<String, Set<String>>) getCommonBeanKeysByDomain.invoke(null, filtersByDomain);
		HashMap<String, HashMap<String, String>> commonBeanScopeByDomain = (HashMap<String, HashMap<String, String>>) getCommonScopeByDomain.invoke(null, parametersIntersectionByDomain, filtersByDomain);

		// Only contains 'org.datadog.jmxfetch.test' domain
		assertEquals(commonBeanScopeByDomain.size(), 1);
		assertTrue(commonBeanScopeByDomain.containsKey("org.datadog.jmxfetch.test"));
		HashMap<String, String> beanScope = commonBeanScopeByDomain.get("org.datadog.jmxfetch.test");

		// Bean scope contains 'scope' parameter only
		assertEquals(beanScope.size(), 1);
		assertTrue(beanScope.containsKey("scope"));

		// Common value is 'sameScope'
		assertEquals(beanScope.get("scope"), "sameScope");

	}

	/**
	 * Stringify a bean pattern to comply with the representation of a MBean
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	@Test
	public void testBeanScopeToString() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		// Private method reflection
		Method beanScopeToString = Configuration.class.getDeclaredMethod("beanScopeToString", String.class, HashMap.class);
		beanScopeToString.setAccessible(true);

		// Mock parameters
		HashMap<String, String> beanScope = new HashMap<String, String>();
		beanScope.put("type", "someType");
		beanScope.put("param", "someParam");

		// No domain name, no parameters
		assertEquals((String) beanScopeToString.invoke(null, null, new HashMap<String, String>()), "*:*");

		// No domain but parameters
		assertEquals((String) beanScopeToString.invoke(null, null, beanScope), "*:param=someParam,type=someType,*");

		// Domain name with no parameters
		assertEquals((String) beanScopeToString.invoke(null, "org.datadog.com", new HashMap<String, String>()), "org.datadog.com:*");

		// Domain name with parameters
		assertEquals((String) beanScopeToString.invoke(null, "org.datadog.com", beanScope), "org.datadog.com:param=someParam,type=someType,*");
	}
}
