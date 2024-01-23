package org.datadog.jmxfetch.util;

import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import java.time.Duration;

public class TimerWaitStrategy implements WaitStrategy {

    private final long sleepTimeMillis;

    public TimerWaitStrategy(long sleepTimeMillis) {
        this.sleepTimeMillis = sleepTimeMillis;
    }

    @Override
    public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
        try {
            Thread.sleep(sleepTimeMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("TimerWaitStrategy was interrupted", e);
        }
    }

    @Override
    public WaitStrategy withStartupTimeout(Duration startupTimeout) {
        return this; // This method can be a no-op for this strategy, as the timeout is fixed
    }
}

