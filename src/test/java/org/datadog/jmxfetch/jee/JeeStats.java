package org.datadog.jmxfetch.jee;

import javax.management.j2ee.statistics.Statistic;
import javax.management.j2ee.statistics.Stats;
import java.util.Map;

public class JeeStats implements Stats {
    private final Map<String, ? extends Statistic> statistics;

    public JeeStats(Map<String, ? extends Statistic> statistics) {
        this.statistics = statistics;
    }

    @Override
    public Statistic getStatistic(String statisticName) {
        return statistics.get(statisticName);
    }

    @Override
    public String[] getStatisticNames() {
        return statistics.keySet().toArray(new String[0]);
    }

    @Override
    public Statistic[] getStatistics() {
        return statistics.values().toArray(new Statistic[0]);
    }
}
