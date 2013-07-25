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
	private Set<ObjectInstance> beans;
	public MBeanServerConnection mbs;
	public LinkedHashMap<String, Object> yaml;
	private LinkedList<Configuration> conf_list = new LinkedList<Configuration>();
	private LinkedList<JMXAttribute> matching_attr;
	public LinkedHashMap<String, Object> init_config;
	String instance_name;
	public String check_name;
	private LinkedList<JMXAttribute> failing_attributes;
	private Integer refresh_beans;
	private long last_refresh;
	private static final List<String> SIMPLE_TYPES = Arrays.asList("long", "java.lang.String", "int", "double"); 
	private static final List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData");
	
	
	public Instance(LinkedHashMap<String, Object> yaml_instance, LinkedHashMap<String, Object> init_config, String check_name) 
	{
		this.yaml = yaml_instance;
		this.init_config = init_config;
		this.mbs = null;
		this.instance_name = (String) yaml_instance.get("name");
		this.check_name = check_name;
		this.failing_attributes = new LinkedList<JMXAttribute>();
		this.refresh_beans = (Integer)this.yaml.get("refresh_beans");
		this.last_refresh = 0;
		
		// Generate an instance name that will be send as a tag with the metrics
		if (this.instance_name == null)
		{
			this.instance_name = check_name + "-" + this.yaml.get("host") + "-" + this.yaml.get("port");
		}
		
		Object yaml_conf = this.yaml.get("conf");
		if (yaml_conf == null)
		{
			yaml_conf = this.init_config.get("conf");
		}
		for ( LinkedHashMap<String, Object> conf : (ArrayList<LinkedHashMap<String, Object>>)(yaml_conf) ) 
		{
			conf_list.add(new Configuration(conf));
		}
		
		// We add a default configuration to fetch basic JVM metrics
		// The configuration files are embedded within the jar file
		try {
			conf_list.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(".settings/jmx-1.yaml").get_parsed_yaml()));
		} catch (FileNotFoundException e) {
			LOGGER.warning("Cannot load basic jvm metrics configuration file jmx-1.yaml");
		}
		try {
			conf_list.add(new Configuration((LinkedHashMap<String, Object>) new YamlParser(".settings/jmx-2.yaml").get_parsed_yaml()));
		} catch (FileNotFoundException e) {
			LOGGER.warning("Cannot load basic jvm metrics configuration file jmx-2.yaml");
		}
	}
	
	public void init() throws IOException, FailedLoginException, SecurityException
	{
		LOGGER.info("Trying to connect to JMX Server at " + this.toString());
		this.mbs = this.connect(this.yaml);
		LOGGER.info("Connected to JMX Server at " + this.toString());
		
		this.refresh_beans_list();
		this.get_matching_attributes();
	}
	
	@Override
	public String toString()
	{
		return this.yaml.get("host") + ":" + this.yaml.get("port");
	}
	
	public LinkedList<HashMap<String, Object>> get_metrics() throws IOException
	{
		
		// We can force to refresh the bean list every x seconds in case of ephemeral beans
		// To enable this, a "refresh_beans" parameter must be specified in the yaml config file
		if (this.refresh_beans != null && (System.currentTimeMillis() - this.last_refresh) / 1000 > this.refresh_beans)
		{
			LOGGER.info("Refreshing bean list");
			this.refresh_beans_list();
			this.get_matching_attributes();
		}
		
		LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
		Iterator<JMXAttribute> it = matching_attr.iterator();
		
		while(it.hasNext())
		{
			JMXAttribute jmx_attr = it.next();
			try {
				LinkedList<HashMap<String, Object>> attribute_metrics = jmx_attr.get_metrics();
				for (HashMap<String, Object> m : attribute_metrics)
				{
					m.put("check_name", this.check_name);
					metrics.add(m);
				}
				
				if(this.failing_attributes.contains(jmx_attr))
				{
					this.failing_attributes.remove(jmx_attr);
				}
			} catch (Exception e) {
				if (this.failing_attributes.contains(jmx_attr))
				{
					LOGGER.warning("Cannot generate metrics for attribute: " + jmx_attr + " twice in a row. Removing it from the attribute list");
					it.remove();
				}
				else
				{
					this.failing_attributes.add(jmx_attr);
				}
				continue;
			} 
		}
		return metrics;
	}

	private void get_matching_attributes() {
		this.matching_attr = new LinkedList<JMXAttribute>();
		for( ObjectInstance instance : get_beans() )
		{
			ObjectName bean_name = instance.getObjectName();	
			MBeanAttributeInfo[] atr;
			
			try {
				// Get all the attributes for bean_name
				atr = this.mbs.getMBeanInfo( bean_name ).getAttributes();
			} catch (Exception e) {
				LOGGER.warning("Cannot get bean attributes " + e.getMessage());
				continue;
			} 
		    
		    for ( MBeanAttributeInfo a : atr)
		    {
		    	JMXAttribute jmx_attribute;
	    		if( SIMPLE_TYPES.contains(a.getType()) )
	    		{
	    			jmx_attribute = new JMXSimpleAttribute(a, this.mbs, instance, this.instance_name);
	    		}
	    		else if (COMPOSED_TYPES.contains(a.getType()))
	    		{
	    			jmx_attribute = new JMXComplexAttribute(a, this.mbs, instance, this.instance_name);
	    		}
	    		else
	    		{
	    			LOGGER.fine("Attribute: " + a + " has an unsupported type: " + a.getType());
	    			continue;
	    		}
	    		
	    		
	    		// For each attribute we try it with each configuration to see if there is one that matches
	    		// If so, we store the attribute so metrics will be collected from it. Otherwise we discard it.
		    	for ( Configuration conf : this.conf_list)
		    	{	
		    		if ( jmx_attribute.match(conf) )
		    		{
		    			jmx_attribute.matching_conf = conf;
		    			this.matching_attr.add(jmx_attribute);
		    			break;
		    		}  		
		    	}
		    }
		}
		LOGGER.fine("Found " + matching_attr.size() + " matching attributes");
	}
		
	private MBeanServerConnection connect(LinkedHashMap<String, Object> connection_params) throws IOException
	{
	
		JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+connection_params.get("host")+":"+Integer.toString((Integer)(connection_params.get("port")))+"/jmxrmi");
		Map<String,String[]> env = new HashMap<String, String[]>();
		env.put( JMXConnector.CREDENTIALS, new String[]{(String)(connection_params.get("user")), (String)(connection_params.get("password"))} );
		JMXConnector connector = JMXConnectorFactory.connect(address,env);
		MBeanServerConnection mbs = connector.getMBeanServerConnection();
		return mbs;
		
	}
	
	public void refresh_beans_list() throws IOException {
		this.beans = this.mbs.queryMBeans(null, null);
		this.last_refresh = System.currentTimeMillis();
	}
	
	public Set<ObjectInstance> get_beans() {
		return this.beans;
	}

}
