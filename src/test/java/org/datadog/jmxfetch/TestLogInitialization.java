package org.datadog.jmxfetch;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.sun.jmx.remote.util.ClassLogger;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogManager;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestLogInitialization {
    public static final Callable<AppConfig> LOCAL_CONFIG = new Callable<AppConfig>() {
        public AppConfig call() throws Exception {
            return AppConfig.create(
                    ImmutableList.of("org/datadog/jmxfetch/dd-java-agent-jmx.yaml"),
                    Collections.<String>emptyList(),
                    (int) TimeUnit.SECONDS.toMillis(30),
                    (int) TimeUnit.SECONDS.toMillis(30),
                    Collections.<String, String>emptyMap(),
                    "console",
                    "System.err",
                    "DEBUG");
        }
    };

    public static final Callable<AppConfig> REMOTE_CONFIG = new Callable<AppConfig>() {
        public AppConfig call() throws Exception {
            return AppConfig.create(
                    ImmutableList.of("org/datadog/jmxfetch/remote-jmx.yaml"),
                    Collections.<String>emptyList(),
                    (int) TimeUnit.SECONDS.toMillis(30),
                    (int) TimeUnit.SECONDS.toMillis(30),
                    Collections.<String, String>emptyMap(),
                    "console",
                    "System.err",
                    "DEBUG");
        }
    };

    @After
    public void unregisterLockingMBean() {
        try {
        ManagementFactory.getPlatformMBeanServer()
                .unregisterMBean(new ObjectName("org.datadog.jmxfetch.log_init_test:type=TriggeringMBean"));

        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    public void testLocalUsageDoesNotInitalizeLogManager() throws Exception {
        CountDownLatch latch = registerLockingMBean();

        TrackingClassLoader classLoader = new TrackingClassLoader();

        final AtomicReference<Exception> errored = runInThread(classLoader, "LOCAL_CONFIG");

        latch.await(15, TimeUnit.SECONDS);

        assertNull(errored.get());
        assertTrue(classLoader.classLoaded(LocalConnection.class.getName()));
        assertFalse(classLoader.classLoaded(JMXServiceURL.class.getName()));
    }

    @Test
    public void testRemoteUsageDoesInitalizeLogManager() throws Exception {
        registerLockingMBean();

        TrackingClassLoader classLoader = new TrackingClassLoader();

        runInThread(classLoader, "REMOTE_CONFIG");

        // We don't know how long until the error triggers.  No good way to verify.
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));

        assertTrue(classLoader.classLoaded(RemoteConnection.class.getName()));
        assertTrue(classLoader.classLoaded(JMXServiceURL.class.getName()));
    }

    private AtomicReference<Exception> runInThread(final TrackingClassLoader classLoader,
                                                   String configName) throws Exception {
        final AtomicReference<Exception> errored = new AtomicReference<Exception>();

        Class<?> appClass = classLoader.loadClass(App.class.getName());
        Class<?> appConfigClass = classLoader.loadClass(AppConfig.class.getName());
        assertTrue(classLoader.classLoaded(App.class.getName()));
        assertTrue(classLoader.classLoaded(AppConfig.class.getName()));
        final Method runMethod = appClass.getMethod("run", appConfigClass);

        Class<?> thisClass = classLoader.loadClass(getClass().getName());
        Field appConfigField = thisClass.getField(configName);
        final Callable config = (Callable)appConfigField.get(null);

        Thread task = new Thread(new Runnable() {
            public void run() {
                // This will run forever, so we need to run in a different thread.
                try {
                    // App.run(appConfig);
                    runMethod.invoke(null, config.call());
                } catch (Exception e) {
                    errored.set(e);
                    e.printStackTrace();
                }
            }
        });
        task.start();

        return errored;
    }

    private CountDownLatch registerLockingMBean() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        TriggeringMBean bean = new Triggering(latch);
        ManagementFactory.getPlatformMBeanServer()
                .registerMBean(bean, new ObjectName("org.datadog.jmxfetch.log_init_test:type=TriggeringMBean"));
        return latch;
    }

    public interface TriggeringMBean {
        boolean isTriggered();
    }

    class Triggering implements TriggeringMBean {
        private final CountDownLatch latch;

        Triggering(CountDownLatch latch) {
            this.latch = latch;
        }

        public boolean isTriggered() {
            System.out.println("Triggering!");
            latch.countDown();
            return true;
        }
    }

    static class TrackingClassLoader extends URLClassLoader {
        private final Set<String> loadedClasses = Sets.newConcurrentHashSet();

        TrackingClassLoader() throws MalformedURLException {
            // Don't delegate to the parent as that already has the classes loaded.
            super(getClasspathUrls(), null);
        }

        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            loadedClasses.add(name);
            if(name.startsWith("org.apache.log4j")) {
                return getSystemClassLoader().loadClass(name);
            }
            return super.loadClass(name, resolve);
        }

        boolean classLoaded(String name) {
            return loadedClasses.contains(name);
        }

        private static URL[] getClasspathUrls() throws MalformedURLException {

            ImmutableList.Builder<URL> urls = ImmutableList.builder();
            for (String entry : Splitter.on(PATH_SEPARATOR.value()).split(JAVA_CLASS_PATH.value())) {
                try {
                    urls.add(new File(entry).toURI().toURL());
                } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
                    urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
                }

            }
            return urls.build().toArray(new URL[0]);
        }
    }
}
