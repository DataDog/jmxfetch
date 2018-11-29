package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Test;

public class TestInstance extends TestCommon {
	private final static Logger LOGGER = Logger.getLogger("Test Instance");

	@Test
	public void testMinCollectionInterval() throws Exception {
		registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
		initApplication("jmx_min_collection_period.yml");

		run();
		LinkedList<HashMap<String, Object>> metrics = getMetrics();
		assertEquals(15, metrics.size());
		
		run();
		metrics = getMetrics();
		assertEquals(0, metrics.size());

		LOGGER.info("sleeping before the next collection");
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

		LinkedList<HashMap<String, Object>> metrics = getMetrics();
		assertEquals(28, metrics.size());
		for (HashMap<String, Object> metric : metrics) {
			String[] tags = (String[]) metric.get("tags");
			this.assertHostnameTags(Arrays.asList(tags));
		}

        LinkedList<HashMap<String, Object>> serviceChecks = getServiceChecks();
        assertEquals(2, serviceChecks.size());
        for (HashMap<String, Object> sc : serviceChecks) {
			String[] tags = (String[]) sc.get("tags");
			this.assertHostnameTags(Arrays.asList(tags));
        }
	}
}
