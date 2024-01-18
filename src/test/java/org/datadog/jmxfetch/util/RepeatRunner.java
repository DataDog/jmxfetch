package org.datadog.jmxfetch.util;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import org.datadog.jmxfetch.util.RepeatTest;

public class RepeatRunner extends BlockJUnit4ClassRunner {

    public RepeatRunner(Class<?> klass) throws Throwable {
        super(klass);
    }

    @Override
    protected Statement methodBlock(FrameworkMethod method) {
        RepeatTest repeat = method.getAnnotation(RepeatTest.class);
        final int times = repeat != null ? repeat.value() : 1;

        final Statement statement = super.methodBlock(method);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                for (int i = 0; i < times; i++) {
                    try {
                        statement.evaluate();
                    } catch (Throwable t) {
                        System.err.println("Test failed on iteration: " + (i + 1));
                        throw t; // Rethrow to allow JUnit to handle the failure
                    }
                }
            }
        };
    }
}
