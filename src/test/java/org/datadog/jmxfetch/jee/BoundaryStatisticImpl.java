package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.BoundaryStatistic;

public class BoundaryStatisticImpl extends BaseStatistic implements BoundaryStatistic {
  private final long low;
  private final long high;

  public BoundaryStatisticImpl(String name, long low, long high) {
    super(name);
    this.low = low;
    this.high = high;
  }

  @Override
  public long getUpperBound() {
    return high;
  }

  @Override
  public long getLowerBound() {
    return low;
  }
}
