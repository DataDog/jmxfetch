package org.datadog.jmxfetch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final static List<String> SIMPLE_TYPES = Arrays.asList("long", "java.lang.String", "int", "double"); 
	private final static List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData");
	
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

		// Generate an instance name that will be send as a tag with the metrics
		if (this._instanceName == null)	{
			this._instanceName = check_name + "-" + this._yaml.get("host") + "-" + this._yaml.get("port");
		}

		// In case the configuration to match beans is not specified in the "instance" parameter but in the init_config one
		Object yaml_conf = this._yaml.get("conf");
		if (yaml_conf == null) {
			yaml_conf = this._initConfig.get("conf");
		}
		
		for ( LinkedHashMap<String, Object> conf : (ArrayList<LinkedHashMap<String, Object>>)(yaml_conf) ) {
			_configurationList.add(new Configuration(conf));
		}

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
	public String toString()
	{
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
		for( ObjectInstance instance : this._beans) {
			ObjectName bean_name = instance.getObjectName();	
			MBeanAttributeInfo[] atr;

			try {
				// Get all the attributes for bean_name
				atr = this._mbs.getMBeanInfo( bean_name ).getAttributes();
			} catch (Exception e) {
				LOGGER.warning("Cannot get bean attributes " + e.getMessage());
				continue;
			} 

			for ( MBeanAttributeInfo a : atr) {
				JMXAttribute jmxAttribute;
				if( SIMPLE_TYPES.contains(a.getType()) ) {
					jmxAttribute = new JMXSimpleAttribute(a, this._mbs, instance, this._instanceName);
				} else if (COMPOSED_TYPES.contains(a.getType())) {
					jmxAttribute = new JMXComplexAttribute(a, this._mbs, instance, this._instanceName);
				} else {
					LOGGER.fine("Attribute: " + a + " has an unsupported type: " + a.getType());
					continue;
				}

				// For each attribute we try it with each configuration to see if there is one that matches
				// If so, we store the attribute so metrics will be collected from it. Otherwise we discard it.
				for ( Configuration conf : this._configurationList) {	
					if ( jmxAttribute.match(conf) ) {
						jmxAttribute.matching_conf = conf;
						this._matchingAttributes.add(jmxAttribute);
						break;
					}  		
				}
			}
		}
		LOGGER.fine("Found " + _matchingAttributes.size() + " matching attributes");
	}

	private MBeanServerConnection connect(LinkedHashMap<String, Object> connection_params) throws IOException {
		JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+connection_params.get("host")+":"+Integer.toString((Integer)(connection_params.get("port")))+"/jmxrmi");
		Map<String,String[]> env = new HashMap<String, String[]>();
		env.put( JMXConnector.CREDENTIALS, new String[]{(String)(connection_params.get("user")), (String)(connection_params.get("password"))} );
		JMXConnector connector = JMXConnectorFactory.connect(address,env);
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

}
