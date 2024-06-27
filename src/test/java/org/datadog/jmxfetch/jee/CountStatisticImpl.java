package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.CountStatistic;

public class CountStatisticImpl extends BaseStatistic implements CountStatistic {
  private final long count;

  public CountStatisticImpl(String name, long count) {
    super(name);
    this.count = count;
  }

  @Override
  public long getCount() {
    return count;
  }
}
