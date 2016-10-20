package org.datadog.jmxfetch;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleTestJavaApp implements SimpleTestJavaAppMBean {

    // Integers
    private final int shouldBe100 = 100;
    private final int shouldBe1000 = 1000;
    private final Integer int424242 = new Integer(424242);
    private final AtomicInteger atomic42 = new AtomicInteger(42);

    private int shouldBeCounter = 0;

    // Floats & Long
    private final float primitiveFloat = 123.4f;
    private final Float instanceFloat = 567.8f;
    private final AtomicLong atomic4242 = new AtomicLong(4242);
    private final Long long42424242 = new Long(42424242);

    // String
    private final String shouldBeConverted = "ShouldBe5";
    private final String shouldBeDefaulted = "DefaultMe";

    // Others
    private final boolean shouldBeBoolean = true;
    private final HashMap<String, Integer> hashmap = new HashMap<String, Integer>();
    private final Object object1337 = new Double(13.37);
    private final BigDecimal numberBig = new BigDecimal(123456788901234567890.0);
    private final TabularData tabulardata;
    private final CompositeType compositetype;

    SimpleTestJavaApp() {
        hashmap.put("thisis0", 0);
        hashmap.put("thisis10", 10);
        hashmap.put("thisiscounter", 0);
        compositetype = buildCompositeType();
        tabulardata = buildTabularType();
        if (tabulardata != null) {
            tabulardata.put(buildCompositeData(1));
        }
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

    public void populateHashMap(int count) {
        for (Integer i =1;i <= count ; i++ ) {
            hashmap.put(i.toString(), i);
        }
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

    public float getPrimitiveFloat(){
        return primitiveFloat;
    }

    public Float getInstanceFloat(){
        return instanceFloat;
    }

    private TabularData buildTabularType() {
        try {
            CompositeType rowType = buildCompositeType();
            TabularType tabularType = new TabularType("myTabularType", "My tabular type", rowType,
                    new String[]{"thisiskey"});
            return new TabularDataSupport(tabularType);
        } catch (OpenDataException e) {
            return null;
        }
    }

    private CompositeType buildCompositeType() {
        try {
            return new CompositeType("myCompositeType", "My composite type",
                    new String[]{"thisiskey", "thisisx"},
                    new String[]{"This is key", "This is x"},
                    new OpenType[]{SimpleType.STRING, SimpleType.INTEGER});
        } catch (OpenDataException e) {
            return null;
        }
    }

    private CompositeData buildCompositeData(Integer i) {
        try {
            return new CompositeDataSupport(compositetype,
                    new String[]{"thisiskey", "thisisx"},
                    new Object[]{i.toString(), i});
        } catch (OpenDataException e) {
            return null;
        }
    }

    public void populateTabularData(int count) {
        tabulardata.clear();
        for (Integer i = 1; i <= count; i++) {
            tabulardata.put(buildCompositeData(i));
        }
    }

    public TabularData getTabulardata() { return tabulardata; }
}
