package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.service.ServiceNameProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;

@Slf4j
class AttributeMatcher {

    private static final String ATTRIBUTE = "Attribute: ";

    private final List<Configuration> configurationList;
    private final Connection connection;
    private final String instanceName;
    private final String checkName;
    private final Map<String, String> tags;
    private final boolean cassandraAliasing;
    private final boolean emptyDefaultHostname;
    private final boolean normalizeBeanParamTags;
    private final boolean useCanonicalBeanName;
    private final int maxReturnedMetrics;
    private final ServiceNameProvider serviceNameProvider;
    private final AppConfig appConfig;

    private List<JmxAttribute> matchingAttributes = new ArrayList<JmxAttribute>();
    private List<JmxAttribute> failingAttributes = new ArrayList<JmxAttribute>();
    private boolean limitReached = false;
    private int beansWithAttributeMatch = 0;

    AttributeMatcher(
            List<Configuration> configurationList,
            Connection connection,
            String instanceName,
            String checkName,
            Map<String, String> tags,
            boolean cassandraAliasing,
            boolean emptyDefaultHostname,
            boolean normalizeBeanParamTags,
            boolean useCanonicalBeanName,
            int maxReturnedMetrics,
            ServiceNameProvider serviceNameProvider,
            AppConfig appConfig) {
        this.configurationList = configurationList;
        this.connection = connection;
        this.instanceName = instanceName;
        this.checkName = checkName;
        this.tags = tags;
        this.cassandraAliasing = cassandraAliasing;
        this.emptyDefaultHostname = emptyDefaultHostname;
        this.normalizeBeanParamTags = normalizeBeanParamTags;
        this.useCanonicalBeanName = useCanonicalBeanName;
        this.maxReturnedMetrics = maxReturnedMetrics;
        this.serviceNameProvider = serviceNameProvider;
        this.appConfig = appConfig;
    }

    /**
     * Runs the attribute matching loop over the provided bean set.
     *
     * @param beans the set of bean names to scan
     * @param resolvedDynamicTagsPerConfig per-config resolved dynamic tags
     * @param instanceDisplayName instance reference used by the reporter for display
     */
    void run(
            Set<ObjectName> beans,
            Map<Configuration, Map<String, String>> resolvedDynamicTagsPerConfig,
            Instance instanceDisplayName) throws IOException {
        limitReached = false;
        Reporter reporter = appConfig.getReporter();
        String action = appConfig.getAction();
        boolean metricReachedDisplayed = false;

        matchingAttributes = new ArrayList<JmxAttribute>();
        failingAttributes = new ArrayList<JmxAttribute>();
        int metricsCount = 0;

        beansWithAttributeMatch = 0;

        if (!action.equals(AppConfig.ACTION_COLLECT)) {
            reporter.displayInstanceName(instanceDisplayName);
        }

        for (ObjectName beanName : beans) {
            boolean attributeMatched = false;
            if (limitReached) {
                log.debug("Limit reached");
                if (action.equals(AppConfig.ACTION_COLLECT)) {
                    break;
                }
            }
            String className;
            MBeanAttributeInfo[] attributeInfos;
            String beanNameStr = beanName.getCanonicalName();
            try {
                log.debug("Getting bean info for bean: {}", beanNameStr);
                MBeanInfo info = connection.getMBeanInfo(beanName);

                log.debug("Getting class name for bean: {}", beanNameStr);
                className = info.getClassName();
                log.debug("Getting attributes for bean: {}", beanNameStr);
                attributeInfos = info.getAttributes();
            } catch (IOException e) {
                // we should not continue
                throw e;
            } catch (Exception e) {
                log.warn("Cannot get attributes or class name for bean {}: ", beanNameStr, e);
                continue;
            }

            for (MBeanAttributeInfo attributeInfo : attributeInfos) {
                if (metricsCount >= maxReturnedMetrics) {
                    limitReached = true;
                    if (action.equals(AppConfig.ACTION_COLLECT)) {
                        log.warn("Maximum number of metrics reached.");
                        break;
                    } else if (!metricReachedDisplayed
                            && !action.equals(AppConfig.ACTION_LIST_COLLECTED)
                            && !action.equals(AppConfig.ACTION_LIST_NOT_MATCHING)) {
                        reporter.displayMetricReached();
                        metricReachedDisplayed = true;
                    }
                }
                JmxAttribute jmxAttribute;
                String attributeType = attributeInfo.getType();

                if (JmxSimpleAttribute.matchAttributeType(attributeType)) {
                    log.debug(
                            ATTRIBUTE
                            + beanNameStr
                            + " : "
                            + attributeInfo
                            + " has attributeInfo simple type");
                    jmxAttribute =
                        new JmxSimpleAttribute(
                                attributeInfo,
                                beanName,
                                className,
                                instanceName,
                                checkName,
                                connection,
                                serviceNameProvider,
                                tags,
                                cassandraAliasing,
                                emptyDefaultHostname,
                                normalizeBeanParamTags,
                                useCanonicalBeanName);
                } else if (JmxComplexAttribute.matchAttributeType(attributeType)) {
                    log.debug(
                            ATTRIBUTE
                            + beanNameStr
                            + " : "
                            + attributeInfo
                            + " has attributeInfo composite type");
                    jmxAttribute =
                        new JmxComplexAttribute(
                                attributeInfo,
                                beanName,
                                className,
                                instanceName,
                                checkName,
                                connection,
                                serviceNameProvider,
                                tags,
                                emptyDefaultHostname,
                                normalizeBeanParamTags,
                                useCanonicalBeanName);
                } else if (JmxTabularAttribute.matchAttributeType(attributeType)) {
                    log.debug(
                            ATTRIBUTE
                            + beanNameStr
                            + " : "
                            + attributeInfo
                            + " has attributeInfo tabular type");
                    jmxAttribute =
                        new JmxTabularAttribute(
                                attributeInfo,
                                beanName,
                                className,
                                instanceName,
                                checkName,
                                connection,
                                serviceNameProvider,
                                tags,
                                emptyDefaultHostname,
                                normalizeBeanParamTags,
                                useCanonicalBeanName);
                } else {
                    try {
                        log.debug(
                                ATTRIBUTE
                                + beanNameStr
                                + " : "
                                + attributeInfo
                                + " has an unsupported type: "
                                + attributeType);
                    } catch (NullPointerException e) {
                        log.warn("Caught unexpected NullPointerException");
                    }
                    continue;
                }

                // For each attribute we try it with each configuration to see if there is one that
                // matches
                // If so, we store the attribute so metrics will be collected from it. Otherwise we
                // discard it.
                for (Configuration conf : configurationList) {
                    try {
                        if (jmxAttribute.match(conf)) {
                            Map<String, String> resolvedDynamicTags =
                                    (resolvedDynamicTagsPerConfig != null
                                            ? resolvedDynamicTagsPerConfig.get(conf)
                                            : null);
                            jmxAttribute.setResolvedDynamicTags(resolvedDynamicTags);
                            jmxAttribute.setMatchingConf(conf);
                            metricsCount += jmxAttribute.getMetricsCount();
                            matchingAttributes.add(jmxAttribute);

                            if (action.equals(AppConfig.ACTION_LIST_EVERYTHING)
                                    || action.equals(AppConfig.ACTION_LIST_MATCHING)
                                    || action.equals(AppConfig.ACTION_LIST_COLLECTED)
                                    && !limitReached
                                    || action.equals(AppConfig.ACTION_LIST_LIMITED)
                                    && limitReached) {
                                reporter.displayMatchingAttributeName(
                                        jmxAttribute, metricsCount, maxReturnedMetrics);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        log.error(
                                "Error while trying to match attributeInfo configuration "
                                + "with the Attribute: "
                                + beanNameStr
                                + " : "
                                + attributeInfo,
                                e);
                    }
                }
                if (jmxAttribute.getMatchingConf() == null
                        && (action.equals(AppConfig.ACTION_LIST_EVERYTHING)
                            || action.equals(AppConfig.ACTION_LIST_NOT_MATCHING))) {
                    reporter.displayNonMatchingAttributeName(jmxAttribute);
                }
                if (jmxAttribute.getMatchingConf() != null) {
                    attributeMatched = true;
                }
            }
            if (attributeMatched) {
                beansWithAttributeMatch += 1;
            }
        }
        log.info("Found {} matching attributes", matchingAttributes.size());
    }

    List<JmxAttribute> getMatchingAttributes() {
        return matchingAttributes;
    }

    List<JmxAttribute> getFailingAttributes() {
        return failingAttributes;
    }

    boolean isLimitReached() {
        return limitReached;
    }

    int getBeansWithAttributeMatch() {
        return beansWithAttributeMatch;
    }
}
