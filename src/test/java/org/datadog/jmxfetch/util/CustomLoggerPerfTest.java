package com.timgroup.statsd;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.util.CustomLogger;
import org.datadog.jmxfetch.util.LogLevel;

import java.io.IOException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@Slf4j
@RunWith(Parameterized.class)
public final class CustomLoggerPerfTest {

    private final int duration;  // Duration in secs
    private final int testWorkers;
    private final int msgSize;  // length of log message in bytes
    private final int uPause;  // length of log message in bytes
    private final boolean rfc3339;  // length of log message in bytes

    private AtomicBoolean running;
    private final ExecutorService executor;

    private static Logger log = Logger.getLogger("CustomLoggerPerfTest");

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                 { 90, 100, 1, 128, false },  // 90 seconds, 100 microsecond pause, 1 worker, 128 byte string, false
                 { 90, 100, 1, 512, false },  // 90 seconds, 100 microsecond pause, 1 worker, 512 byte string, false
                 { 90, 100, 1, 1024, false },  // 90 seconds, 100 microsecond pause, 1 worker, 512 byte string, false
                 { 90, 100, 1, 128, true },  // 90 seconds, 100 microsecond pause, 1 worker, 128 byte string, true
                 { 90, 100, 1, 512, true },  // 90 seconds, 100 microsecond pause, 1 worker, 512 byte string, true
                 { 90, 100, 1, 1024, true },  // 90 seconds, 100 microsecond pause, 1 worker, 512 byte string, true
           });
    }

    public CustomLoggerPerfTest(int duration, int uPause, int testWorkers, int msgSize, boolean rfc3339) throws IOException {
        this.duration = duration;
        this.testWorkers = testWorkers;
        this.msgSize = msgSize;
        this.uPause = uPause;
        this.rfc3339 = rfc3339;

        this.executor = Executors.newFixedThreadPool(testWorkers);
        this.running = new AtomicBoolean(true);

        CustomLogger.setup(LogLevel.fromString("INFO"),
                null,   //stdout
                rfc3339);
    }

    /**
     * Run with -Dtest_perf=true if you wish the performance tests defined here to run
     */
    @Before
    public void shouldRun() {
        boolean run = false;
        try {
            run = (System.getProperty("test_perf").compareToIgnoreCase("true") == 0);
        } catch (Exception ex) {
            // NADA
        }
        Assume.assumeTrue(run);
    }

    @Test
    public void perfTest() throws Exception {

        final String msg = getRandomString(msgSize);
        final AtomicInteger count = new AtomicInteger(0);
        final int pause =(int) TimeUnit.MICROSECONDS.toNanos(this.uPause);

        for(int i=0 ; i < this.testWorkers ; i++) {
            executor.submit(new Runnable() {
                public void run()  {
                    while (running.get()) {
                       log.info(msg);
                       count.incrementAndGet();
                       try {
                           Thread.sleep(0, pause);
                        } catch (InterruptedException e) {
                            // pass
                        }
                    }
                }
            });
        }

        Thread.sleep(TimeUnit.SECONDS.toMillis(this.duration));
        running.set(false);

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        StringBuilder sb = new StringBuilder()
            .append("For ")
            .append(this.testWorkers)
            .append(" workers with msg size ")
            .append(this.msgSize)
            .append(" and RFC 3339 mode set to ")
            .append(this.rfc3339)
            .append(" logged ")
            .append(count.get())
            .append(" messsages.");

        System.out.println(sb.toString());
    }

    private String getRandomString(int length) {

        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomLimitedInt = leftLimit + (int)
              (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        String generatedString = buffer.toString();

        return generatedString;
    }
}
