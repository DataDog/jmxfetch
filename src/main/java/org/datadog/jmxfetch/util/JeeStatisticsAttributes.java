package org.datadog.jmxfetch.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.ReflectionException;
import javax.management.j2ee.statistics.BoundaryStatistic;
import javax.management.j2ee.statistics.BoundedRangeStatistic;
import javax.management.j2ee.statistics.CountStatistic;
import javax.management.j2ee.statistics.RangeStatistic;
import javax.management.j2ee.statistics.Statistic;
import javax.management.j2ee.statistics.TimeStatistic;

public class JeeStatisticsAttributes {
    /**
     * Attributes for {@link javax.management.j2ee.statistics.CountStatistic}
     * */
    public static final List<String> COUNT_ATTRIBUTES = Collections.singletonList("count");

    /**
     * Attributes for {@link javax.management.j2ee.statistics.BoundaryStatistic}
     * */
    public static final List<String> BOUNDARY_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("upperBound", "lowerBound"));

    /**
     * Attributes for {@link javax.management.j2ee.statistics.TimeStatistic}
     * */
    public static final List<String> TIME_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("count", "minTime", "maxTime", "totalTime"));

    /**
     * Attributes for {@link javax.management.j2ee.statistics.RangeStatistic}
     * */
    public static final List<String> RANGE_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("highWaterMark", "lowWaterMark", "current"));

    /**
     * Attributes for {@link javax.management.j2ee.statistics.BoundedRangeStatistic}
     * */
    public static final List<String> BOUNDED_RANGE_ATTRIBUTES =
        Collections.unmodifiableList(
            Arrays.asList("upperBound", "lowerBound", "highWaterMark", "lowWaterMark", "current"));

    private static final Logger LOGGER = LoggerFactory.getLogger(JeeStatisticsAttributes.class);
    private static final Map<Class<? extends Statistic>, Map<String, MethodHandle>> METHOD_CACHE =
        buildMethodCache();

    private static Map<Class<? extends Statistic>, Map<String, MethodHandle>> buildMethodCache() {
        Map<Class<? extends Statistic>, Map<String, MethodHandle>> map = new HashMap<>();
        map.put(CountStatistic.class, buildMethodCacheFor(CountStatistic.class, COUNT_ATTRIBUTES));
        map.put(TimeStatistic.class, buildMethodCacheFor(TimeStatistic.class, TIME_ATTRIBUTES));
        map.put(BoundaryStatistic.class, buildMethodCacheFor(BoundaryStatistic.class,
            BOUNDARY_ATTRIBUTES));
        map.put(RangeStatistic.class, buildMethodCacheFor(RangeStatistic.class, RANGE_ATTRIBUTES));
        map.put(
            BoundedRangeStatistic.class,
            buildMethodCacheFor(BoundedRangeStatistic.class, BOUNDED_RANGE_ATTRIBUTES));
        return map;
    }

    private static Map<String, MethodHandle> buildMethodCacheFor(
        final Class<? extends Statistic> cls, final List<String> attributes) {
        final Map<String, MethodHandle> map = new HashMap<>();
        AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    for (String attribute : attributes) {
                        try {
                            Method getter = cls.getMethod(getterMethodName(attribute));
                            map.put(attribute, MethodHandles.lookup().unreflect(getter));
                        } catch (Throwable t) {
                            LOGGER.warn("Unable to find getter for attribute {}: {}", attribute,
                                t.getMessage());
                        }
                    }
                    return null;
                }
            });

        return map;
    }

    private static String getterMethodName(String attribute) {
        // inspired from JavaBean PropertyDescriptor
        return "get" + attribute.substring(0, 1).toUpperCase(Locale.ROOT) + attribute.substring(1);
    }

    /**
     * Get the list of attributes for a {@link Statistic} object.
     * @param instance a {@link Statistic} instance
     * @return the list of attributes
     */
    public static List<String> attributesFor(Object instance) {
        if (instance instanceof CountStatistic) {
            return COUNT_ATTRIBUTES;
        }
        if (instance instanceof TimeStatistic) {
            return TIME_ATTRIBUTES;
        }
        if (instance instanceof BoundedRangeStatistic) {
            return BOUNDED_RANGE_ATTRIBUTES;
        }
        if (instance instanceof RangeStatistic) {
            return RANGE_ATTRIBUTES;
        }
        if (instance instanceof BoundaryStatistic) {
            return BOUNDARY_ATTRIBUTES;
        }
        return Collections.emptyList();
    }

    /**
     * Fetch the data for a {@link Statistic} instance given an attribute name.
     * @param instance the {@link Statistic} object
     * @param attribute the attribute name
     * @return the data if any
     * @throws ReflectionException in case of reflection issues.
     * @throws AttributeNotFoundException in case the attribute is not found.
     */
    public static long dataFor(Object instance, String attribute)
        throws ReflectionException, AttributeNotFoundException {
        Class<? extends Statistic> cls = null;
        if (instance instanceof CountStatistic) {
            cls = CountStatistic.class;
        } else if (instance instanceof TimeStatistic) {
            cls = TimeStatistic.class;
        } else if (instance instanceof BoundedRangeStatistic) {
            cls = BoundedRangeStatistic.class;
        } else if (instance instanceof RangeStatistic) {
            cls = RangeStatistic.class;
        } else if (instance instanceof BoundaryStatistic) {
            cls = BoundaryStatistic.class;
        }
        if (cls == null) {
            throw new AttributeNotFoundException("Not supported JSR-77 class: "
                + instance.getClass().getName());
        }
        MethodHandle methodHandle = METHOD_CACHE.get(cls).get(attribute);
        if (methodHandle == null) {
            throw new AttributeNotFoundException(
                "Unable to find getter for attribute " + attribute + " on class " + cls.getName());
        }
        try {
            return (long) methodHandle.invoke(instance);
        } catch (Throwable t) {
            throw new ReflectionException(
                new Exception(t),
                "Unable to invoke getter for attribute" + attribute + " on class " + cls.getName());
        }
    }
}
