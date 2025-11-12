package org.datadog.jmxfetch.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import javax.management.AttributeNotFoundException;
import javax.management.ReflectionException;

public class JeeStatisticsAttributes {
    /** Attributes for @see javax.management.j2ee.statistics.CountStatistic */
    static final List<String> COUNT_ATTRIBUTES = Collections.singletonList("count");

    /** Attributes for @see javax.management.j2ee.statistics.BoundaryStatistic */
    static final List<String> BOUNDARY_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("upperBound", "lowerBound"));

    /** Attributes for @see javax.management.j2ee.statistics.TimeStatistic */
    static final List<String> TIME_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("count", "minTime", "maxTime", "totalTime"));

    /** Attributes for @see javax.management.j2ee.statistics.RangeStatistic */
    static final List<String> RANGE_ATTRIBUTES =
        Collections.unmodifiableList(Arrays.asList("highWaterMark", "lowWaterMark", "current"));

    /** Attributes for @see javax.management.j2ee.statistics.BoundedRangeStatistic */
    static final List<String> BOUNDED_RANGE_ATTRIBUTES =
        Collections.unmodifiableList(
            Arrays.asList("upperBound", "lowerBound", "highWaterMark", "lowWaterMark", "current"));

    private static final Logger LOGGER = LoggerFactory.getLogger(JeeStatisticsAttributes.class);
    private static final WeakHashMap<ClassLoader, SoftReference<ReflectionHolder>>
        REFLECTION_CACHE = new WeakHashMap<>();

    static class ReflectionHolder {

        public final Class<?> classStat;
        public final MethodHandle mhStatGetStatisticNames;
        public final MethodHandle mhStatGetStatistic;
        public final Class<?> classStatistic;
        private final Class<?> classCountStatistic;
        private final Class<?> classTimeStatistic;
        private final Class<?> classRangeStatistic;
        private final Class<?> classBoundaryStatistic;
        private final Class<?> classBoundedRangeStatistic;

        private final Map<Class<?>, Map<String, MethodHandle>> methodCache;

        ReflectionHolder(final ClassLoader classLoader) {
            classStat = maybeLookupClass("javax.management.j2ee.statistics.Stats", classLoader);
            if (classStat != null) {
                mhStatGetStatisticNames = maybeFindMethodHandleFor(classStat, "getStatisticNames");
                mhStatGetStatistic = maybeFindMethodHandleFor(classStat, "getStatistic",
                    String.class);
                classStatistic = maybeLookupClass("javax.management.j2ee.statistics.Statistic",
                    classLoader);
                classCountStatistic =
                    maybeLookupClass("javax.management.j2ee.statistics.CountStatistic",
                        classLoader);
                classTimeStatistic =
                    maybeLookupClass("javax.management.j2ee.statistics.TimeStatistic",
                        classLoader);
                classRangeStatistic =
                    maybeLookupClass("javax.management.j2ee.statistics.RangeStatistic",
                        classLoader);
                classBoundaryStatistic =
                    maybeLookupClass("javax.management.j2ee.statistics.BoundaryStatistic",
                        classLoader);
                classBoundedRangeStatistic =
                    maybeLookupClass("javax.management.j2ee.statistics.BoundedRangeStatistic",
                        classLoader);
                methodCache = buildMethodCache();
            } else {
                mhStatGetStatisticNames = null;
                mhStatGetStatistic = null;
                classStatistic = null;
                classCountStatistic = null;
                classTimeStatistic = null;
                classRangeStatistic  = null;
                classBoundaryStatistic = null;
                classBoundedRangeStatistic = null;
                methodCache = new WeakHashMap<>();
            }
        }

        private Map<Class<?>, Map<String, MethodHandle>> buildMethodCache() {
            Map<Class<?>, Map<String, MethodHandle>> map = new HashMap<>();
            if (classCountStatistic != null) {
                map.put(classCountStatistic,
                    buildMethodCacheFor(classCountStatistic, COUNT_ATTRIBUTES));
            }
            if (classTimeStatistic != null) {
                map.put(classTimeStatistic,
                    buildMethodCacheFor(classTimeStatistic, TIME_ATTRIBUTES));
            }
            if (classBoundaryStatistic != null) {
                map.put(
                    classBoundaryStatistic,
                    buildMethodCacheFor(classBoundaryStatistic, BOUNDARY_ATTRIBUTES));
            }
            if (classRangeStatistic != null) {
                map.put(classRangeStatistic,
                    buildMethodCacheFor(classRangeStatistic, RANGE_ATTRIBUTES));
            }
            if (classBoundedRangeStatistic != null) {
                map.put(
                    classBoundedRangeStatistic,
                    buildMethodCacheFor(classBoundedRangeStatistic, BOUNDED_RANGE_ATTRIBUTES));
            }
            return map;
        }

        static MethodHandle maybeFindMethodHandleFor(
            final Class<?> cls, final String name, final Class<?>... parameterTypes) {
            if (cls == null) {
                return null;
            }
            return AccessController.doPrivileged(
                new PrivilegedAction<MethodHandle>() {
                    @Override
                    public MethodHandle run() {
                        try {
                            return MethodHandles.lookup()
                                .unreflect(cls.getMethod(name, parameterTypes));
                        } catch (Throwable t) {
                            LOGGER.debug("Unable to find method {} for class {}: {}", name, cls,
                                t.getMessage());
                        }
                        return null;
                    }
                });
        }

        static Map<String, MethodHandle> buildMethodCacheFor(
            final Class<?> cls, final List<String> attributes) {
            final Map<String, MethodHandle> map = new HashMap<>();
            for (String attribute : attributes) {
                MethodHandle methodHandle =
                    maybeFindMethodHandleFor(cls, getterMethodName(attribute));
                if (methodHandle != null) {
                    map.put(attribute, methodHandle);
                }
            }
            return map;
        }

        static Class<?> maybeLookupClass(final String name, final ClassLoader classLoader) {
            try {
                return Class.forName(name, false, classLoader);
            } catch (Throwable t) {
                LOGGER.debug(
                    "Class {} is unavailable for classloader {}. JEE statistics won't be extracted",
                    name,
                    classLoader);
            }
            return null;
        }

        static String getterMethodName(String attribute) {
            // inspired from JavaBean PropertyDescriptor
            return "get" + attribute.substring(0, 1).toUpperCase(Locale.ROOT)
                + attribute.substring(1);
        }
    }

    private static ReflectionHolder getOrCreateReflectionHolder(final ClassLoader classLoader) {
        // no need to lock here. At worst, we'll do it more time if there is contention.
        SoftReference<ReflectionHolder> ref = REFLECTION_CACHE.get(classLoader);
        if (ref != null && ref.get() != null) {
            return ref.get();
        }
        final ReflectionHolder holder = new ReflectionHolder(classLoader);
        REFLECTION_CACHE.put(classLoader, new SoftReference<>(holder));
        return holder;
    }

    /**
     * Get the list of attributes for a @see javax.management.j2ee.statistics.Statistic object.
     *
     * @param instance a @see javax.management.j2ee.statistics.Statistic instance
     * @return the list of attributes
     */
    public static List<String> attributesFor(Object instance) {
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        if (rh.classCountStatistic != null && rh.classCountStatistic.isInstance(instance)) {
            return COUNT_ATTRIBUTES;
        }
        if (rh.classTimeStatistic != null && rh.classTimeStatistic.isInstance(instance)) {
            return TIME_ATTRIBUTES;
        }
        if (rh.classBoundedRangeStatistic != null
            && rh.classBoundedRangeStatistic.isInstance(instance)) {
            return BOUNDED_RANGE_ATTRIBUTES;
        }
        if (rh.classRangeStatistic != null && rh.classRangeStatistic.isInstance(instance)) {
            return RANGE_ATTRIBUTES;
        }
        if (rh.classBoundaryStatistic != null && rh.classBoundaryStatistic.isInstance(instance)) {
            return BOUNDARY_ATTRIBUTES;
        }

        LOGGER.debug("Getting attributes for class of type {} not supported", instance.getClass());
        return Collections.emptyList();
    }

    /**
     * Fetch the data for a @see javax.management.j2ee.statistics.Statistic instance given an
     * attribute name.
     *
     * @param instance the @see javax.management.j2ee.statistics.Statistic object
     * @param attribute the attribute name
     * @return the data if any
     * @throws ReflectionException in case of reflection issues.
     * @throws AttributeNotFoundException in case the attribute is not found.
     */
    public static long dataFor(Object instance, String attribute)
        throws ReflectionException, AttributeNotFoundException {
        Class<?> cls = null;
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        if (rh.classCountStatistic != null && rh.classCountStatistic.isInstance(instance)) {
            cls = rh.classCountStatistic;
        } else if (rh.classTimeStatistic != null && rh.classTimeStatistic.isInstance(instance)) {
            cls = rh.classTimeStatistic;
        } else if (rh.classBoundedRangeStatistic != null
            && rh.classBoundedRangeStatistic.isInstance(instance)) {
            cls = rh.classBoundedRangeStatistic;
        } else if (rh.classRangeStatistic != null && rh.classRangeStatistic.isInstance(instance)) {
            cls = rh.classRangeStatistic;
        } else if (rh.classBoundaryStatistic != null
            && rh.classBoundaryStatistic.isInstance(instance)) {
            cls = rh.classBoundaryStatistic;
        }
        if (cls == null) {
            throw new AttributeNotFoundException(
                "Not supported JSR-77 class: " + instance.getClass().getName());
        }
        MethodHandle methodHandle = rh.methodCache.get(cls).get(attribute);
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
     *
     * @param instance a Stat instance
     * @return the names of inner statistics it holds.
     */
    public static List<String> getStatisticNames(Object instance) {
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        if (rh.mhStatGetStatisticNames == null || rh.mhStatGetStatistic == null) {
            return Collections.emptyList();
        }
        try {
            String[] names = (String[]) rh.mhStatGetStatisticNames.invoke(instance);
            if (names != null) {
                List<String> ret = new ArrayList<>();
                for (String name : names) {
                    Object stat = rh.mhStatGetStatistic.invoke(instance, name);
                    for (String attr : attributesFor(stat)) {
                        ret.add(name + "." + attr);
                    }
                }
                return ret;
            }
        } catch (Throwable t) {
            LOGGER.warn(
                "Unable to get statistic names from jee stat class {}: {}",
                instance.getClass(),
                t.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Get the statistic data for a complex Stat object.
     *
     * @param instance the Stat instance
     * @param name the name (dot separated) of the statistic
     * @return the value.
     * @throws AttributeNotFoundException if the attribute is invalid or not found.
     */
    public static long getStatisticDataFor(Object instance, String name)
        throws AttributeNotFoundException {
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        if (rh.mhStatGetStatistic == null) {
            throw new IllegalStateException(
                "Cannot fetch statistic data for instance type " + instance.getClass());
        }
        int idx = name.indexOf(".");
        if (idx == -1) {
            throw new AttributeNotFoundException("Invalid attribute name " + name);
        }
        String statName = name.substring(0, idx);
        String attrName = name.substring(idx + 1);
        try {
            Object stat = rh.mhStatGetStatistic.invoke(instance, statName);
            return dataFor(stat, attrName);
        } catch (Throwable t) {
            throw new AttributeNotFoundException(
                "Unable to get statistic with name "
                    + name
                    + "from jee stat class "
                    + instance.getClass());
        }
    }

    /**
     * Check that's instance is instance of Stat.
     * @param instance the instance
     * @return a boolean
     */
    public static boolean isJeeStat(Object instance) {
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        return rh.classStat != null && rh.classStat.isInstance(instance);
    }

    /**
     * Check that's instance is instance of Statistic.
     * @param instance the instance
     * @return a boolean
     */
    public static boolean isJeeStatistic(Object instance) {
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        return rh.classStatistic != null && rh.classStatistic.isInstance(instance);
    }
}
