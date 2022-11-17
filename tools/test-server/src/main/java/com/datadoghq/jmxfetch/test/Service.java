package com.datadoghq.jmxfetch.test;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Service {
    public static void main(String[] args) throws Exception {
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        final MetricsMBean metrics = new Metrics("Example");
        mbs.registerMBean(metrics, null);
        final TimerTask task = new TimerTask() {
            public void run() {
                System.out.println("Task performed on: " + new Date() + ", " +
                        "Thread's name: " + Thread.currentThread().getName());
                metrics.Do();
            }
        };
        final Timer timer = new Timer("Timer");
        final long delay = 1000L;
        timer.scheduleAtFixedRate(task, delay, delay);
        Thread.currentThread().join();
    }
}
