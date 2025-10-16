package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

@Slf4j
public class DynamicTag {
    private final String tagName;
    private final String beanName;
    private final String attributeName;
    
    /** Parse dynamic tag from configuration map. */
    public static DynamicTag parse(String tagKey, Object tagConfig) {
        if (tagConfig == null) {
            return null;
        }
        
        if (!(tagConfig instanceof Map)) {
            log.warn("Invalid dynamic tag config for '{}': expected map with 'bean' and "
                    + "'attribute' keys", tagKey);
            return null;
        }
        
        Map<String, Object> config = (Map<String, Object>) tagConfig;
        Object beanObj = config.get("bean");
        Object attrObj = config.get("attribute");
        
        if (beanObj == null || attrObj == null) {
            log.warn("Invalid dynamic tag config for '{}': missing 'bean' or 'attribute' key", 
                    tagKey);
            return null;
        }
        
        String beanName = beanObj.toString();
        String attributeName = attrObj.toString();
        
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

    @Override
    public String toString() {
        return String.format("DynamicTag{name='%s', bean='%s', attribute='%s'}", 
                tagName, beanName, attributeName);
    }
}

