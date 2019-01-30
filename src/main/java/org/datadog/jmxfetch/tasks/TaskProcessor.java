package org.datadog.jmxfetch.tasks;

import org.apache.log4j.Logger;

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

public class TaskProcessor {
    private final Logger logger;
    private Reporter reporter;
    private ExecutorService threadPoolExecutor;

    /**
     * TaskProcess constructor.
     * */
    public TaskProcessor(ExecutorService executor, Reporter reporter, Logger logger) {
        threadPoolExecutor = executor;
        this.reporter = reporter;
        this.logger = logger;
    }

    public void setThreadPoolExecutor(ExecutorService executor) {
        threadPoolExecutor = executor;
    }

    /**
     * Returns whether or not the executor service has threads available
     * for work on tasks.
     * */
    public boolean ready() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) threadPoolExecutor;
        return !(tpe.getPoolSize() == tpe.getActiveCount());
    }

    /**
     * Processes the list of InstanceTasks within a set timeout deadline.
     * */
    public <T> List<TaskStatusHandler> processTasks(
            List<InstanceTask<T>> tasks, int timeout, TimeUnit timeUnit, TaskMethod<T> processor)
            throws Exception {

        List<TaskStatusHandler> statuses = new ArrayList<TaskStatusHandler>();

        try {
            List<Callable<T>> callables = new ArrayList<Callable<T>>();
            for (InstanceTask<T> task : tasks) {
                callables.add(task);
            }
            List<Future<T>> results = threadPoolExecutor.invokeAll(callables, timeout, timeUnit);

            for (int i = 0; i < results.size(); i++) {

                Instance instance = tasks.get(i).getInstance();
                try {
                    Future<T> future = results.get(i);

                    statuses.add(processor.invoke(instance, future, reporter));

                } catch (Exception e) {
                    logger.warn(
                            "There was an error processing concurrent instance: " + instance, e);

                    statuses.add(new TaskStatusHandler(e));
                }
            }
        } catch (Exception e) {
            // Should we do anything else here?
            logger.warn("JMXFetch internal TaskProcessor error invoking concurrent tasks: ", e);
            throw e;
        }

        return statuses;
    }

    /**
     * Stops the excutor service.
     * */
    public void stop() {
        threadPoolExecutor.shutdownNow();
    }
}
