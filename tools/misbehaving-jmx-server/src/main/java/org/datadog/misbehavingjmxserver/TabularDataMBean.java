package org.datadog.misbehavingjmxserver;

import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

public interface TabularDataMBean {

    TabularData getTabularData() throws OpenDataException;

    TabularDataSupport getTabularDataSupport() throws OpenDataException;
}
