package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.TimeStatistic;

public class TimeStatisticImpl extends BaseStatistic implements TimeStatistic {
  private final long count;
  private final long min;
  private final long max;
  private final long total;

  public TimeStatisticImpl(String name, long min, long max, long total, long count) {
    super(name);
    this.count = count;
    this.min = min;
    this.max = max;
    this.total = total;
  }

  @Override
  public long getCount() {
    return count;
  }

  @Override
  public long getMaxTime() {
    return max;
  }

  @Override
  public long getMinTime() {
    return min;
  }

  @Override
  public long getTotalTime() {
    return total;
  }
}
