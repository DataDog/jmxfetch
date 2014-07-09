package org.datadog.jmxfetch;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleTestJavaApp implements SimpleTestJavaAppMBean {

    private final int shouldBe100 = 100;
    private final int shouldBe1000 = 1000;
    private int shouldBeCounter = 0;
    private final String shouldBeConverted = "ShouldBe5";
    private final String shouldBeDefaulted = "DefaultMe";
    private final boolean shouldBeBoolean = true;
    private final HashMap<String, Integer> hashmap = new HashMap<String, Integer>();
    private final AtomicInteger atomic42 = new AtomicInteger(42);
    private final AtomicLong atomic4242 = new AtomicLong(4242);
    private final Object object1337 = new Double(13.37);
    private final Long long42424242 = new Long(42424242);
    private final Integer int424242 = new Integer(424242);
    private final BigDecimal numberBig = new BigDecimal(123456788901234567890.0);

    SimpleTestJavaApp() {
        hashmap.put("thisis0", 0);
        hashmap.put("thisis10", 10);
        hashmap.put("thisiscounter", 0);
    }

    public int getShouldBe100() {
        return shouldBe100;
    }

    public Double getShouldBe1000() {
        return Double.valueOf(shouldBe1000);
    }

    public int getShouldBeCounter() {
        return shouldBeCounter;
    }

    public String getShouldBeConverted() {
        return shouldBeConverted;
    }

    public String getShouldBeDefaulted() {
        return shouldBeDefaulted;
    }

    public boolean getShouldBeBoolean() {
        return shouldBeBoolean;
    }

    public void incrementCounter(int inc) {
        shouldBeCounter += inc;
    }

    public void incrementHashMapCounter(int inc) {
        hashmap.put("thisiscounter", hashmap.get("thisiscounter") + inc);
    }

    public HashMap<String, Integer> getHashmap() {
        return hashmap;
    }

    public AtomicInteger getAtomic42() {
        return atomic42;
    }

    public AtomicLong getAtomic4242() {
        return atomic4242;
    }

    public Object getObject1337() {
        return object1337;
    }

    public Number getNumberBig() {
        return numberBig;
    }

    public Long getLong42424242() {
        return long42424242;
    }

    public Integer getInt424242() {
        return int424242;
    }


}
