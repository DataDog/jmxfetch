package org.datadog.jmxfetch;

import java.util.HashMap;

public interface SimpleTestJavaAppMBean {
	
	int getShouldBe100();
	int getShouldBe1000();
	int getShouldBeCounter();
	HashMap<String, Integer> getHashmap();
	
}
