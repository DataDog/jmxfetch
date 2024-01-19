package org.datadog.jmxfetch.util.server;

import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom wait strategy for Testcontainers to determine when a container is ready.
 *
 * This strategy concurrently checks for two conditions:
 * 1. Whether a specified log message appears in the container's output.
 * 2. Whether a specified port is open and listening for connections.
 *
 * The container is considered ready if either of these conditions is met.
 *
 * If the log message "Server Started" is detected within the given timeout period,
 * the container is immediately considered ready, even if the specified port is not
 * yet open. If the specified port is open and listening before the log message appears
 * or the timeout occurs, the container is also considered ready.
 *
 * If neither condition is met within the timeout period, an IllegalStateException is thrown.
 *
 * Usage:
 * This strategy should be used with the Testcontainers framework when starting a container,
 * to specify the readiness criteria.
 *
 * Example:
 * <pre>{@code
 * GenericContainer container = new GenericContainer("your/image:tag")
 *     .withExposedPorts(8080)
 *     .waitingFor(new CustomWaitStrategy(8080, "Server Started", Duration.ofSeconds(10)));
 * container.start();
 * }</pre>
 *
 * @param port The port to check for availability.
 * @param logMessage The log message to wait for.
 * @param timeout The maximum time to wait for either condition to be met.
 */
public class CustomWaitStrategy extends AbstractWaitStrategy {

    private final int port;
    private final String logMessage;
    private final Duration timeout;


    public CustomWaitStrategy(int port, String logMessage, Duration timeout) {
        this.port = port;
        this.logMessage = logMessage;
        this.timeout = timeout;
    }

    @Override
    protected void waitUntilReady() {
        final AtomicBoolean logMessageFound = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        // Thread for log message checking
        new Thread(new Runnable() {
            @Override
            public void run() {
                WaitingConsumer waitingConsumer = new WaitingConsumer();
                waitStrategyTarget.getLogsOutput().subscribe(waitingConsumer);

                try {
                    waitingConsumer.waitUntil(new Predicate<OutputFrame>() {
                        @Override
                        public boolean test(OutputFrame outputFrame) {
                            boolean contains = outputFrame.getUtf8String().contains(logMessage);
                            if (contains) {
                                logMessageFound.set(true);
                                latch.countDown();
                            }
                            return contains;
                        }
                    }, timeout.getSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // Log message not found within the timeout
                    latch.countDown();
                }
            }
        }).start();

        // Main loop for port checking
        while (latch.getCount() > 0 && !waitStrategyTarget.isPortOpen(port)) {
            try {
                // Check every 250ms
                latch.await(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // If the port is open or the log message is found, the wait is over
        if (waitStrategyTarget.isPortOpen(port) || logMessageFound.get()) {
            return;
        }

        // If neither condition is met within the timeout, throw an exception
        throw new IllegalStateException("Neither log message was found nor was the port open within the timeout");
    }

    // Java 7 compatible Predicate interface
    private interface Predicate<T> {
        boolean test(T t);
    }
}
