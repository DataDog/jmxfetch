package org.datadog.jmxfetch;

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

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.log4j.Logger;

public class Connection {
    private static final long CONNECTION_TIMEOUT = 10000;
    private static final long JMX_TIMEOUT = 20;
    private final static Logger LOGGER = Logger.getLogger(Connection.class.getName());
    private static final ThreadFactory daemonThreadFactory = new DaemonThreadFactory();
    private JMXConnector connector;
    private MBeanServerConnection mbs;
    protected HashMap<String, Object> env;
    protected JMXServiceURL address;

    private static <T extends Throwable> T initCause(T wrapper, Throwable wrapped) {
        wrapper.initCause(wrapped);
        return wrapper;
    }

    public MBeanAttributeInfo[] getAttributesForBean(ObjectName bean_name)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        return mbs.getMBeanInfo(bean_name).getAttributes();
    }

    public Set<ObjectName> queryNames(ObjectName name) throws IOException {
        String scope = (name != null) ? name.toString() : "*:*";
        LOGGER.debug("Querying bean names on scope: " + scope);
        return mbs.queryNames(name, null);
    }

    protected void createConnection() throws IOException {
        this.env.put("attribute.remote.x.request.waiting.timeout", CONNECTION_TIMEOUT);
        closeConnector();
        LOGGER.info("Connecting to: " + this.address);
        connector = connectWithTimeout(this.address, this.env);
        mbs = connector.getMBeanServerConnection();
    }

    public Object getAttribute(ObjectName objectName, String attributeName) throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return mbs.getAttribute(objectName, attributeName);
    }

    /**
     * Connect to a MBean Server with a timeout
     * This code comes from this blog post:
     * https://weblogs.java.net/blog/emcmanus/archive/2007/05/making_a_jmx_co.html
     */
    JMXConnector connectWithTimeout(final JMXServiceURL url, final Map<String, Object> env) throws IOException {

        final BlockingQueue<Object> mailbox = new ArrayBlockingQueue<Object>(1);

        ExecutorService executor = Executors.newSingleThreadExecutor(daemonThreadFactory);
        executor.submit(new Runnable() {
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
                if (!mailbox.offer(""))
                    result = mailbox.take();
            }
        } catch (InterruptedException e) {
            throw initCause(new InterruptedIOException(e.getMessage()), e);
        } finally {
            executor.shutdown();
        }
        if (result == null) {
            LOGGER.warn("Connection timed out: " + url);
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

    public void closeConnector() {
        if (connector != null) {
            try {
                connector.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

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
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    }
}

