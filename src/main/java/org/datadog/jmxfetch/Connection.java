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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.relation.MBeanServerNotificationFilter;
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
    private JMXConnector connector;
    protected MBeanServerConnection mbs;
    protected Map<String, Object> env;
    protected JMXServiceURL address;
    private NotificationListener connectionNotificationListener;
    private boolean seenConnectionIssues;

    private static class ConnectionNotificationListener implements NotificationListener {
        public void handleNotification(Notification notification, Object handback) {
            if (!(notification instanceof JMXConnectionNotification)) {
                return;
            }
            if (!(handback instanceof Connection)) {
                return;
            }

            JMXConnectionNotification connNotif = (JMXConnectionNotification) notification;
            Connection conn = (Connection) handback;

            if (connNotif.getType() == JMXConnectionNotification.CLOSED
                    || connNotif.getType() == JMXConnectionNotification.FAILED
                    || connNotif.getType() == JMXConnectionNotification.NOTIFS_LOST) {
                log.warn("Connection is potentially in a bad state, marking connection issues. {} - {}", connNotif.getType(), connNotif.getMessage());
                conn.seenConnectionIssues = true;
            }
            log.debug("Received connection notification: {} Message: {}",
                 connNotif.getType(), connNotif.getMessage());
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
    public MBeanInfo getMBeanInfo(ObjectName beanName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException,
                    IOException {
        return mbs.getMBeanInfo(beanName);
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

        this.connectionNotificationListener = new ConnectionNotificationListener();
        connector.addConnectionNotificationListener(this.connectionNotificationListener, null, this);
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
                this.connector.removeConnectionNotificationListener(this.connectionNotificationListener);
                connector.close();
                connector = null;
            } catch (IOException | ListenerNotFoundException e) {
                // ignore
            }
        }
    }

    /** True if connection has been notified of failure/lost notifications */
    public boolean hasSeenConnectionIssues() {
        return this.seenConnectionIssues;
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
