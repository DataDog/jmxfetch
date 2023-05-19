package org.datadog.misbehavingjmxserver;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

public class SingleEntryTabularData extends AbstractTabularDataMBean {

    public SingleEntryTabularData(final MetricDAO metricsDAO) {
        super(metricsDAO, "Single Value/Row TabularData");
    }

    @Override
    public TabularData getTabularData() throws OpenDataException {
        return this.getTabularDataSupport();
    }

    @Override
    public TabularDataSupport getTabularDataSupport() throws OpenDataException {
        final TabularDataSupport result = new TabularDataSupport(AbstractTabularDataMBean.TABULAR_TYPE);
        result.put(new CompositeDataSupport(COMPOSITE_TYPE, ITEM_NAMES,
            new Object[]{
                "Foo",
                this.metricsDAO.getNumberValue(),
                this.metricsDAO.getDoubleValue(),
                this.metricsDAO.getFloatValue(),
                this.metricsDAO.getBooleanValue()
            }));
        return result;
    }
}
