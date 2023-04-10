package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
import javax.management.MBeanServerDelegate;
import javax.management.relation.MBeanServerNotificationFilter;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

@Slf4j
public class Connection {
    private static final long CONNECTION_TIMEOUT = 10000;
    public static final String CLOSED_CLIENT_CAUSE = "The client has been closed";
    private JMXConnector connector;
    protected MBeanServerConnection mbs;
    protected Map<String, Object> env;
    protected JMXServiceURL address;

    private static class BeanNotificationListener implements NotificationListener {
        private BeanListener bl;

        public BeanNotificationListener(BeanListener bl) {
            this.bl = bl;
        }
        public void handleNotification(Notification notification, Object handback) {
            if (!(notification instanceof MBeanServerNotification)) {
                log.warn("Got unknown notification, expected MBeanServerNotification but got {}", notification.getClass());
            }
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            ObjectName mBeanName = mbs.getMBeanName();
            if (mbs.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
                bl.beanRegistered(mBeanName);
            } else if (mbs.getType().equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
                this.bl.beanUnregistered(mBeanName);
            }
        }
    }

    public void subscribeToBeanScopes(List<String> beanScopes, BeanListener bl) throws MalformedObjectNameException, IOException, InstanceNotFoundException{
        BeanNotificationListener listener = new BeanNotificationListener(bl);
        for (String scope : beanScopes) {
            ObjectName name = new ObjectName(scope);
            MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
            filter.enableObjectName(name);
        }
        mbs.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, null, null);
    }

    /** Gets attributes for matching bean name. */
    public MBeanAttributeInfo[] getAttributesForBean(ObjectName beanName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException,
                    IOException {
        return mbs.getMBeanInfo(beanName).getAttributes();
    }

    /** Gets class name for matching bean name. */
    public String getClassNameForBean(ObjectName beanName)
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
        connector = JMXConnectorFactory.connect(this.address, this.env);
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
}
