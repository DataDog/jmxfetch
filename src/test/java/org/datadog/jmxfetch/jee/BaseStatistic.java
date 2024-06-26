package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.Statistic;

public abstract class BaseStatistic implements Statistic {
  private final String name;

  public BaseStatistic(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getUnit() {
    return "";
  }

  @Override
  public String getDescription() {
    return "";
  }

  @Override
  public long getStartTime() {
    return 0;
  }

  @Override
  public long getLastSampleTime() {
    return 0;
  }
}
