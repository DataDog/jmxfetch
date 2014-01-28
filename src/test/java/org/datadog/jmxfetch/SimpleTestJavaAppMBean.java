package org.datadog.jmxfetch;

import java.util.HashMap;

public interface SimpleTestJavaAppMBean {
    
    int getShouldBe100();
    Double getShouldBe1000();
    int getShouldBeCounter();
    HashMap<String, Integer> getHashmap();
    
}
