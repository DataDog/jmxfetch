package org.datadog.jmxfetch.util.server;

import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class WaitOrStrategy implements WaitStrategy {

    private final List<WaitStrategy> waitStrategies;

    public WaitOrStrategy(WaitStrategy... strategies) {
        this.waitStrategies = Arrays.asList(strategies);
    }

    @Override
    public WaitOrStrategy withStartupTimeout(java.time.Duration startupTimeout) {
        return this;
    }

    @Override
    public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
        final WaitStrategyTarget target = waitStrategyTarget;
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<?>> futures = new ArrayList<>();

        for (final WaitStrategy strategy : waitStrategies) {
            Future<?> future = executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    strategy.waitUntilReady(target);
                    return null;
                }
            });
            futures.add(future);
        }

        try {
            for (Future<?> future : futures) {
                try {
                    future.get(1, TimeUnit.SECONDS);
                    break; // One of the strategies has completed, we can stop waiting
                } catch (TimeoutException ignored) {
                    // This strategy did not complete yet, try the next one
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WaitOrStrategy was interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Exception during WaitOrStrategy execution", e);
        } finally {
            executor.shutdownNow(); // Make sure to shutdown the executor
        }
    }
}

