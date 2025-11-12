package org.datadog.jmxfetch.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.invoke.MethodHandle;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.management.AttributeNotFoundException;
import javax.management.ReflectionException;

/**
 * Utility class for working with IBM WebSphere PMI
 * (Performance Monitoring Infrastructure) Statistics.
 * PMI classes are IBM-specific implementations that don't implement standard JEE interfaces,
 * requiring class name-based detection and optimized caching strategies.
 *
 * <p>Also handles WebSphere PMI subCollections - named groups of statistics within
 * a Stats object, commonly used for JDBC connection pools and other resources.
 */
@Slf4j
public class PmiStatisticsAttributes {
    // Cache for WebSphere PMI reflection metadata
    private static final WeakHashMap<ClassLoader, SoftReference<ReflectionHolder>>
        REFLECTION_CACHE = new WeakHashMap<>();

    private PmiStatisticsAttributes() {
        // Utility class
    }

    /**
     * Reflection holder for WebSphere PMI classes.
     */
    private static class ReflectionHolder {
        // PMI Statistic implementation classes
        private final Class<?> classCountStatisticImpl;
        private final Class<?> classTimeStatisticImpl;
        private final Class<?> classBoundedRangeStatisticImpl;
        private final Class<?> classRangeStatisticImpl;
        private final Class<?> classBoundaryStatisticImpl;

        // Method cache per class type
        private final Map<Class<?>, Map<String, MethodHandle>> methodCache;

        // SubCollection support
        public final Class<?> classStat;
        public final Class<?> classStatsImpl;  // com.ibm.ws.pmi.stat.StatsImpl
        public final Class<?> classJ2eeStatsImpl;  // com.ibm.ws.pmi.j2ee.StatsImpl

        // Method handles for com.ibm.ws.pmi.stat.StatsImpl
        public final MethodHandle mhStatsImplSubCollection;
        public final MethodHandle mhStatsImplGetName;
        public final MethodHandle mhStatsImplGetStatisticNames;
        public final MethodHandle mhStatsImplGetStatistic;

        // Method handles for com.ibm.ws.pmi.j2ee.StatsImpl (used by most Stats implementations)
        public final MethodHandle mhJ2eeStatsImplGetStatistic;
        public final MethodHandle mhJ2eeStatsImplGetStatisticNames;
        public final MethodHandle mhJ2eeStatsImplGetWsImpl;

        ReflectionHolder(final ClassLoader classLoader) {
            // Load PMI Statistic implementation classes
            classCountStatisticImpl = JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                "com.ibm.ws.pmi.stat.CountStatisticImpl", classLoader);
            classTimeStatisticImpl =
                JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                    "com.ibm.ws.pmi.stat.TimeStatisticImpl", classLoader);
            classBoundedRangeStatisticImpl =
                JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                    "com.ibm.ws.pmi.stat.BoundedRangeStatisticImpl", classLoader);
            classRangeStatisticImpl = JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                "com.ibm.ws.pmi.stat.RangeStatisticImpl", classLoader);
            classBoundaryStatisticImpl = JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                "com.ibm.ws.pmi.stat.BoundaryStatisticImpl", classLoader);

            // Build method cache for PMI classes
            methodCache = buildMethodCache();

            // SubCollection support
            classStat = JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                "javax.management.j2ee.statistics.Stats", classLoader);
            classStatsImpl = JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                "com.ibm.ws.pmi.stat.StatsImpl", classLoader);
            classJ2eeStatsImpl = JeeStatisticsAttributes.ReflectionHolder.maybeLookupClass(
                "com.ibm.ws.pmi.j2ee.StatsImpl", classLoader);

            // Initialize method handles for com.ibm.ws.pmi.stat.StatsImpl
            if (classStatsImpl != null) {
                mhStatsImplSubCollection =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classStatsImpl, "subCollections");
                mhStatsImplGetName =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classStatsImpl, "getName");
                mhStatsImplGetStatisticNames =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classStatsImpl, "getStatisticNames");
                mhStatsImplGetStatistic =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classStatsImpl, "getStatistic", String.class);
            } else {
                mhStatsImplSubCollection = null;
                mhStatsImplGetName = null;
                mhStatsImplGetStatisticNames = null;
                mhStatsImplGetStatistic = null;
            }

            // Initialize method handles for com.ibm.ws.pmi.j2ee.StatsImpl
            if (classJ2eeStatsImpl != null) {
                mhJ2eeStatsImplGetStatistic =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classJ2eeStatsImpl, "getStatistic", String.class);
                mhJ2eeStatsImplGetStatisticNames =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classJ2eeStatsImpl, "getStatisticNames");
                mhJ2eeStatsImplGetWsImpl =
                        JeeStatisticsAttributes.ReflectionHolder.maybeFindMethodHandleFor(
                            classJ2eeStatsImpl, "getWSImpl");
            } else {
                mhJ2eeStatsImplGetStatistic = null;
                mhJ2eeStatsImplGetStatisticNames = null;
                mhJ2eeStatsImplGetWsImpl = null;
            }
        }

        private Map<Class<?>, Map<String, MethodHandle>> buildMethodCache() {
            Map<Class<?>, Map<String, MethodHandle>> map = new HashMap<>();
            if (classCountStatisticImpl != null) {
                map.put(classCountStatisticImpl,
                    JeeStatisticsAttributes.ReflectionHolder.buildMethodCacheFor(
                        classCountStatisticImpl, JeeStatisticsAttributes.COUNT_ATTRIBUTES));
            }
            if (classTimeStatisticImpl != null) {
                map.put(classTimeStatisticImpl,
                    JeeStatisticsAttributes.ReflectionHolder.buildMethodCacheFor(
                        classTimeStatisticImpl, JeeStatisticsAttributes.TIME_ATTRIBUTES));
            }
            if (classBoundaryStatisticImpl != null) {
                map.put(classBoundaryStatisticImpl,
                    JeeStatisticsAttributes.ReflectionHolder.buildMethodCacheFor(
                        classBoundaryStatisticImpl, JeeStatisticsAttributes.BOUNDARY_ATTRIBUTES));
            }
            if (classRangeStatisticImpl != null) {
                map.put(classRangeStatisticImpl,
                    JeeStatisticsAttributes.ReflectionHolder.buildMethodCacheFor(
                        classRangeStatisticImpl, JeeStatisticsAttributes.RANGE_ATTRIBUTES));
            }
            if (classBoundedRangeStatisticImpl != null) {
                map.put(classBoundedRangeStatisticImpl,
                    JeeStatisticsAttributes.ReflectionHolder.buildMethodCacheFor(
                        classBoundedRangeStatisticImpl,
                        JeeStatisticsAttributes.BOUNDED_RANGE_ATTRIBUTES));
            }
            return map;
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
     * Fetch the data for a PMI Statistic instance given an attribute name.
     * For non-PMI classes, delegates to JeeStatisticsAttributes.dataFor().
     *
     * @param instance the Statistic instance (maybe PMI or standard J2EE)
     * @param attribute the attribute name (e.g., "count", "current", "upperBound")
     * @return the attribute value
     * @throws ReflectionException if reflection fails
     * @throws AttributeNotFoundException if attribute not found
     */
    public static long dataFor(Object instance, String attribute)
            throws ReflectionException, AttributeNotFoundException {
        Class<?> cls = null;
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        if (rh.classCountStatisticImpl != null
                && rh.classCountStatisticImpl.isInstance(instance)) {
            cls = rh.classCountStatisticImpl;
        } else if (rh.classTimeStatisticImpl != null
                && rh.classTimeStatisticImpl.isInstance(instance)) {
            cls = rh.classTimeStatisticImpl;
        } else if (rh.classBoundedRangeStatisticImpl != null
                && rh.classBoundedRangeStatisticImpl.isInstance(instance)) {
            cls = rh.classBoundedRangeStatisticImpl;
        } else if (rh.classRangeStatisticImpl != null
                && rh.classRangeStatisticImpl.isInstance(instance)) {
            cls = rh.classRangeStatisticImpl;
        } else if (rh.classBoundaryStatisticImpl != null
                && rh.classBoundaryStatisticImpl.isInstance(instance)) {
            cls = rh.classBoundaryStatisticImpl;
        }
        if (cls == null) {
            // Not a PMI class - delegate to standard implementation
            return JeeStatisticsAttributes.dataFor(instance, attribute);
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
                "Unable to invoke getter for attribute " + attribute + " on class "
                    + cls.getName());
        }
    }

    /**
     * Get the list of available attributes for a Statistic object.
     * Handles both PMI implementation classes and standard JEE Statistics.
     * For PMI classes, uses cached Class references; for standard JEE, delegates.
     *
     * @param instance the Statistic instance
     * @return list of attribute names (e.g., ["count"] or ["current", "upperBound", ...])
     */
    public static List<String> attributesFor(Object instance) {
        ReflectionHolder rh = getOrCreateReflectionHolder(
            instance.getClass().getClassLoader());

        // Check PMI implementation classes using instanceof (via cached Class references)
        if (rh.classCountStatisticImpl != null
                && rh.classCountStatisticImpl.isInstance(instance)) {
            return JeeStatisticsAttributes.COUNT_ATTRIBUTES;
        }
        if (rh.classTimeStatisticImpl != null
                && rh.classTimeStatisticImpl.isInstance(instance)) {
            return JeeStatisticsAttributes.TIME_ATTRIBUTES;
        }
        if (rh.classBoundedRangeStatisticImpl != null
                && rh.classBoundedRangeStatisticImpl.isInstance(instance)) {
            return JeeStatisticsAttributes.BOUNDED_RANGE_ATTRIBUTES;
        }
        if (rh.classRangeStatisticImpl != null
                && rh.classRangeStatisticImpl.isInstance(instance)) {
            return JeeStatisticsAttributes.RANGE_ATTRIBUTES;
        }
        if (rh.classBoundaryStatisticImpl != null
                && rh.classBoundaryStatisticImpl.isInstance(instance)) {
            return JeeStatisticsAttributes.BOUNDARY_ATTRIBUTES;
        }

        // Not PMI - delegate to standard implementation
        return JeeStatisticsAttributes.attributesFor(instance);
    }

    /**
     * Get statistic names from a Stats object.
     * Handles both PMI Stats objects and standard JEE Stats.
     * Returns full paths like "StatisticName.attribute" (e.g., "PoolSize.current").
     *
     * @param instance a Stats instance
     * @return list of statistic paths
     */
    public static List<String> getStatisticNames(Object instance) {
        List<String> ret = new ArrayList<>();
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());

        try {
            final MethodHandle mhGetStatisticNames;
            final MethodHandle mhGetStatistic;

            // Determine which method handles to use based on class hierarchy
            if (rh.classJ2eeStatsImpl != null
                    && rh.classJ2eeStatsImpl.isAssignableFrom(instance.getClass())) {
                mhGetStatisticNames = rh.mhJ2eeStatsImplGetStatisticNames;
                mhGetStatistic = rh.mhJ2eeStatsImplGetStatistic;
            } else if (rh.classStatsImpl != null
                    && rh.classStatsImpl.isAssignableFrom(instance.getClass())) {
                mhGetStatisticNames = rh.mhStatsImplGetStatisticNames;
                mhGetStatistic = rh.mhStatsImplGetStatistic;
            } else {
                return Collections.emptyList();
            }

            String[] names = (String[]) mhGetStatisticNames.invoke(instance);
            if (names != null && mhGetStatistic != null) {
                for (String name : names) {
                    Object stat = mhGetStatistic.invoke(instance, name);
                    List<String> attrs = attributesFor(stat);
                    for (String attr : attrs) {
                        ret.add(name + "." + attr);
                    }
                }
            }
        } catch (Throwable t) {
            log.debug("Unable to get statistic names from Stats class {}: {}",
                    instance.getClass(), t.getMessage());
        }

        return ret;
    }

    /**
     * Check if a Stats instance has subCollections.
     *
     * @param instance a Stats instance
     * @return true if the instance has subCollections
     */
    public static boolean hasSubCollections(Object instance) {
        ReflectionHolder rh =
            getOrCreateReflectionHolder(instance.getClass().getClassLoader());

        if (!JeeStatisticsAttributes.isJeeStat(instance)) {
            return false;
        }

        // WebSphere J2EE Stats implementations (JDBCStatsImpl, ServletStatsImpl, etc.)
        // are wrapper classes with a getWSImpl() method that returns the actual
        // StatsImpl which has the subCollections() method
        if (rh.classStat != null && rh.classStat.isInstance(instance)) {
            try {
                MethodHandle mhGetWsImpl = rh.mhJ2eeStatsImplGetWsImpl;
                if (mhGetWsImpl != null) {
                    Object wsImpl = mhGetWsImpl.invoke(instance);
                    if (wsImpl != null && rh.mhStatsImplSubCollection != null) {
                        ArrayList<?> subCollections =
                            (ArrayList<?>) rh.mhStatsImplSubCollection.invoke(wsImpl);
                        return !subCollections.isEmpty();
                    }
                }
            } catch (Throwable t) {
                log.trace("No subCollections via getWSImpl for {}", instance.getClass());
            }
        }

        return false;
    }

    /**
     * Get subCollection statistics structure.
     * Returns a map from subCollection name to list of statistic attribute names.
     *
     * @param instance a Stats instance
     * @return map of subCollection name to list of statistic paths (e.g., "statName.count")
     */
    public static Map<String, List<String>> getSubCollectionStatistics(Object instance) {
        ReflectionHolder rh =
            getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        Map<String, List<String>> result = new HashMap<>();

        if (!JeeStatisticsAttributes.isJeeStat(instance)) {
            return result;
        }

        // WebSphere JEE Stats implementations have getWSImpl() method that returns
        // the actual StatsImpl object with subCollections
        try {
            MethodHandle mhGetWsImpl = rh.mhJ2eeStatsImplGetWsImpl;
            if (mhGetWsImpl != null) {
                Object wsImpl = mhGetWsImpl.invoke(instance);
                if (wsImpl != null && rh.mhStatsImplSubCollection != null) {
                    ArrayList<?> subCollections =
                        (ArrayList<?>) rh.mhStatsImplSubCollection.invoke(wsImpl);
                    if (subCollections != null && !subCollections.isEmpty()) {
                        return extractSubCollectionStats(subCollections, rh);
                    }
                }
            }
        } catch (Throwable t) {
            log.trace("Could not get subCollections via getWSImpl: {}", t.getMessage());
        }

        return result;
    }

    /**
     * Extract statistics from a list of subCollection objects.
     */
    private static Map<String, List<String>> extractSubCollectionStats(
            ArrayList<?> subCollections,
            ReflectionHolder rh) {
        Map<String, List<String>> result = new HashMap<>();

        for (Object subColl : subCollections) {
            try {
                // Get the name of this subCollection
                String subCollName = (String) rh.mhStatsImplGetName.invoke(subColl);

                // Get the statistic names for this subCollection
                // getStatisticNames() returns full paths like "PoolSize.current"
                // So we can just use them directly as attributes
                List<String> statNames = getStatisticNames(subColl);
                List<String> attributes = new ArrayList<>(statNames);

                if (!attributes.isEmpty()) {
                    result.put(subCollName, attributes);
                    log.debug("SubCollection '{}' has {} attributes",
                        subCollName, attributes.size());
                }
            } catch (Throwable t) {
                log.debug("Could not process subCollection: {}", t.getMessage());
            }
        }

        return result;
    }

    /**
     * Get a list of subCollections from a Stats instance.
     * Used internally for navigation in getStatisticDataFor.
     */
    private static ArrayList<?> getSubCollections(Object instance) {
        ReflectionHolder rh =
            getOrCreateReflectionHolder(instance.getClass().getClassLoader());

        try {
            // WebSphere JEE Stats are wrapper classes - get actual StatsImpl via getWSImpl()
            MethodHandle mhGetWsImpl = rh.mhJ2eeStatsImplGetWsImpl;
            if (mhGetWsImpl != null) {
                Object wsImpl = mhGetWsImpl.invoke(instance);
                if (wsImpl != null && rh.mhStatsImplSubCollection != null) {
                    return (ArrayList<?>) rh.mhStatsImplSubCollection.invoke(wsImpl);
                }
            }
            return null;
        } catch (Throwable t) {
            log.trace("Could not get subCollections: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Get the name of a subCollection object.
     */
    private static String getName(Object subCollection) {
        ReflectionHolder rh = getOrCreateReflectionHolder(
            subCollection.getClass().getClassLoader());

        try {
            if (rh.mhStatsImplGetName != null) {
                return (String) rh.mhStatsImplGetName.invoke(subCollection);
            }
        } catch (Throwable t) {
            log.trace("Could not get subCollection name: {}", t.getMessage());
        }
        return null;
    }

    /**
     * Get statistic data for a given path in a Stats instance.
     * Handles both direct statistics and subCollection statistics.
     *
     * @param instance Stats instance
     * @param name Statistic path (e.g., "PoolSize.current" or
     *             "subCollectionName.PoolSize.current")
     * @return the statistic value
     * @throws AttributeNotFoundException if not found
     */
    public static long getStatisticDataFor(Object instance, String name)
            throws AttributeNotFoundException {
        ReflectionHolder rh = getOrCreateReflectionHolder(instance.getClass().getClassLoader());
        int idx = name.indexOf(".");
        if (idx == -1) {
            throw new AttributeNotFoundException("Invalid attribute name " + name);
        }

        String firstPart = name.substring(0, idx);
        String remainder = name.substring(idx + 1);

        MethodHandle mhGetStatistic = null;
        if (rh.classJ2eeStatsImpl != null
                && rh.classJ2eeStatsImpl.isAssignableFrom(instance.getClass())) {
            mhGetStatistic = rh.mhJ2eeStatsImplGetStatistic;
        } else if (rh.classStatsImpl != null
                && rh.classStatsImpl.isAssignableFrom(instance.getClass())) {
            mhGetStatistic = rh.mhStatsImplGetStatistic;
        }

        // Try direct statistic first using getStatistic method
        try {
            if (mhGetStatistic != null) {
                Object stat = mhGetStatistic.invoke(instance, firstPart);
                if (stat != null && JeeStatisticsAttributes.isJeeStatistic(stat)) {
                    return dataFor(stat, remainder);
                }
            }
        } catch (Throwable t) {
            log.trace("'{}' is not a direct statistic, trying as subCollection", firstPart);
        }

        // Try as subCollection (firstPart is subCollection name)
        try {
            ArrayList<?> subCollections = getSubCollections(instance);

            if (subCollections != null) {
                for (Object subColl : subCollections) {
                    String subCollName = getName(subColl);
                    if (firstPart.equals(subCollName)) {
                        try {
                            MethodHandle subCollMhGetStatistic = rh.mhStatsImplGetStatistic;
                            if (subCollMhGetStatistic != null) {
                                // Parse remainder to get statistic name and attribute
                                int idx2 = remainder.indexOf(".");
                                if (idx2 != -1) {
                                    String statName = remainder.substring(0, idx2);
                                    String attrName = remainder.substring(idx2 + 1);
                                    log.trace(
                                        "Getting statistic '{}' from subCollection '{}' class {}",
                                        statName, subCollName, subColl.getClass().getName());
                                    Object stat = subCollMhGetStatistic.invoke(subColl, statName);
                                    if (stat != null) {
                                        log.trace(
                                            "Got statistic object of type {}, isJeeStatistic={}",
                                            stat.getClass().getName(),
                                            JeeStatisticsAttributes.isJeeStatistic(stat));
                                        try {
                                            return dataFor(stat, attrName);
                                        } catch (Throwable t3) {
                                            log.debug(
                                                "Failed to get attribute '{}' from statistic: {}",
                                                attrName, t3.getMessage());
                                        }
                                    }
                                    log.debug("Statistic '{}' returned null", statName);
                                }
                            } else {
                                log.debug(
                                    "No getStatistic method found on subCollection class {}",
                                    subColl.getClass().getName());
                            }
                        } catch (Throwable t2) {
                            log.debug("Failed to get statistic from subCollection: {}",
                                t2.getMessage());
                        }

                        // Fallback: try recursive call (for nested subCollections)
                        log.trace("Trying recursive call for subCollection");
                        return getStatisticDataFor(subColl, remainder);
                    }
                }
            }
        } catch (AttributeNotFoundException e) {
            // Re-throw AttributeNotFoundException
            throw e;
        } catch (Throwable t) {
            log.debug("Failed to access subCollection '{}': {}", firstPart, t.getMessage());
        }

        throw new AttributeNotFoundException(
            "Unable to get statistic with name " + name + " from jee stat class "
                + instance.getClass());
    }
}
