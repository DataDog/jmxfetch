package org.datadog.jmxfetch;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class Connection {
    private static final String TRUST_STORE_PATH_KEY = "trust_store_path";
    private static final String TRUST_STORE_PASSWORD_KEY = "trust_store_password";
    static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
    private Integer port;
    private String host;
    private String user;
    private String password;
    private String trustStorePath;
    private String trustStorePassword;
    private MBeanServerConnection mbs;
	private String processRegex;
    private static final Long CONNECTION_TIMEOUT = new Long(10000);
    private final static Logger LOGGER = Logger.getLogger(Connection.class.getName());
    private static final ThreadFactory daemonThreadFactory = new DaemonThreadFactory();


    public Connection(LinkedHashMap<String, Object> connectionParams) throws IOException {
        this.host = (String) connectionParams.get("host");
        this.port = (Integer) connectionParams.get("port"); 
        this.user = (String) connectionParams.get("user");
        this.password = (String) connectionParams.get("password");
        this.processRegex = (String) connectionParams.get("process_name_regex");

        if(connectionParams.containsKey(TRUST_STORE_PATH_KEY) && connectionParams.containsKey(TRUST_STORE_PASSWORD_KEY)) {
            this.trustStorePath = (String) connectionParams.get(TRUST_STORE_PATH_KEY);
            this.trustStorePassword = (String) connectionParams.get(TRUST_STORE_PASSWORD_KEY);
        } else {
            this.trustStorePath = null;
            this.trustStorePassword = null;
        }

        this.mbs = this.createConnection();
    }

    public MBeanAttributeInfo[] getAttributesForBean(ObjectName bean_name) throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        return this.mbs.getMBeanInfo( bean_name ).getAttributes();
    }

    public Set<ObjectInstance> queryMBeans() throws IOException {
        return this.mbs.queryMBeans(null, null);
    }
    
    private MBeanServerConnection createConnection() throws IOException {
    	JMXServiceURL address;
    	
    	if (processRegex != null) {
    		try {
				address = new JMXServiceURL(getJMXUrlForProcessRegex(processRegex));
			} catch (AttachNotSupportedException e) {
				throw new IOException("Unnable to attach to process regex:  "+ processRegex, e);
			}
    	} else {
    		address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + this.host + ":" + this.port +"/jmxrmi");	
    	}
    	
        Map<String,Object> env = new HashMap<String, Object>();
        env.put( JMXConnector.CREDENTIALS, new String[]{this.user,this.password} );
        env.put( "jmx.remote.x.request.waiting.timeout", CONNECTION_TIMEOUT);

        if(this.trustStorePath != null && this.trustStorePassword != null) {
            System.setProperty("javax.net.ssl.trustStore", this.trustStorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", this.trustStorePassword);
            LOGGER.info("Setting trustStore path: " + this.trustStorePath + " and trustStorePassword");
        }

        JMXConnector connector = connectWithTimeout(address, env, 20, TimeUnit.SECONDS);
        MBeanServerConnection mbs = connector.getMBeanServerConnection();
        return mbs;
    }

    private String getJMXUrlForProcessRegex(String processRegex) throws AttachNotSupportedException, IOException {
    	String jmxURL = "";
		for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
			if (vmd.displayName().matches(processRegex)) {
				VirtualMachine vm = VirtualMachine.attach(vmd);
				String connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
				//If jmx agent is not running in VM, load it and return the connector url
				if (connectorAddress == null) {
					loadJMXAgent(vm);
					
					// agent is started, get the connector address
					connectorAddress = vm.getAgentProperties().getProperty(
							CONNECTOR_ADDRESS);
				}
				
				return connectorAddress;
			}
		}
		return jmxURL;
	}

	private void loadJMXAgent(VirtualMachine vm) throws IOException {
		String agent = vm.getSystemProperties().getProperty(
				"java.home")
				+ File.separator
				+ "lib"
				+ File.separator
				+ "management-agent.jar";
		try {
			vm.loadAgent(agent);
		} catch (Exception e) {
			 LOGGER.warn("Error initializing JMX agent on host: "+this.host, e);
		}
	}

	public Object getAttribute(ObjectName objectName, String attributeName) throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return this.mbs.getAttribute(objectName, attributeName);
    }

    /**
     * Connect to a MBean Server with a timeout
     * This code comes from this blog post:
     * https://weblogs.java.net/blog/emcmanus/archive/2007/05/making_a_jmx_co.html
     */
    public static JMXConnector connectWithTimeout(
            final JMXServiceURL url, final Map<String, Object> env, long timeout, TimeUnit unit)
                    throws IOException {

        final BlockingQueue<Object> mailbox = new ArrayBlockingQueue<Object>(1);

        ExecutorService executor =  Executors.newSingleThreadExecutor(daemonThreadFactory);
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
            result = mailbox.poll(timeout, unit);
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

    private static <T extends Throwable> T initCause(T wrapper, Throwable wrapped) {
        wrapper.initCause(wrapped);
        return wrapper;
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    }
}

