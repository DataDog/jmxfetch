package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles parsing and resolution of dynamic tags that reference JMX bean attribute values.
 * 
 * <p>Dynamic tags allow you to extract values from JMX bean attributes and use them as tags
 * on all metrics. This is useful for adding contextual information like cluster IDs, version
 * numbers, or other dynamic configuration values.
 * 
 * <p>Supported formats:
 * <ul>
 *   <li>Simple attribute: {@code tag_name:$domain:bean_params#AttributeName}</li>
 *   <li>With "attribute" prefix: {@code tag_name:$domain:bean_params#attribute.AttributeName}</li>
 * </ul>
 * 
 * <p>Example configuration:
 * <pre>
 * instances:
 *   - host: kafka
 *     port: 9101
 *     tags:
 *       - env:local
 *       - cluster_id:$kafka.server:type=KafkaServer,name=ClusterId#Value
 * </pre>
 */
@Slf4j
public class DynamicTag {
    // Pattern to match dynamic tag references like:
    // $domain:type=Something,name=Something#AttributeName
    // $domain:type=Something,name=Something#attribute.AttributeName
    private static final Pattern DYNAMIC_TAG_PATTERN = 
        Pattern.compile("^\\$([^#]+)#(?:attribute\\.)?(.+)$");
    
    private final String tagName;
    private final String beanName;
    private final String attributeName;
    
    /**
     * Creates a DynamicTag from a tag key and value.
     * 
     * @param tagKey the tag name (e.g., "cluster_id")
     * @param tagValue the tag value with dynamic reference (e.g., "$kafka.server:type=KafkaServer#Value")
     * @return a DynamicTag instance, or null if the tag is not a dynamic tag
     */
    public static DynamicTag parse(String tagKey, String tagValue) {
        if (tagValue == null || !tagValue.startsWith("$")) {
            return null;
        }
        
        Matcher matcher = DYNAMIC_TAG_PATTERN.matcher(tagValue);
        if (!matcher.matches()) {
            log.warn("Invalid dynamic tag format: {}. Expected format: $domain:bean_params#AttributeName", 
                    tagValue);
            return null;
        }
        
        String beanName = matcher.group(1);
        String attributeName = matcher.group(2);
        
        return new DynamicTag(tagKey, beanName, attributeName);
    }
    
    /**
     * Parses dynamic tags from a list of tag strings.
     * 
     * @param tags list of tag strings (e.g., ["env:prod", "cluster_id:$kafka.server:type=KafkaServer#Value"])
     * @return list of DynamicTag instances
     */
    public static List<DynamicTag> parseFromList(List<String> tags) {
        List<DynamicTag> dynamicTags = new ArrayList<>();
        if (tags == null) {
            return dynamicTags;
        }
        
        for (String tag : tags) {
            int colonIndex = tag.indexOf(':');
            if (colonIndex > 0) {
                String tagKey = tag.substring(0, colonIndex);
                String tagValue = tag.substring(colonIndex + 1);
                DynamicTag dynamicTag = parse(tagKey, tagValue);
                if (dynamicTag != null) {
                    dynamicTags.add(dynamicTag);
                }
            }
        }
        
        return dynamicTags;
    }
    
    /**
     * Parses dynamic tags from a map of tag key-value pairs.
     * 
     * @param tags map of tag key-value pairs
     * @return list of DynamicTag instances
     */
    public static List<DynamicTag> parseFromMap(Map<String, String> tags) {
        List<DynamicTag> dynamicTags = new ArrayList<>();
        if (tags == null) {
            return dynamicTags;
        }
        
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            DynamicTag dynamicTag = parse(entry.getKey(), entry.getValue());
            if (dynamicTag != null) {
                dynamicTags.add(dynamicTag);
            }
        }
        
        return dynamicTags;
    }
    
    /**
     * Parses dynamic tags from the tag object which could be a List or Map.
     * 
     * @param tagsObj the tags object from YAML configuration
     * @return list of DynamicTag instances
     */
    @SuppressWarnings("unchecked")
    public static List<DynamicTag> parseFromObject(Object tagsObj) {
        if (tagsObj == null) {
            return new ArrayList<>();
        }
        
        if (tagsObj instanceof Map) {
            return parseFromMap((Map<String, String>) tagsObj);
        } else if (tagsObj instanceof List) {
            return parseFromList((List<String>) tagsObj);
        }
        
        return new ArrayList<>();
    }
    
    private DynamicTag(String tagName, String beanName, String attributeName) {
        this.tagName = tagName;
        this.beanName = beanName;
        this.attributeName = attributeName;
    }
    
    public String getTagName() {
        return tagName;
    }
    
    public String getBeanName() {
        return beanName;
    }
    
    public String getAttributeName() {
        return attributeName;
    }
    
    /**
     * Resolves the dynamic tag by fetching the actual value from the JMX server.
     * 
     * @param connection the JMX connection to use
     * @return a map entry with the tag name and resolved value, or null if resolution failed
     */
    public Map.Entry<String, String> resolve(Connection connection) {
        try {
            ObjectName objectName = new ObjectName(beanName);
            Object value = connection.getAttribute(objectName, attributeName);
            
            if (value == null) {
                log.warn("Dynamic tag '{}' resolved to null for bean '{}' attribute '{}'", 
                        tagName, beanName, attributeName);
                return null;
            }
            
            String stringValue = value.toString();
            log.info("Resolved dynamic tag '{}' to value '{}' from bean '{}' attribute '{}'", 
                    tagName, stringValue, beanName, attributeName);
            
            return new HashMap.SimpleEntry<>(tagName, stringValue);
            
        } catch (MalformedObjectNameException e) {
            log.error("Invalid bean name '{}' for dynamic tag '{}': {}", 
                    beanName, tagName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to resolve dynamic tag '{}' from bean '{}' attribute '{}': {}", 
                    tagName, beanName, attributeName, e.getMessage());
            log.debug("Dynamic tag resolution error details", e);
            return null;
        }
    }
    
    /**
     * Resolves multiple dynamic tags using the provided connection.
     * 
     * @param dynamicTags list of dynamic tags to resolve
     * @param connection the JMX connection to use
     * @return map of resolved tag names to values
     */
    public static Map<String, String> resolveAll(List<DynamicTag> dynamicTags, Connection connection) {
        Map<String, String> resolvedTags = new HashMap<>();
        
        if (dynamicTags == null || dynamicTags.isEmpty()) {
            return resolvedTags;
        }
        
        for (DynamicTag dynamicTag : dynamicTags) {
            Map.Entry<String, String> resolved = dynamicTag.resolve(connection);
            if (resolved != null) {
                resolvedTags.put(resolved.getKey(), resolved.getValue());
            }
        }
        
        return resolvedTags;
    }
    
    @Override
    public String toString() {
        return String.format("DynamicTag{name='%s', bean='%s', attribute='%s'}", 
                tagName, beanName, attributeName);
    }
}

