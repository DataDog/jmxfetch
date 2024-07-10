package org.datadog.jmxfetch;

import org.datadog.jmxfetch.jee.BoundaryStatisticImpl;
import org.datadog.jmxfetch.jee.BoundedRangeStatisticImpl;
import org.datadog.jmxfetch.jee.CountStatisticImpl;
import org.datadog.jmxfetch.jee.JeeStats;
import org.datadog.jmxfetch.jee.RangeStatisticImpl;
import org.datadog.jmxfetch.jee.TimeStatisticImpl;
import org.datadog.jmxfetch.jee.UnsupportedStatisticImpl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.j2ee.statistics.BoundaryStatistic;
import javax.management.j2ee.statistics.BoundedRangeStatistic;
import javax.management.j2ee.statistics.CountStatistic;
import javax.management.j2ee.statistics.RangeStatistic;
import javax.management.j2ee.statistics.Statistic;
import javax.management.j2ee.statistics.Stats;
import javax.management.j2ee.statistics.TimeStatistic;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

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
    private final Map<String, Integer> hashmap = new HashMap<String, Integer>();
    private final Object object1337 = new Double(13.37);
    private final BigDecimal numberBig = new BigDecimal(123456788901234567890.0);
    private final TabularDataSupport tabulardata;
    private final CompositeType compositetype;

    private final CompositeData nestedCompositeData;

    // JEE Stats
    private final CountStatistic countStatistic;
    private final TimeStatistic timeStatistic;
    private final RangeStatistic rangeStatistic;
    private final BoundaryStatistic boundaryStatistic;
    private final BoundedRangeStatistic boundedRangeStatistic;
    private final Statistic unsupportedStatistic;
    private final Stats jeeStats;

    SimpleTestJavaApp() {
        this(false);
    }
    SimpleTestJavaApp(boolean includeJeeStats) {
        hashmap.put("thisis0", 0);
        hashmap.put("thisis10", 10);
        hashmap.put("thisiscounter", 0);
        hashmap.put("shouldBeDefaulted", 0);
        compositetype = buildCompositeType();
        tabulardata = buildTabularType();
        if (tabulardata != null) {
            tabulardata.put(buildCompositeData(1));
        }

        nestedCompositeData = buildNestedCompositeData();
        if (includeJeeStats) {
            countStatistic = new CountStatisticImpl("Sample Counter", long42424242);
            timeStatistic = new TimeStatisticImpl("Sample Time", 0, Long.MAX_VALUE, long42424242, 1);
            rangeStatistic = new RangeStatisticImpl("Sample Range", Long.MIN_VALUE, Long.MAX_VALUE, long42424242);
            boundaryStatistic = new BoundaryStatisticImpl("Sample Boundary", Long.MIN_VALUE, Long.MAX_VALUE);
            boundedRangeStatistic = new BoundedRangeStatisticImpl("Sample BoundedRange", Long.MIN_VALUE, Long.MAX_VALUE, 0, -1, +1);
            unsupportedStatistic = new UnsupportedStatisticImpl("Sample Unsupported Statistic");
            jeeStats = new JeeStats(Collections.singletonMap("MyCounter", countStatistic));
        } else {
            countStatistic = null;
            timeStatistic = null;
            rangeStatistic = null;
            boundaryStatistic = null;
            boundedRangeStatistic = null;
            unsupportedStatistic = null;
            jeeStats = null;
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

    public void decrementCounter(int dec) {
        shouldBeCounter -= dec;
    }

    public void incrementHashMapCounter(int inc) {
        hashmap.put("thisiscounter", hashmap.get("thisiscounter") + inc);
    }

    public void populateHashMap(int count) {
        for (Integer i = 1; i <= count; i++) {
            hashmap.put(i.toString(), i);
        }
    }

    public Map<String, Integer> getHashmap() {
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

    public float getPrimitiveFloat() {
        return primitiveFloat;
    }

    public Float getInstanceFloat() {
        return instanceFloat;
    }

    private TabularDataSupport buildTabularType() {
        try {
            CompositeType rowType = buildCompositeType();
            TabularType tabularType =
                    new TabularType(
                            "myTabularType", "My tabular type", rowType, new String[] {"foo"});
            return new TabularDataSupport(tabularType);
        } catch (OpenDataException e) {
            return null;
        }
    }

    private CompositeType buildCompositeType() {
        try {
            return new CompositeType(
                    "myCompositeType",
                    "My composite type",
                    new String[] {"foo", "bar", "toto"},
                    new String[] {
                        "Description of `foo`", "Description of `bar`", "Description of `toto`"
                    },
                    new OpenType[] {SimpleType.STRING, SimpleType.INTEGER, SimpleType.STRING});
        } catch (OpenDataException e) {
            return null;
        }
    }

    private CompositeDataSupport buildNestedCompositeData() {
        try {
            // Define the inner CompositeData
            String[] innerNames = { "aLong", "aDouble", "aString" };
            Object[] innerValues = { 123456L, 123.456, "Test String" };
            OpenType<?>[] innerTypes = { SimpleType.LONG, SimpleType.DOUBLE, SimpleType.STRING };

            CompositeType innerType = new CompositeType(
                    "InnerType",
                    "Description for Inner CompositeData",
                    innerNames,
                    innerNames,
                    innerTypes);

            CompositeData innerComposite = new CompositeDataSupport(innerType, innerNames, innerValues);

            // Define the outer CompositeData
            String[] outerNames = { "anInt", "aBoolean", "nestedData" };
            Object[] outerValues = { 42, true, innerComposite };
            OpenType<?>[] outerTypes = { SimpleType.INTEGER, SimpleType.BOOLEAN, innerType };

            CompositeType outerType = new CompositeType(
                    "OuterType",
                    "Description for Outer CompositeData",
                    outerNames,
                    outerNames,
                    outerTypes);

            return new CompositeDataSupport(outerType, outerNames, outerValues);
        } catch (Exception e) {
            // should never happen
            return null;
        }
    }

    public CompositeData getNestedCompositeData() {
        return this.nestedCompositeData;
    }

    @Override
    public Statistic getJeeCounter() {
        return countStatistic;
    }

    @Override
    public Statistic getJeeRange() {
        return rangeStatistic;
    }

    @Override
    public Statistic getJeeTime() {
        return timeStatistic;
    }

    @Override
    public Statistic getJeeBoundary() {
        return boundaryStatistic;
    }

    @Override
    public Statistic getJeeBoundedRange() {
        return boundedRangeStatistic;
    }

    @Override
    public Statistic getJeeUnsupported() {
        return unsupportedStatistic;
    }

    @Override
    public Stats getJeeStat() {
        return jeeStats;
    }

    private CompositeData buildCompositeData(Integer i) {
        try {
            return new CompositeDataSupport(
                    compositetype,
                    new String[] {"foo", "bar", "toto"},
                    new Object[] {i.toString(), i, "tata"});
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

    public TabularData getTabulardata() {
        return tabulardata;
    }
    public TabularDataSupport getTabularDataSupport() {
        return tabulardata;
    }
}
