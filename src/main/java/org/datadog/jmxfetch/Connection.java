package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

@Slf4j
public abstract class Connection {
    private static final long CONNECTION_TIMEOUT = 10000;
    private JMXConnector connector;
    protected MBeanServerConnection mbs;
    protected Map<String, Object> env;
    protected JMXServiceURL address;

    /** Gets attributes for matching bean name. */
    public MBeanInfo getMBeanInfo(final ObjectName beanName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException,
                    IOException {
        return this.mbs.getMBeanInfo(beanName);
    }

    /** Queries beans on specific scope. Returns set of matching query names.. */
    public Set<ObjectName> queryNames(final ObjectName name) throws IOException {
        final String scope = (name != null) ? name.toString() : "*:*";
        log.debug("Querying bean names on scope: {}", scope);
        return this.mbs.queryNames(name, null);
    }

    protected void createConnection() throws IOException {
        this.env.put("attribute.remote.x.request.waiting.timeout", CONNECTION_TIMEOUT);
        
        this.closeConnector();
        log.info("Connecting to: " + this.address);
        try {
            this.connector = JMXConnectorFactory.connect(this.address, this.env);
        } catch (IOException e) {
            log.error("Error creating connector for address: " + this.address + " with error: " + e.getMessage());

            // Handle ConnectException specifically for socket cleanup
            if (e instanceof java.rmi.ConnectException) {
                log.warn("RMI ConnectException detected, performing socket cleanup");
                // Force garbage collection to help clean up any lingering socket references
                System.gc();
            }

            throw e;
        }
        try {
            log.info("Getting MBeanServerConnection for address: " + this.address);
            this.mbs = this.connector.getMBeanServerConnection();
        } catch (IOException e) {
            log.error("Error creating connection for address: " + this.address + " with error: " + e.getMessage());
            // close the connector if the connection fails
            this.closeConnector();
            throw e;
        }
    }

    /** Gets attribute for matching bean and attribute name. */
    public Object getAttribute(final ObjectName objectName, final String attributeName)
            throws AttributeNotFoundException, InstanceNotFoundException, MBeanException,
                    ReflectionException, IOException {
        final Object attr = this.mbs.getAttribute(objectName, attributeName);
        if (attr instanceof javax.management.Attribute) {
            return ((Attribute) attr).getValue();
        }
        return attr;
    }

    /** Closes the connector. */
    public void closeConnector() {
        if (this.connector != null) {
            log.info("Closing connector for address: " + this.address);
            try {
                this.connector.close();
            } catch (IOException e) {
                // ignore
            }
        } else {
            log.info("Connector is null for address: " + this.address);
        }
    }

    /** Returns a boolean describing if the connection is still alive. */
    public boolean isAlive() {
        if (this.connector == null) {
            return false;
        }
        try {
            this.connector.getConnectionId();
        } catch (IOException e) { // the connection is closed or broken
            return false;
        }
        return true;
    }
}
