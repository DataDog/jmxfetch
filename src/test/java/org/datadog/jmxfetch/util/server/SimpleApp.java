package org.datadog.jmxfetch.util.server;

import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

// TODO: Create tests to check all supported versions of Java work with this server - AMLII-1354
class SimpleApp {
    public interface SampleMBean {

        Integer getShouldBe100();

        Double getShouldBe1000();

        Long getShouldBe1337();

        Float getShouldBe1_1();

        int getShouldBeCounter();
    }

    public static class Sample implements SampleMBean {

        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Integer getShouldBe100() {
            return 100;
        }

        @Override
        public Double getShouldBe1000() {
            return 200.0;
        }

        @Override
        public Long getShouldBe1337() {
            return 1337L;
        }

        @Override
        public Float getShouldBe1_1() {
            return 1.1F;
        }

        @Override
        public int getShouldBeCounter() {
            return this.counter.get();
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting sample app...");
        try {
            final Hashtable<String, String> pairs = new Hashtable<>();
            pairs.put("name", "default");
            pairs.put("type", "simple");
            final Thread daemonThread = getThread(pairs);
            daemonThread.start();
            System.out.println("Sample app started.");
            daemonThread.join();
        } catch (MalformedObjectNameException | InstanceAlreadyExistsException |
                 MBeanRegistrationException | NotCompliantMBeanException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Thread getThread(final Hashtable<String, String> pairs)
            throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        final ObjectName objectName = new ObjectName("dd.test.sample", pairs);
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final Sample sample = new Sample();
        server.registerMBean(sample, objectName);
        final Thread daemonThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (sample.counter.incrementAndGet() > 0) {
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toSeconds(5));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        daemonThread.setDaemon(true);
        return daemonThread;
    }
}
