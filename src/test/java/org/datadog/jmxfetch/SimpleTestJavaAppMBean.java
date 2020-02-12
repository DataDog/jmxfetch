package org.datadog.jmxfetch;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.openmbean.TabularData;

public interface SimpleTestJavaAppMBean {

    int getShouldBe100();

    Double getShouldBe1000();

    int getShouldBeCounter();

    String getShouldBeConverted();

    String getShouldBeDefaulted();

    boolean getShouldBeBoolean();

    Map<String, Integer> getHashmap();

    AtomicInteger getAtomic42();

    AtomicLong getAtomic4242();

    Object getObject1337();

    Number getNumberBig();

    Long getLong42424242();

    Integer getInt424242();

    float getPrimitiveFloat();

    Float getInstanceFloat();

    TabularData getTabulardata();
}
