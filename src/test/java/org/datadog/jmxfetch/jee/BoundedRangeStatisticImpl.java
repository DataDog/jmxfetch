package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.BoundedRangeStatistic;

public class BoundedRangeStatisticImpl extends RangeStatisticImpl implements BoundedRangeStatistic {
  private final long low;
  private final long high;

  public BoundedRangeStatisticImpl(
      String name, long min, long max, long current, long low, long high) {
    super(name, min, max, current);
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
