package org.datadog.misbehavingjmxserver;

import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

public interface MetricsMBean {
    String getName();

    Number getNumberValue();

    Double getDoubleValue();

    Float getFloatValue();

    Boolean getBooleanValue();

    TabularData getTabularData();

    TabularDataSupport getTabularDataSupport();

}
