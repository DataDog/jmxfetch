package com.datadoghq.jmxfetch.test;

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
