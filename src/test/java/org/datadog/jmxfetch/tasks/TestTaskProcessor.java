package org.datadog.jmxfetch.tasks;

import  org.mockito.Spy;
import static org.mockito.Mockito.*;

import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.reporter.Reporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.ArrayList;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;

import org.junit.Test;
import org.junit.BeforeClass;

public class TestTaskProcessor {
    private static List<Instance> instances = null;

	private final static Logger LOGGER = Logger.getLogger("Test Task Processor");

    class TestSimpleTask implements Callable {
        Instance instance;

         TestSimpleTask(Instance instance) {
            this.instance = instance;
        }

        @Override
        public Boolean call() throws Exception {
            TimeUnit.SECONDS.sleep(3);
            return new Boolean(instance.timeToCollect());
        }
    }

    static TaskStatusHandler processTestResults(
            Instance instance, Future<Boolean> future, Reporter reporter){

        TaskStatusHandler status = new TaskStatusHandler();
        Throwable exc = null;

        try {
            if (future.isDone()) {

                status.setData(future.get());
            } else if (future.isCancelled()) {
                // Build custom exception
                exc = new TaskProcessException("unable to complete task successfully: " + instance);
            }
        } catch (Exception e){
            exc = e;
        } 

        if (exc != null) {
            status.setThrowableStatus(exc);
        }

        return status;
    }

    @BeforeClass
    public static void beforeTestRun() {
        instances = new ArrayList<Instance>();
        instances.add(mock(Instance.class));
        instances.add(mock(Instance.class));
        instances.add(mock(Instance.class));

        for (Instance instance: instances) {
            when(instance.timeToCollect()).thenReturn(true);
        }

    }

    /**
     * Test Task Processor 
     */
    @Test
    public void testTaskProcessor() throws Throwable {
    
        ExecutorService testThreadPool = Executors.newFixedThreadPool(2);
        TaskProcessor testProcessor = new TaskProcessor(testThreadPool, null, LOGGER);

        List<Pair<Instance, Callable<Boolean>>> instanceTestTasks = new ArrayList<Pair<Instance, Callable<Boolean>>>();

        for (Instance instance: instances) {
            Callable<Boolean> callable = new TestSimpleTask(instance);
            Pair<Instance, Callable<Boolean>> task = Pair.of(instance, callable);
            instanceTestTasks.add(task);
        }

        // 10 second timeout, 2 runners in thread, plenty of time.
        List<TaskStatusHandler> statuses = testProcessor.processTasks(
                instanceTestTasks, 10, TimeUnit.SECONDS,
                new TaskMethod<Boolean>() {
                    @Override
                    public TaskStatusHandler invoke(Instance instance, Future<Boolean> future, Reporter reporter) {
                        return TestTaskProcessor.processTestResults(instance, future, reporter);
                    };
                });
 
        // this should all be green
        for (int i=0 ; i<statuses.size(); i++) {

            TaskStatusHandler status = statuses.get(i);

            status.raiseForStatus();

            // It should be true - both instances ready to collect
            assertTrue((Boolean)status.getData());
        }
    }

    /**
     * Test Timeout in Task Processor 
     */
    @Test
    public void testTaskProcessorTimeout() throws Throwable {
    
        ExecutorService testThreadPool = Executors.newFixedThreadPool(2);
        TaskProcessor testProcessor = new TaskProcessor(testThreadPool, null, LOGGER);

        List<Pair<Instance, Callable<Boolean>>> instanceTestTasks = new ArrayList<Pair<Instance, Callable<Boolean>>>();

        for (Instance instance: instances) {
            Callable<Boolean> callable = new TestSimpleTask(instance);
            Pair<Instance, Callable<Boolean>> task = Pair.of(instance, callable);
            instanceTestTasks.add(task);
        }

        // 10 second timeout, 2 runners in thread, not enough time for all tasks - last one should fail.
        List<TaskStatusHandler> statuses = testProcessor.processTasks(
                instanceTestTasks, 4, TimeUnit.SECONDS,
                new TaskMethod<Boolean>() {
                    @Override
                    public TaskStatusHandler invoke(Instance instance, Future<Boolean> future, Reporter reporter) {
                        return TestTaskProcessor.processTestResults(instance, future, reporter);
                    };
                });
 
        // this should all be green
        for (int i=0 ; i<statuses.size(); i++) {

            TaskStatusHandler status = statuses.get(i);

            try {
                // there should be no exceptions, so don't wrap in try...catch block
                status.raiseForStatus();

                // It should be true - both instances ready to collect
                assertTrue((Boolean)status.getData());
            } catch (CancellationException ce){
                // only third instance should timeout given test structure.
                assertEquals(i, 2);
            }
        }
    }
}
