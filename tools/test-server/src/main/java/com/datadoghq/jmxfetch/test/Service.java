package com.datadoghq.jmxfetch.test;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Service {
    public static void main(String[] args) throws Exception {
        long totalMetrics = 1;
        if (args.length == 1) {
            totalMetrics = Long.parseLong(args[0]);
        }
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        final MetricsDAO metricsDAO = new MetricsDAO();
        for (int i = 0; i < totalMetrics; i++) {
            final String metricName = String.format("Example_%010d", i);
            final MetricsMBean metrics = new Metrics(metricName, metricsDAO);
            mbs.registerMBean(metrics, null);
        }
        final TimerTask task = new TimerTask() {
            public void run() {
                System.out.println("Task performed on: " + new Date() + ", " +
                        "Thread's name: " + Thread.currentThread().getName());
                metricsDAO.Do();
            }
        };
        final Timer timer = new Timer("Timer");
        final long delay = 1000L;
        timer.scheduleAtFixedRate(task, delay, delay);
        Thread.currentThread().join();
    }
}
