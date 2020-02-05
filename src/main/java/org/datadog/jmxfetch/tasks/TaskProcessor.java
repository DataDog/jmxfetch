package org.datadog.jmxfetch.tasks;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.InstanceTask;
import org.datadog.jmxfetch.reporter.Reporter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TaskProcessor {
    private Reporter reporter;
    private ExecutorService threadPoolExecutor;

    /**
     * TaskProcess constructor.
     * */
    public TaskProcessor(ExecutorService executor, Reporter reporter) {
        threadPoolExecutor = executor;
        this.reporter = reporter;
    }

    public void setThreadPoolExecutor(ExecutorService executor) {
        threadPoolExecutor = executor;
    }

    /**
     * Returns whether or not the executor service has threads available
     * for work on tasks.
     * */
    public boolean ready() {
        if (threadPoolExecutor == null) {
            // assumes we are in embedded mode and tasks will process by the calling thread
            return true;
        }
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) threadPoolExecutor;
        return !tpe.isTerminated() && !(tpe.getMaximumPoolSize() == tpe.getActiveCount());
    }

    /**
     * Processes the list of InstanceTasks within a set timeout deadline.
     * */
    public <T> List<TaskStatusHandler> processTasks(
            List<InstanceTask<T>> tasks, int timeout, TimeUnit timeUnit, TaskMethod<T> processor)
            throws Exception {
        List<TaskStatusHandler> statuses = new ArrayList<TaskStatusHandler>();
        try {
            if (threadPoolExecutor != null) {
                List<Callable<T>> callables = new ArrayList<Callable<T>>(tasks);
                List<Future<T>> results = threadPoolExecutor.invokeAll(callables, timeout,
                        timeUnit);
                for (int i = 0; i < results.size(); i++) {
                    Instance instance = tasks.get(i).getInstance();
                    try {
                        Future<T> future = results.get(i);
                        statuses.add(processor.invoke(instance, future, reporter));
                    } catch (Exception e) {
                        log.warn("There was an error processing concurrent instance: "
                                        + instance, e);
                        statuses.add(new TaskStatusHandler(e));
                    }
                }
            } else {
                for (InstanceTask<T> task : tasks) {
                    T result = task.call();
                    statuses.add(processor.invoke(task.getInstance(), new SimpleFuture<T>(result),
                            reporter));
                }
            }

        } catch (Exception e) {
            // Should we do anything else here?
            log.warn("JMXFetch internal TaskProcessor error invoking concurrent tasks: ", e);
            throw e;
        }
        return statuses;
    }

    /**
     * Stops the excutor service.
     * */
    public void stop() {
        if (threadPoolExecutor != null) {
            threadPoolExecutor.shutdownNow();
        }
    }

    /**
     * used to wrap the result in embedded mode when not using executors.
     */
    private static class SimpleFuture<T> implements Future<T> {
        private final T result;

        public SimpleFuture(T result) {
            this.result = result;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() {
            return result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) {
            return result;
        }
    }
}
