package org.datadog.misbehavingjmxserver;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;

public abstract class AbstractTabularDataMBean  implements TabularDataMBean, MBeanRegistration, DynamicMBean {

    private final String dClassName = this.getClass().getName();
    protected final MetricDAO metricsDAO;
    private final MBeanInfo dMBeanInfo;
    private final MBeanAttributeInfo[] dAttributes;

    protected static final String[] ITEM_NAMES = new String[]{
        "some_name",
        "number_value",
        "double_value",
        "float_value",
        "boolean_value",
    };
    protected static final String[] ITEM_DESCS = new String[]{
        "Some name",
        "Number value",
        "Double value",
        "Float value",
        "Boolean value"
    };
    protected static final String TYPE_NAME = "SingleValueTabularData";

    protected static final String ROW_DESC = "SingleTabularDataRow";

    protected static final OpenType<?>[] ITEM_TYPES;

    protected static final CompositeType COMPOSITE_TYPE;

    protected static final TabularType TABULAR_TYPE;

    static {
        try {
            ITEM_TYPES = new OpenType[]{
                SimpleType.STRING,
                SimpleType.INTEGER,
                SimpleType.DOUBLE,
                SimpleType.FLOAT,
                SimpleType.BOOLEAN,
            };

            COMPOSITE_TYPE = new CompositeType(TYPE_NAME, ROW_DESC, ITEM_NAMES, ITEM_DESCS,
                ITEM_TYPES);

            TABULAR_TYPE = new TabularType(TYPE_NAME, ROW_DESC, COMPOSITE_TYPE, ITEM_NAMES);
        } catch (OpenDataException e) {
            throw new RuntimeException(e);
        }
    }


    protected AbstractTabularDataMBean(final MetricDAO metricsDAO, final String dDescription) {
        this.metricsDAO = metricsDAO;
        dAttributes = new MBeanAttributeInfo[2];
        dAttributes[0] = new MBeanAttributeInfo(
            "TabularData", // name
            "javax.management.openmbean.TabularData", // type
            "TabularData: Some tabular data", // description
            true, // isReadable
            false, // isWritable
            false); // isIs
        dAttributes[1] = new MBeanAttributeInfo(
            "TabularDataSupport", // name
            "javax.management.openmbean.TabularDataSupport", // type
            "TabularDataSupport: Some tabular data", // description
            true, // isReadable
            false, // isWritable
            false); // isIs
        this.dMBeanInfo = new MBeanInfo(
            this.dClassName,
            dDescription,
            dAttributes,
            null,
            null,
            new MBeanNotificationInfo[0]);
    }


    @Override
    public ObjectName preRegister(final MBeanServer server, final ObjectName name)
        throws Exception {
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {

    }

    @Override
    public void preDeregister() throws Exception {

    }

    @Override
    public void postDeregister() {

    }

    @Override
    public Object getAttribute(final String attribute)
        throws AttributeNotFoundException, MBeanException, ReflectionException {

        // Check attribute_name to avoid NullPointerException later on
        if (attribute == null) {
            throw new RuntimeOperationsException(
                new IllegalArgumentException("Attribute name cannot be null"),
                "Cannot invoke a getter of " + dClassName +
                    " with null attribute name");
        }

        // Call the corresponding getter for a recognized attribute_name
        if (attribute.equals("TabularData")) {
            try {
                return this.getTabularData();
            } catch (OpenDataException e) {
                throw new RuntimeException(e);
            }
        }
        if (attribute.equals("TabularDataSupport")) {
            try {
                return this.getTabularDataSupport();
            } catch (OpenDataException e) {
                throw new RuntimeException(e);
            }
        }

        // If attribute_name has not been recognized
        throw(new AttributeNotFoundException(
            "Cannot find " + attribute + " attribute in " + dClassName));
    }

    @Override
    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {

    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        return null;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        return null;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
        throws MBeanException, ReflectionException {
        return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return this.dMBeanInfo;
    }
}
