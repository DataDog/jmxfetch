package com.datadoghq.jmxfetch.test;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

public class Metrics implements MetricsMBean, MBeanRegistration {
    private final String name;
    private final MetricsDAO metricsDAO;
    private final TabularDataSupport tabularData;

    public Metrics(final String name, final MetricsDAO metricsDAO) {
        this.name = name;
        this.metricsDAO = metricsDAO;

        try {
            final CompositeType rowType = new CompositeType(
                    "myCompositeType",
                    "My composite type",
                    new String[]{"foo", "bar", "toto"},
                    new String[]{
                            "Description of `foo`", "Description of `bar`", "Description of `toto`"
                    },
                    new OpenType[]{SimpleType.STRING, SimpleType.INTEGER, SimpleType.STRING});
            final TabularType tabularType =
                    new TabularType(
                            "myTabularType", "My tabular type", rowType, new String[]{"foo"});
            /*
            final String[] itemNamesDescriptionsAndIndexName = {
                    "Name",
                    "Number",
            };
            final OpenType[] itemTypes = {
                    SimpleType.STRING,
                    SimpleType.INTEGER,
            };
            final CompositeType pageType = new CompositeType(
                    "compositeType",
                    "CompositeType info",
                    itemNamesDescriptionsAndIndexName,
                    itemNamesDescriptionsAndIndexName,
                    itemTypes
            );
            final TabularType tabularType = new TabularType(
                    "CustomActionTable",
                    "CustomAction table",
                    pageType,
                    itemNamesDescriptionsAndIndexName
            );*/
            this.tabularData = new TabularDataSupport(tabularType);
//            this.tabularData.put(new CompositeDataSupport(pageType, itemNamesDescriptionsAndIndexName, new Object[]{
//                    "Id1",
//                    101
//            }));
            this.tabularData.put(new CompositeDataSupport(
                    rowType,
                    new String[]{"foo", "bar", "toto"},
                    new Object[]{"1", 1, "tata"}));
            System.out.println("Mbean " + name + " ready! " + metricsDAO.toString());
        } catch (OpenDataException e) {
            System.err.println(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Number getNumberValue() {
        return this.metricsDAO.getNumberValue();
    }

    @Override
    public Double getDoubleValue() {
        return this.metricsDAO.getDoubleValue();
    }

    @Override
    public Float getFloatValue() {
        return this.metricsDAO.getFloatValue();
    }

    @Override
    public Boolean getBooleanValue() {
        return this.metricsDAO.getBooleanValue();
    }

    @Override
    public TabularData getTabularData() {
        return this.tabularData;
    }
    @Override
    public TabularDataSupport getTabularDataSupport() {
        return this.tabularData;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName("test:type=MyMBean,name=" + this.name);
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
}
