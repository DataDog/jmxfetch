package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

@Slf4j
public class DynamicTag {
    private static final Pattern DYNAMIC_TAG_PATTERN = 
        Pattern.compile("^\\$([^#]+)#(?:attribute\\.)?(.+)$");
    
    private final String tagName;
    private final String beanName;
    private final String attributeName;
    
    /** Parse a dynamic tag from a tag key and value. */
    public static DynamicTag parse(String tagKey, String tagValue) {
        if (tagValue == null || !tagValue.startsWith("$")) {
            return null;
        }
        
        Matcher matcher = DYNAMIC_TAG_PATTERN.matcher(tagValue);
        if (!matcher.matches()) {
            log.warn("Invalid dynamic tag format: {}. "
                    + "Expected format: $domain:bean_params#AttributeName", tagValue);
            return null;
        }
        
        String beanName = matcher.group(1);
        String attributeName = matcher.group(2);
        
        return new DynamicTag(tagKey, beanName, attributeName);
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
    
    /** Resolve the dynamic tag by fetching the attribute value from JMX. */
    public Map.Entry<String, String> resolve(MBeanServerConnection connection) {
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
        } catch (AttributeNotFoundException | InstanceNotFoundException 
                | MBeanException | ReflectionException | IOException e) {
            log.warn("Failed to resolve dynamic tag '{}' from bean '{}' attribute '{}': {}", 
                    tagName, beanName, attributeName, e.getMessage());
            log.debug("Dynamic tag resolution error details", e);
            return null;
        }
    }
    
    /** Resolve multiple dynamic tags at once. */
    public static Map<String, String> resolveAll(
            List<DynamicTag> dynamicTags, MBeanServerConnection connection) {
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

