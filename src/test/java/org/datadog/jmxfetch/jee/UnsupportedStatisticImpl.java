package org.datadog.jmxfetch.jee;

public class UnsupportedStatisticImpl extends BaseStatistic {

  public UnsupportedStatisticImpl(String name) {
    super(name);
  }

  public long getUnsupportedValue() {
    return 0;
  }
}
