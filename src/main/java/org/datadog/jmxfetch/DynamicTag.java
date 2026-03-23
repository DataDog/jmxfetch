package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

@Slf4j
public class DynamicTag {
    private final String tagName;
    private final String beanName;
    private final String attributeName;  // null when using key_property mode
    private final String keyProperty;    // null when using attribute mode

    /** Parse dynamic tag from configuration map (list entry format). */
    public static DynamicTag parse(Object tagConfig) {
        if (tagConfig == null) {
            return null;
        }

        if (!(tagConfig instanceof Map)) {
            log.warn("Invalid dynamic tag config: expected map with 'tag_name', 'bean_name' and "
                    + "'attribute' or 'key_property' keys");
            return null;
        }

        Map<String, Object> config = (Map<String, Object>) tagConfig;
        Object tagNameObj = config.get("tag_name");
        Object beanObj = config.get("bean_name");
        Object attrObj = config.get("attribute");
        Object keyPropObj = config.get("key_property");

        if (tagNameObj == null || beanObj == null) {
            String missing = "Invalid dynamic tag config: missing"
                    + (tagNameObj == null ? " tag_name" : "")
                    + (beanObj == null ? " bean_name" : "");
            log.warn(missing);
            return null;
        }

        if (attrObj == null && keyPropObj == null) {
            log.warn("Invalid dynamic tag config: must specify either 'attribute' or 'key_property'");
            return null;
        }

        if (attrObj != null && keyPropObj != null) {
            log.warn("Dynamic tag config specifies both 'attribute' and 'key_property'; "
                    + "using 'key_property'");
        }

        String tagName = tagNameObj.toString();
        String beanName = beanObj.toString();

        if (keyPropObj != null) {
            return new DynamicTag(tagName, beanName, null, keyPropObj.toString());
        } else {
            return new DynamicTag(tagName, beanName, attrObj.toString(), null);
        }
    }

    private DynamicTag(String tagName, String beanName, String attributeName, String keyProperty) {
        this.tagName = tagName;
        this.beanName = beanName;
        this.attributeName = attributeName;
        this.keyProperty = keyProperty;
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

    public String getKeyProperty() {
        return keyProperty;
    }

    public boolean isKeyPropertyMode() {
        return keyProperty != null;
    }

    /** Gets a unique key for the bean and attribute/key_property combination. */
    public String getBeanAttributeKey() {
        if (keyProperty != null) {
            return beanName + "#key:" + keyProperty;
        }
        return beanName + "#" + attributeName;
    }

    /** Resolve the dynamic tag by fetching the value from JMX. */
    public Map.Entry<String, String> resolve(Connection connection) {
        try {
            ObjectName objectName = new ObjectName(beanName);

            if (keyProperty != null) {
                return resolveKeyProperty(connection, objectName);
            } else {
                return resolveAttribute(connection, objectName);
            }
        } catch (MalformedObjectNameException e) {
            log.error("Invalid bean name '{}' for dynamic tag '{}': {}",
                    beanName, tagName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to resolve dynamic tag '{}' from bean '{}': {}",
                    tagName, beanName, e.getMessage());
            log.debug("Dynamic tag resolution error details", e);
            return null;
        }
    }

    private Map.Entry<String, String> resolveAttribute(Connection connection, ObjectName objectName)
            throws Exception {
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
    }

    private Map.Entry<String, String> resolveKeyProperty(Connection connection, ObjectName objectName)
            throws Exception {
        Set<ObjectName> matchingBeans = connection.queryNames(objectName);

        if (matchingBeans == null || matchingBeans.isEmpty()) {
            log.warn("Dynamic tag '{}': no beans found matching '{}'", tagName, beanName);
            return null;
        }

        ObjectName matchedBean = matchingBeans.iterator().next();
        String value = matchedBean.getKeyProperty(keyProperty);

        if (value == null) {
            log.warn("Dynamic tag '{}': bean '{}' does not have key property '{}'",
                    tagName, matchedBean, keyProperty);
            return null;
        }

        log.info("Resolved dynamic tag '{}' to value '{}' from bean '{}' key property '{}'",
                tagName, value, matchedBean, keyProperty);

        return new HashMap.SimpleEntry<>(tagName, value);
    }

    @Override
    public String toString() {
        if (keyProperty != null) {
            return String.format("DynamicTag{name='%s', bean='%s', key_property='%s'}",
                    tagName, beanName, keyProperty);
        }
        return String.format("DynamicTag{name='%s', bean='%s', attribute='%s'}",
                tagName, beanName, attributeName);
    }
}
