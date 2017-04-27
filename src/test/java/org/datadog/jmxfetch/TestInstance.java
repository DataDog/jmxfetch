package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.LinkedList;

import org.junit.Test;

public class TestInstance extends TestCommon {
	
	@Test
	public void testMinCollectionInterval() throws Exception {
		registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:foo=Bar,qux=Baz");
		initApplication("jmx_min_collection_period.yml");
		
		run();
		
		LinkedList<HashMap<String, Object>> metrics = getMetrics();
		assertEquals(15, metrics.size());
		metrics = new LinkedList<HashMap<String, Object>>();
		
		run();
		metrics = getMetrics();
		assertEquals(0, metrics.size());
		metrics = new LinkedList<HashMap<String, Object>>();
		
		Thread.sleep(300000);
		
		run();
		metrics = getMetrics();
		assertEquals(15, metrics.size());
		
	}

}
