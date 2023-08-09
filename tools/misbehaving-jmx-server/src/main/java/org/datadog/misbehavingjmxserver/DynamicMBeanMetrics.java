package org.datadog.misbehavingjmxserver;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicMBeanMetrics implements DynamicMBean {
    public final String name;
    private Map<String, Object> attributes = new HashMap<>();
    private final MetricDAO metricsDAO;
    private int numCompPerTabular;

    public DynamicMBeanMetrics(final String name, int numAttributes, int numTabulars, int numCompPerTabular, final MetricDAO metricsDAO) {
        this.name = name;
        this.metricsDAO = metricsDAO;
        this.numCompPerTabular = numCompPerTabular;

        // Add dummy attributes during object initialization
        for (int i = 1; i <= numAttributes; i++){
            attributes.put("Attribute" + String.valueOf(i), getSimpleAttributeValue());
        }

        for (int i = 1; i <= numTabulars; i++) {
            try {

                attributes.put("Tabular" + String.valueOf(i),getTabularData());
            } catch (Exception e) {
                log.warn("Tabular" + String.valueOf(i) + " threw an exception: ", e);
            }
        }

    }

    public Number getSimpleAttributeValue() {
        return this.metricsDAO.getNumberValue();
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException {
        if (!attributes.containsKey(attribute)) {
            throw new AttributeNotFoundException("Attribute '" + attribute + "' not found.");
        }
        return attributes.get(attribute);
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException {
        String attributeName = attribute.getName();
        if (!attributes.containsKey(attributeName)) {
            throw new AttributeNotFoundException("Attribute '" + attributeName + "' not found.");
        }
        attributes.put(attributeName, attribute.getValue());
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        AttributeList result = new AttributeList();
        for (String attr : attributes) {
            try {
                Object value = getAttribute(attr);
                result.add(new Attribute(attr, value));
            } catch (Exception e) {
                // Ignore attributes that couldn't be retrieved
            }
        }
        return result;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList result = new AttributeList();
        for (Object obj : attributes) {
            if (obj instanceof Attribute) {
                Attribute attr = (Attribute) obj;
                try {
                    setAttribute(attr);
                    result.add(attr);
                } catch (Exception e) {
                    // Ignore attributes that couldn't be set
                }
            }
        }
        return result;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
            throws MBeanException, ReflectionException {
        throw new UnsupportedOperationException("Not supported in misbehaving-jmx-server.");
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        MBeanAttributeInfo[] attributeInfos = new MBeanAttributeInfo[attributes.size()];
        int i = 0;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue().getClass().getName();
            String description = "Dummy attribute " + name;
            boolean isReadable = true;
            boolean isWritable = true;
            boolean isIs = false;
            attributeInfos[i++] = new MBeanAttributeInfo(name, type, description, isReadable, isWritable, isIs);
        }

        return new MBeanInfo(
                this.getClass().getName(),
                "Dynamic MBean with dummy attributes",
                attributeInfos,
                null,
                null,
                null
        );
    }

    public TabularData getTabularData() {
        String[] itemNames = {"Id", "Value"};
        String[] itemDescriptions = {"Attribute Id", "Attribute Value"};
        OpenType<?>[] itemTypes = {SimpleType.INTEGER, SimpleType.INTEGER};

        try {
            CompositeType rowType = new CompositeType("AttributeRow", "Attribute Row", itemNames, itemDescriptions, itemTypes);

            TabularType tabularType = new TabularType("AttributeTable", "Table of Attributes", rowType, new String[]{"Id"});

            TabularDataSupport tabularData = new TabularDataSupport(tabularType);

            Map<Object, Object> compositeData = new HashMap<>();

            for (int i = 1; i <= numCompPerTabular; i++){
                compositeData.put(i, getSimpleAttributeValue());
            }

            // Add dummy data to the TabularData
            for (Map.Entry<Object, Object> entry : compositeData.entrySet()) {
                Object attributeId = entry.getKey();
                Object attributeValue = entry.getValue();

                CompositeData row = new CompositeDataSupport(rowType, new String[]{"Id", "Value"}, new Object[]{attributeId, attributeValue});
                tabularData.put(row);
            }

            return tabularData;
        } catch ( OpenDataException  e) {
            log.warn("Unable to create TabularData", e);
            return null;
        }
    }

    public void updateBeanAttributes() {
        log.debug("Updating attribute values for " + this.name);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                attributes.put(entry.getKey(), getSimpleAttributeValue());
            }
            if(entry.getValue() instanceof TabularDataSupport){
                attributes.put(entry.getKey(), getTabularData());
            }
        }
    }


}
