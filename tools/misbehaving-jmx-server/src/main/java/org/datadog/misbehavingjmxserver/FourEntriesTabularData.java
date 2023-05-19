package org.datadog.misbehavingjmxserver;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

public class FourEntriesTabularData extends AbstractTabularDataMBean {

    public FourEntriesTabularData(final MetricDAO metricsDAO) {
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
                "One",
                1,
                1.1d,
                1.11f,
                false,
            }));
        result.put(new CompositeDataSupport(COMPOSITE_TYPE, ITEM_NAMES,
            new Object[]{
                "Two",
                2,
                2.2d,
                2.22f,
                false,
            }));
        result.put(new CompositeDataSupport(COMPOSITE_TYPE, ITEM_NAMES,
            new Object[]{
                "Three",
                this.metricsDAO.getNumberValue(),
                this.metricsDAO.getDoubleValue(),
                this.metricsDAO.getFloatValue(),
                this.metricsDAO.getBooleanValue()
            }));
        result.put(new CompositeDataSupport(COMPOSITE_TYPE, ITEM_NAMES,
            new Object[]{
                "Four",
                4,
                4.4d,
                4.44f,
                false,
            }));
        return result;
    }
}
