package org.datadog.jmxfetch;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.login.FailedLoginException;

public class Instance {
	private final static Logger LOGGER = Logger.getLogger(Instance.class.getName());
	private final static List<String> SIMPLE_TYPES = Arrays.asList("long", "java.lang.String", "int", "double", "java.lang.Double", "java.lang.Integer", "java.lang.Long"); 
	private final static List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData", "java.util.HashMap");
	private final static int MAX_RETURNED_METRICS = 100;

	private Set<ObjectInstance> _beans;
	private LinkedList<Configuration> _configurationList = new LinkedList<Configuration>();
	private LinkedList<JMXAttribute> _matchingAttributes;
	private LinkedList<JMXAttribute> _failingAttributes;
	private Integer _refreshBeansPeriod;
	private long _lastRefreshTime;  
	private MBeanServerConnection _mbs;
	private LinkedHashMap<String, Object> _yaml;
	private LinkedHashMap<String, Object> _initConfig;
	private String _instanceName;
	private String _checkName;
	private int _maxReturnedMetrics;
	private boolean _limitReached;
	private static final ThreadFactory daemonThreadFactory = new DaemonThreadFactory();



	public Instance(LinkedHashMap<String, Object> yaml_instance, LinkedHashMap<String, Object> init_config, String check_name) 
	{
		this._mbs = null;
		this._yaml = yaml_instance;
		this._initConfig = init_config;
		this._instanceName = (String) yaml_instance.get("name");
		this._checkName = check_name;
		this._failingAttributes = new LinkedList<JMXAttribute>();
		this._refreshBeansPeriod = (Integer)this._yaml.get("refresh_beans");
		this._lastRefreshTime = 0;
		this._limitReached = false;
		Object maxReturnedMetrics = this._yaml.get("max_returned_metrics");
		if (maxReturnedMetrics == null) {
			_maxReturnedMetrics = MAX_RETURNED_METRICS;
		} else {
			_maxReturnedMetrics = (Integer) maxReturnedMetrics;
		}

		// Generate an instance name that will be send as a tag with the metrics
		if (this._instanceName == null) {
			this._instanceName = check_name + "-" + this._yaml.get("host") + "-" + this._yaml.get("port");
		}

		// In case the configuration to match beans is not specified in the "instance" parameter but in the init_config one
		Object yaml_conf = this._yaml.get("conf");
		if (yaml_conf == null && this._initConfig != null) {
			yaml_conf = this._initConfig.get("conf");
		}

		for ( LinkedHashMap<String, Object> conf : (ArrayList<LinkedHashMap<String, Object>>)(yaml_conf) ) {
			_configurationList.add(new Configuration(conf));
		}

		// Add the configuration to get the default basic metrics from the JVM
		_configurationList.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(this.getClass().getResourceAsStream("/jmx-1.yaml")).getParsedYaml()));
		_configurationList.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(this.getClass().getResourceAsStream("/jmx-2.yaml")).getParsedYaml()));
	}

	public void init() throws IOException, FailedLoginException, SecurityException {
		LOGGER.info("Trying to connect to JMX Server at " + this.toString());
		this._mbs = this.connect(this._yaml);
		LOGGER.info("Connected to JMX Server at " + this.toString());

		this._refreshBeansList();
		this._getMatchingAttributes();
	}

	@Override
	public String toString() {
		return this._yaml.get("host") + ":" + this._yaml.get("port");
	}

	public LinkedList<HashMap<String, Object>> getMetrics() throws IOException {

		// We can force to refresh the bean list every x seconds in case of ephemeral beans
		// To enable this, a "refresh_beans" parameter must be specified in the yaml config file
		if (this._refreshBeansPeriod != null && (System.currentTimeMillis() - this._lastRefreshTime) / 1000 > this._refreshBeansPeriod) {
			LOGGER.info("Refreshing bean list");
			this._refreshBeansList();
			this._getMatchingAttributes();
		}

		LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
		Iterator<JMXAttribute> it = _matchingAttributes.iterator();

		while(it.hasNext()) {
			JMXAttribute jmxAttr = it.next();
			try {
				LinkedList<HashMap<String, Object>> attribute_metrics = jmxAttr.getMetrics();
				for (HashMap<String, Object> m : attribute_metrics) {
					m.put("check_name", this._checkName);
					metrics.add(m);
				}

				if(this._failingAttributes.contains(jmxAttr)) {
					this._failingAttributes.remove(jmxAttr);
				}
			} catch (Exception e) {
				if (this._failingAttributes.contains(jmxAttr)) {
					LOGGER.warning("Cannot generate metrics for attribute: " + jmxAttr + " twice in a row. Removing it from the attribute list");
					it.remove();
				} else {
					this._failingAttributes.add(jmxAttr);
				}
				continue;
			}
		}
		return metrics;
	}

	private void _getMatchingAttributes() {
		this._matchingAttributes = new LinkedList<JMXAttribute>();
		for( ObjectInstance bean : this._beans) {
			ObjectName bean_name = bean.getObjectName();    
			MBeanAttributeInfo[] atr;

			try {
				// Get all the attributes for bean_name
				LOGGER.fine("Getting attributes for bean: " + bean_name);
				atr = this._mbs.getMBeanInfo( bean_name ).getAttributes();
			} catch (Exception e) {
				LOGGER.warning("Cannot get bean attributes " + e.getMessage());
				continue;
			} 
			
			for ( MBeanAttributeInfo a : atr) {
				if (  _matchingAttributes.size() >= this._maxReturnedMetrics ) {
					this._limitReached = true;
					LOGGER.warning("Maximum number of metrics reached");
					break;
				}
				JMXAttribute jmxAttribute;
				String attributeType = a.getType();
				if( SIMPLE_TYPES.contains(attributeType) ) {
					LOGGER.fine("Attribute: " + bean_name + " : " + a + " has a simple type");
					jmxAttribute = new JMXSimpleAttribute(a, this._mbs, bean, this._instanceName);
				} else if (COMPOSED_TYPES.contains(attributeType)) {
					LOGGER.fine("Attribute: " + bean_name + " : " + a + " has a complex type");
					jmxAttribute = new JMXComplexAttribute(a, this._mbs, bean, this._instanceName);
				} else {
					LOGGER.fine("Attribute: " + bean_name + " : " + a + " has an unsupported type: " + attributeType);
					continue;
				}

				// For each attribute we try it with each configuration to see if there is one that matches
				// If so, we store the attribute so metrics will be collected from it. Otherwise we discard it.
				for ( Configuration conf : this._configurationList) {
					try {
						if ( jmxAttribute.match(conf) ) {
							jmxAttribute.matching_conf = conf;
							this._matchingAttributes.add(jmxAttribute);
							break;
						}       
					} catch (Exception e) {
						LOGGER.log(Level.SEVERE, "Error while trying to match a configuration with the Attribute: " + bean_name + " : " + a, e);
					}
				}		
			}
		}
		LOGGER.info("Found " + _matchingAttributes.size() + " matching attributes");
	}

	private MBeanServerConnection connect(LinkedHashMap<String, Object> connection_params) throws IOException {
		JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+connection_params.get("host")+":"+Integer.toString((Integer)(connection_params.get("port")))+"/jmxrmi");
		Map<String,Object> env = new HashMap<String, Object>();
		env.put( JMXConnector.CREDENTIALS, new String[]{(String)(connection_params.get("user")), (String)(connection_params.get("password"))} );
		env.put( "jmx.remote.x.request.waiting.timeout", new Long(10000));

		if(connection_params.containsKey("trust_store_path") && connection_params.containsKey("trust_store_password")) {
			System.setProperty("javax.net.ssl.trustStore", (String)(connection_params.get("trust_store_path")));
			System.setProperty("javax.net.ssl.trustStorePassword", (String)(connection_params.get("trust_store_password")));
		}

		JMXConnector connector = connectWithTimeout(address, env, 20, TimeUnit.SECONDS);
		MBeanServerConnection mbs = connector.getMBeanServerConnection();
		return mbs;
	}

	private void _refreshBeansList() throws IOException {
		this._beans = this._mbs.queryMBeans(null, null);
		this._lastRefreshTime = System.currentTimeMillis();
	}

	public String getName() {
		return this._instanceName;
	}

	public LinkedHashMap<String, Object> getYaml() {
		return this._yaml;
	}

	public LinkedHashMap<String, Object> getInitConfig() {
		return this._initConfig;
	}

	public String getCheckName() {
		return this._checkName;
	}

	public int getMaxNumberOfMetrics() {
		return this._maxReturnedMetrics;
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
			LOGGER.warning("Connection timed out: " + url);
			throw new SocketTimeoutException("Connect timed out: " + url);
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

	public boolean isLimitReached() {
		return this._limitReached;
	}

}
