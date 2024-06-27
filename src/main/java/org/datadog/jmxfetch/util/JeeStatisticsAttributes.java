package org.datadog.jmxfetch.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.ReflectionException;

public class JeeStatisticsAttributes {
    /**
     * Attributes for @see javax.management.j2ee.statistics.CountStatistic
     * */
    private static final List<String> COUNT_ATTRIBUTES = Collections.singletonList("count");

    /**
     * Attributes for @see javax.management.j2ee.statistics.BoundaryStatistic
     * */
    private static final List<String> BOUNDARY_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("upperBound", "lowerBound"));

    /**
     * Attributes for @see javax.management.j2ee.statistics.TimeStatistic
     * */
    private static final List<String> TIME_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("count", "minTime", "maxTime", "totalTime"));

    /**
     * Attributes for @see javax.management.j2ee.statistics.RangeStatistic
     * */
    private static final List<String> RANGE_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("highWaterMark", "lowWaterMark", "current"));

    /**
     * Attributes for @see javax.management.j2ee.statistics.BoundedRangeStatistic
     * */
    private static final List<String> BOUNDED_RANGE_ATTRIBUTES =
        Collections.unmodifiableList(
            Arrays.asList("upperBound", "lowerBound", "highWaterMark", "lowWaterMark", "current"));

    private static final Logger LOGGER = LoggerFactory.getLogger(JeeStatisticsAttributes.class);
    public static final Class<?> CLASS_STATS =
        maybeLookupClass("javax.management.j2ee.statistics.Stats");
    public static final MethodHandle MH_STATS_GET_STATISTIC_NAMES =
        maybeFindMethodHandleFor(CLASS_STATS, "getStatisticNames");
    public static final MethodHandle MH_STATS_GET_STATISTIC =
        maybeFindMethodHandleFor(CLASS_STATS, "getStatistic", String.class);
    public static final Class<?> CLASS_STATISTIC =
        maybeLookupClass("javax.management.j2ee.statistics.Statistic");
    private static final Class<?> CLASS_COUNT_STATISTIC =
        maybeLookupClass("javax.management.j2ee.statistics.CountStatistic");
    private static final Class<?> CLASS_TIME_STATISTIC =
        maybeLookupClass("javax.management.j2ee.statistics.TimeStatistic");
    private static final Class<?> CLASS_RANGE_STATISTIC =
        maybeLookupClass("javax.management.j2ee.statistics.RangeStatistic");
    private static final Class<?> CLASS_BOUNDARY_STATISTIC =
        maybeLookupClass("javax.management.j2ee.statistics.BoundaryStatistic");
    private static final Class<?> CLASS_BOUNDED_RANGE_STATISTIC =
        maybeLookupClass("javax.management.j2ee.statistics.BoundedRangeStatistic");

    private static final Map<Class<?>, Map<String, MethodHandle>> METHOD_CACHE =
        buildMethodCache();


    private static Class<?> maybeLookupClass(final String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            LOGGER.warn("Class {} is unavailable. J2ee statistics won't be extracted", name);
        }
        return null;
    }

    private static Map<Class<?>, Map<String, MethodHandle>> buildMethodCache() {
        Map<Class<?>, Map<String, MethodHandle>> map = new HashMap<>();
        if (CLASS_STATISTIC != null) {
            map.put(CLASS_COUNT_STATISTIC,
                buildMethodCacheFor(CLASS_COUNT_STATISTIC, COUNT_ATTRIBUTES));
        }
        if (CLASS_TIME_STATISTIC != null) {
            map.put(CLASS_TIME_STATISTIC,
                buildMethodCacheFor(CLASS_TIME_STATISTIC, TIME_ATTRIBUTES));
        }
        if (CLASS_BOUNDARY_STATISTIC != null) {
            map.put(CLASS_BOUNDARY_STATISTIC, buildMethodCacheFor(CLASS_BOUNDARY_STATISTIC,
                BOUNDARY_ATTRIBUTES));
        }
        if (CLASS_RANGE_STATISTIC != null) {
            map.put(CLASS_RANGE_STATISTIC,
                buildMethodCacheFor(CLASS_RANGE_STATISTIC, RANGE_ATTRIBUTES));
        }
        if (CLASS_BOUNDED_RANGE_STATISTIC != null) {
            map.put(
                CLASS_BOUNDED_RANGE_STATISTIC,
                buildMethodCacheFor(CLASS_BOUNDED_RANGE_STATISTIC, BOUNDED_RANGE_ATTRIBUTES));
        }
        return map;
    }


    private static MethodHandle maybeFindMethodHandleFor(final Class<?> cls, final String name,
                                                         final Class<?>... parameterTypes) {
        if (cls == null) {
            return null;
        }
        return AccessController.doPrivileged(
            new PrivilegedAction<MethodHandle>() {
                @Override
                public MethodHandle run() {
                    try {
                        return MethodHandles.lookup().unreflect(
                            cls.getMethod(name, parameterTypes));
                    } catch (Throwable t) {
                        LOGGER.warn("Unable to find method {} for class {}: {}", name, cls,
                            t.getMessage());
                    }
                    return null;
                }
            });
    }

    private static Map<String, MethodHandle> buildMethodCacheFor(final Class<?> cls,
                                                                 final List<String> attributes) {
        final Map<String, MethodHandle> map = new HashMap<>();
        for (String attribute : attributes) {
            MethodHandle methodHandle = maybeFindMethodHandleFor(cls, getterMethodName(attribute));
            if (methodHandle != null) {
                map.put(attribute, methodHandle);
            }
        }
        return map;
    }

    private static String getterMethodName(String attribute) {
        // inspired from JavaBean PropertyDescriptor
        return "get" + attribute.substring(0, 1).toUpperCase(Locale.ROOT) + attribute.substring(1);
    }

    /**
     * Get the list of attributes for a @see javax.management.j2ee.statistics.Statistic object.
     * @param instance a @see javax.management.j2ee.statistics.Statistic instance
     * @return the list of attributes
     */
    public static List<String> attributesFor(Object instance) {
        if (CLASS_COUNT_STATISTIC != null && CLASS_COUNT_STATISTIC.isInstance(instance)) {
            return COUNT_ATTRIBUTES;
        }
        if (CLASS_TIME_STATISTIC != null && CLASS_TIME_STATISTIC.isInstance(instance)) {
            return TIME_ATTRIBUTES;
        }
        if (CLASS_BOUNDED_RANGE_STATISTIC != null
            && CLASS_BOUNDED_RANGE_STATISTIC.isInstance(instance)) {
            return BOUNDED_RANGE_ATTRIBUTES;
        }
        if (CLASS_RANGE_STATISTIC != null && CLASS_RANGE_STATISTIC.isInstance(instance)) {
            return RANGE_ATTRIBUTES;
        }
        if (CLASS_BOUNDARY_STATISTIC != null && CLASS_BOUNDARY_STATISTIC.isInstance(instance)) {
            return BOUNDARY_ATTRIBUTES;
        }
        return Collections.emptyList();
    }

    /**
     * Fetch the data for a @see javax.management.j2ee.statistics.Statistic instance given
     * an attribute name.
     * @param instance the @see javax.management.j2ee.statistics.Statistic object
     * @param attribute the attribute name
     * @return the data if any
     * @throws ReflectionException in case of reflection issues.
     * @throws AttributeNotFoundException in case the attribute is not found.
     */
    public static long dataFor(Object instance, String attribute)
        throws ReflectionException, AttributeNotFoundException {
        Class<?> cls = null;
        if (CLASS_COUNT_STATISTIC != null && CLASS_COUNT_STATISTIC.isInstance(instance)) {
            cls = CLASS_COUNT_STATISTIC;
        } else if (CLASS_TIME_STATISTIC != null && CLASS_TIME_STATISTIC.isInstance(instance)) {
            cls = CLASS_TIME_STATISTIC;
        } else if (CLASS_BOUNDED_RANGE_STATISTIC != null
            && CLASS_BOUNDED_RANGE_STATISTIC.isInstance(instance)) {
            cls = CLASS_BOUNDED_RANGE_STATISTIC;
        } else if (CLASS_RANGE_STATISTIC != null && CLASS_RANGE_STATISTIC.isInstance(instance)) {
            cls = CLASS_RANGE_STATISTIC;
        } else if (CLASS_BOUNDARY_STATISTIC != null
            && CLASS_BOUNDARY_STATISTIC.isInstance(instance)) {
            cls = CLASS_BOUNDARY_STATISTIC;
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

    /**
     * Get names for a Stat complex object.
     * @param instance a Stat instance
     * @return the names of inner statistics it holds.
     */
    public static List<String> getStatisticNames(Object instance) {
        if (MH_STATS_GET_STATISTIC_NAMES == null || MH_STATS_GET_STATISTIC == null) {
            return Collections.emptyList();
        }
        try {
            String[] names = (String[]) MH_STATS_GET_STATISTIC_NAMES.invoke(instance);
            if (names != null) {
                List<String> ret = new ArrayList<>();
                for (String name : names) {
                    Object stat = MH_STATS_GET_STATISTIC.invoke(instance, name);
                    for (String attr : attributesFor(stat)) {
                        ret.add(name + "." + attr);
                    }
                }
                return ret;
            }
        } catch (Throwable t) {
            LOGGER.warn("Unable to get statistic names from jee stat class {}: {}",
                instance.getClass(), t.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Get the statistic data for a complex Stat object.
     * @param instance the Stat instance
     * @param name the name (dot separated) of the statistic
     * @return the value.
     * @throws AttributeNotFoundException if the attribute is invalid or not found.
     */
    public static long getStatisticDataFor(Object instance, String name)
        throws AttributeNotFoundException {
        if (MH_STATS_GET_STATISTIC == null) {
            throw new IllegalStateException("Cannot fetch statistic data for instance type "
                + instance.getClass());
        }
        int idx = name.indexOf(".");
        if (idx == -1) {
            throw new AttributeNotFoundException("Invalid attribute name " + name);
        }
        String statName = name.substring(0, idx);
        String attrName = name.substring(idx + 1);
        try {
            Object stat =  MH_STATS_GET_STATISTIC.invoke(instance, statName);
            return dataFor(stat, attrName);
        } catch (Throwable t) {
            throw new AttributeNotFoundException("Unable to get statistic with name " + name
                + "from jee stat class " + instance.getClass());
        }
    }
}
