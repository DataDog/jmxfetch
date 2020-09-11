package org.datadog.jmxfetch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;
import org.junit.After;
import org.junit.Test;

public class TestLogInitialization {
    public static final String JAVA_CLASS_PATH = System.getProperty("java.class.path");
    public static final String PATH_SEPARATOR = File.pathSeparator;

    public static final Callable<AppConfig> LOCAL_CONFIG =
            new Callable<AppConfig>() {
                public AppConfig call() throws Exception {
                    return AppConfig.create(
                            Collections.unmodifiableList(Arrays.asList("org/datadog/jmxfetch/dd-java-agent-jmx.yaml")),
                            Collections.<String>emptyList(),
                            Collections.<String>emptyList(),
                            (int) TimeUnit.SECONDS.toMillis(30),
                            (int) TimeUnit.SECONDS.toMillis(30),
                            Collections.<String, String>emptyMap(),
                            "console",
                            "System.err",
                            "DEBUG");
                }
            };

    public static final Callable<AppConfig> REMOTE_CONFIG =
            new Callable<AppConfig>() {
                public AppConfig call() throws Exception {
                    return AppConfig.create(
                            Collections.unmodifiableList(Arrays.asList("org/datadog/jmxfetch/remote-jmx.yaml")),
                            Collections.<String>emptyList(),
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
                    .unregisterMBean(
                            new ObjectName(
                                    "org.datadog.jmxfetch.log_init_test:type=TriggeringMBean"));

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

    private AtomicReference<Exception> runInThread(
            final TrackingClassLoader classLoader, String configName) throws Exception {
        final AtomicReference<Exception> errored = new AtomicReference<Exception>();

        Class<?> appClass = classLoader.loadClass(App.class.getName());
        Class<?> appConfigClass = classLoader.loadClass(AppConfig.class.getName());
        assertTrue(classLoader.classLoaded(App.class.getName()));
        assertTrue(classLoader.classLoaded(AppConfig.class.getName()));
        final Method runMethod = appClass.getMethod("run", appConfigClass);

        Class<?> thisClass = classLoader.loadClass(getClass().getName());
        Field appConfigField = thisClass.getField(configName);
        final Callable config = (Callable) appConfigField.get(null);

        Thread task =
                new Thread(
                        new Runnable() {
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
                .registerMBean(
                        bean,
                        new ObjectName("org.datadog.jmxfetch.log_init_test:type=TriggeringMBean"));
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
        private final Set<String> loadedClasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        TrackingClassLoader() throws MalformedURLException {
            // Don't delegate to the parent as that already has the classes loaded.
            super(getClasspathUrls(), null);
        }

        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            loadedClasses.add(name);
            if (name.startsWith("org.apache.log4j")) {
                return getSystemClassLoader().loadClass(name);
            }
            return super.loadClass(name, resolve);
        }

        boolean classLoaded(String name) {
            return loadedClasses.contains(name);
        }

        private static URL[] getClasspathUrls() throws MalformedURLException {

            List<URL> urls = new ArrayList<URL>();

            for (String entry :
                    JAVA_CLASS_PATH.split(PATH_SEPARATOR)) {
                try {
                    urls.add(new File(entry).toURI().toURL());
                } catch (
                        SecurityException
                                e) { // File.toURI checks to see if the file is a directory
                    urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
                }
            }

            return Collections.unmodifiableList(urls).toArray(new URL[0]);
        }
    }
}
