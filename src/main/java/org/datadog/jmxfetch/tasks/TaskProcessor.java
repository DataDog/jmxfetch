package org.datadog.jmxfetch.tasks;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.InstanceTask;

import org.apache.log4j.Logger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public class TaskProcessor {
    private Reporter reporter;
    private Logger LOGGER;
    private ExecutorService threadPoolExecutor;

    public TaskProcessor(ExecutorService executor, Reporter reporter, Logger logger) {
        threadPoolExecutor = executor;
        this.reporter = reporter;
        this.LOGGER = logger;
    }

    public void setThreadPoolExecutor(ExecutorService executor) {
        threadPoolExecutor = executor;
    }

    public boolean ready() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor)threadPoolExecutor;
        return !(tpe.getPoolSize() == tpe.getActiveCount());
    }

    public <T> List<TaskStatusHandler> processTasks(List<InstanceTask<T>> tasks,
            int timeout, TimeUnit timeUnit, TaskMethod<T> processor) throws Exception {

        List<TaskStatusHandler> statuses = new ArrayList<TaskStatusHandler>();

        try {
            List<Callable<T>> callables = new ArrayList<Callable<T>>();
            for (InstanceTask<T> task : tasks) {
                callables.add(task);
            }
            List<Future<T>> results = threadPoolExecutor.invokeAll(callables, timeout, timeUnit);

            for (int i=0; i<results.size(); i++) {

                Instance instance = tasks.get(i).getInstance();
                try {
                    Future<T> future = results.get(i);

                    statuses.add(processor.invoke(instance, future, reporter));

                } catch (Exception e) {
                    LOGGER.warn("There was an error processing concurrent instance: " + instance, e);

                    statuses.add(new TaskStatusHandler(e));
                }
            }
        } catch(Exception e) {
            // Should we do anything else here?
            LOGGER.warn("JMXFetch internal TaskProcessor error invoking concurrent tasks: ", e);
            throw e;
        }

        return statuses;
    }

    public void stop() {
        threadPoolExecutor.shutdownNow();
    }

}
