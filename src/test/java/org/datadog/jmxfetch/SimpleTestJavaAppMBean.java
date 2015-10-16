package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public interface SimpleTestJavaAppMBean {

    int getShouldBe100();

    Double getShouldBe1000();

    int getShouldBeCounter();

    String getShouldBeConverted();

    String getShouldBeDefaulted();

    boolean getShouldBeBoolean();

    HashMap<String, Integer> getHashmap();

    AtomicInteger getAtomic42();

    AtomicLong getAtomic4242();

    Object getObject1337();

    Number getNumberBig();

    Long getLong42424242();

    Integer getInt424242();

    float getPrimitiveFloat();

    Float getInstanceFloat();

}
