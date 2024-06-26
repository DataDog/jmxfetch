package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.RangeStatistic;

public class RangeStatisticImpl extends BaseStatistic implements RangeStatistic {
  private final long current;
  private final long max;
  private final long min;

  public RangeStatisticImpl(String name, long min, long max, long current) {
    super(name);
    this.current = current;
    this.max = max;
    this.min = min;
  }

  @Override
  public long getHighWaterMark() {
    return max;
  }

  @Override
  public long getLowWaterMark() {
    return min;
  }

  @Override
  public long getCurrent() {
    return current;
  }
}
