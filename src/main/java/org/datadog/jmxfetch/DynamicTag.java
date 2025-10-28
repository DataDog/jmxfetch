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
    
    /** Parse dynamic tag from configuration map (list entry format). */
    public static DynamicTag parse(Object tagConfig) {
        if (tagConfig == null) {
            return null;
        }
        
        if (!(tagConfig instanceof Map)) {
            log.warn("Invalid dynamic tag config: expected map with 'tag_name', 'bean_name' and "
                    + "'attribute' keys");
            return null;
        }
        
        Map<String, Object> config = (Map<String, Object>) tagConfig;
        Object tagNameObj = config.get("tag_name");
        Object beanObj = config.get("bean_name");
        Object attrObj = config.get("attribute");
        
        if (tagNameObj == null || beanObj == null || attrObj == null) {
            String missing = "Invalid dynamic tag config: missing"
                    + (tagNameObj == null ? " tag_name" : "")
                    + (beanObj == null ? " bean_name" : "")
                    + (attrObj == null ? " attribute" : "");
            log.warn(missing);
            return null;
        }
        
        String tagName = tagNameObj.toString();
        String beanName = beanObj.toString();
        String attributeName = attrObj.toString();
        
        return new DynamicTag(tagName, beanName, attributeName);
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
    
    /** Gets a unique key for the bean and attribute combination. */
    public String getBeanAttributeKey() {
        return beanName + "#" + attributeName;
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

