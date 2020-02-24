package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

@Slf4j
public class Connection {
    private static final long CONNECTION_TIMEOUT = 10000;
    private static final long JMX_TIMEOUT = 20;
    public static final String CLOSED_CLIENT_CAUSE = "The client has been closed";
    private static final ThreadFactory daemonThreadFactory = new DaemonThreadFactory();
    private JMXConnector connector;
    protected MBeanServerConnection mbs;
    protected HashMap<String, Object> env;
    protected JMXServiceURL address;

    private static <T extends Throwable> T initCause(T wrapper, Throwable wrapped) {
        wrapper.initCause(wrapped);
        return wrapper;
    }

    /** Gets attributes for matching bean name. */
    public MBeanAttributeInfo[] getAttributesForBean(ObjectName beanName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException,
                    IOException {
        return mbs.getMBeanInfo(beanName).getAttributes();
    }

    /** Gets class for matching bean name. */
    public String getClassForBean(ObjectName beanName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException,
            IOException {
        return mbs.getMBeanInfo(beanName).getClassName();
    }

    /** Queries beans on specific scope. Returns set of matching query names.. */
    public Set<ObjectName> queryNames(ObjectName name) throws IOException {
        String scope = (name != null) ? name.toString() : "*:*";
        log.debug("Querying bean names on scope: " + scope);
        return mbs.queryNames(name, null);
    }

    protected void createConnection() throws IOException {
        this.env.put("attribute.remote.x.request.waiting.timeout", CONNECTION_TIMEOUT);
        closeConnector();
        log.info("Connecting to: " + this.address);
        connector = connectWithTimeout(this.address, this.env);
        mbs = connector.getMBeanServerConnection();
    }

    /** Gets attribute for matching bean and attribute name. */
    public Object getAttribute(ObjectName objectName, String attributeName)
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {
        Object attr = mbs.getAttribute(objectName, attributeName);
        if (attr instanceof javax.management.Attribute) {
            return ((Attribute) attr).getValue();
        }
        return attr;
    }

    /**
     * Connect to a MBean Server with a timeout This code comes from this blog post:
     * https://weblogs.java.net/blog/emcmanus/archive/2007/05/making_a_jmx_co.html.
     */
    JMXConnector connectWithTimeout(final JMXServiceURL url, final Map<String, Object> env)
            throws IOException {

        final BlockingQueue<Object> mailbox = new ArrayBlockingQueue<Object>(1);

        ExecutorService executor = Executors.newSingleThreadExecutor(daemonThreadFactory);
        executor.submit(
                new Runnable() {
                    public void run() {
                        try {
                            JMXConnector connector = JMXConnectorFactory.connect(url, env);
                            if (!mailbox.offer(connector)) {
                                connector.close();
                            }
                        } catch (Throwable t) {
                            mailbox.offer(t);
                        }
                    }
                });
        Object result;
        try {
            result = mailbox.poll(JMX_TIMEOUT, TimeUnit.SECONDS);
            if (result == null) {
                if (!mailbox.offer("")) {
                    result = mailbox.take();
                }
            }
        } catch (InterruptedException e) {
            throw initCause(new InterruptedIOException(e.getMessage()), e);
        } finally {
            executor.shutdown();
        }
        if (result == null) {
            log.warn("Connection timed out: " + url);
            throw new SocketTimeoutException("Connection timed out: " + url);
        }
        if (result instanceof JMXConnector) {
            return (JMXConnector) result;
        }
        try {
            throw (Throwable) result;
        } catch (Throwable e) {
            throw new IOException(e.toString(), e);
        }
    }

    /** Closes the connector. */
    public void closeConnector() {
        if (connector != null) {
            try {
                connector.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /** Returns a boolean describing if the connection is still alive. */
    public boolean isAlive() {
        if (connector == null) {
            return false;
        }
        try {
            connector.getConnectionId();
        } catch (IOException e) { // the connection is closed or broken
            return false;
        }
        return true;
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable run) {
            Thread thread = Executors.defaultThreadFactory().newThread(run);
            thread.setDaemon(true);
            return thread;
        }
    }
}
