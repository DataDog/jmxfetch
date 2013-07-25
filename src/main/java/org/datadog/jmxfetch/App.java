package org.datadog.jmxfetch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.security.auth.login.FailedLoginException;

public class App 
{
	public static ArrayList<Instance> _instances = new ArrayList<Instance>();
	
	private static LinkedList<Instance> _broken_instances = new LinkedList<Instance>();

	private static List<String> JMX_YAML_FILE = Arrays.asList("tomcat.yaml", "activemq.yaml", "jmx.yaml", "solr.yaml", "cassandra.yaml"); 

	private final static Logger LOGGER = Logger.getLogger(App.class.getName()); 

	private static MetricReporter METRIC_REPORTER;
	
	private static int loop_counter;
	
	
	
	public static void main( String[] args )
	{
		String confd_directory = null;
		int statsd_port = 0;
		int loop_period = 0;
		String log_location = null;
		String log_level = null;
		
		
		// We check the arguments passed are valid
		if (args.length != 5) {
			System.out.println("All arguments are required!");
			System.exit(1);
		}

		try{
			confd_directory = args[0];
			statsd_port = Integer.valueOf(args[1]);
			loop_period = Integer.valueOf(args[2]);
			log_location = args[3];
			log_level = args[4];
		}
		catch (Exception e)
		{
			System.out.println("Arguments are not valid.");
			System.exit(1);
		}
		
		try {
			CustomLogger.setup(Level.parse(log_level), log_location);
		} catch (IOException e) {
			System.out.println("Unable to setup logging");
			e.printStackTrace();
		}
		

		// Set up the metric reporter (Statsd Client)
		METRIC_REPORTER = new MetricReporter(statsd_port);
		
		// Initiate JMX Connections, get attributes that match the yaml configuration
		init(confd_directory);
		
		// Start main loop
		loop_counter = 0;
		do_loop(loop_period, confd_directory);
		
		
	}
	
	private static void do_loop(int loop_period, String confd_directory)
	{
		while(_instances.size() > 0)
		{
			// Main Loop that will regularly collect metrics from the JMX Server
			do_iteration();
			try {
				Thread.sleep(loop_period);
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, e.getMessage());
			}
		}
		
		while(_instances.size() == 0)
		{
			LOGGER.warning("No instance could be initiated. Will retry.");
			try {
				Thread.sleep(loop_period);
			} catch (InterruptedException e) {
				System.exit(1);
			}
			init(confd_directory);
			do_loop(loop_period, confd_directory);
		}
	}
	
	private static void do_iteration()
	{	
			loop_counter++;
			
			if(_instances.size() == 0)
			{
				LOGGER.severe("No instance are initiated. Connector is exiting...");
				System.exit(1);
			}
				
			
			for (Instance instance : _instances)
			{
				LinkedList<HashMap<String, Object>> metrics;
				try {
					metrics = instance.get_metrics();
				} catch (IOException e) {
					LOGGER.warning("Unable to refresh bean list for instance " + instance);
					continue;
				}

				if (metrics.size() == 0)
				{
					_broken_instances.add(instance);
					continue;
				}
				METRIC_REPORTER.sendMetrics(metrics, instance.instance_name, loop_counter);
					
			}
			
			Iterator<Instance> it = _broken_instances.iterator();
			while(it.hasNext())
			{
				Instance instance = it.next();
				LOGGER.warning("Instance " + instance + " didn't return any metrics. Maybe the server got disconnected ? Trying to reconnect.");
				_instances.remove(instance);
				Instance new_instance = new Instance(instance.yaml, instance.init_config, instance.check_name);
				try {
					new_instance.init();
					_instances.add(new_instance);
					it.remove();
				} catch (IOException e) {
					LOGGER.warning("Cannot connect to instance " + instance + ". Is a JMX Server running at this address?");
				} catch (SecurityException e) {
					LOGGER.warning("Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials");
				} catch (FailedLoginException e) {
					LOGGER.warning("Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials");
				}
			}
		
	}

	private static void init(String confd_directory) {
		// Load JMX Yaml files				
		File conf_d_dir = new File(confd_directory);
		File[] files = conf_d_dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return JMX_YAML_FILE.contains(name);
			}
		});
		

		// Iterate over all JMX Yaml files
		for (File file : files)
		{
			YamlParser config;
			try {
				config = new YamlParser(file.getAbsolutePath());
			} catch (FileNotFoundException e1) {
				LOGGER.log(Level.SEVERE, "Cannot find " + file.getAbsolutePath());
				continue;
			}

			for(Iterator<LinkedHashMap<String,Object>> i = ((ArrayList<LinkedHashMap<String, Object>>) config.get_instances()).iterator(); i.hasNext(); )
			{	
				Instance instance = null;
				//Create a new Instance object
				try
				{
					instance = new Instance(i.next(),  ((LinkedHashMap<String, Object>) config.get_init_config()), file.getName().replace(".yaml", ""));
				} catch(Exception e) {
					LOGGER.severe("Unable to create instance. Please check your yaml file");
					continue;
				}
				try
				{
					//  initiate the JMX Connection
					instance.init();
					_instances.add(instance);
				} catch (IOException e) {
					LOGGER.warning("Cannot connect to instance " + instance + ". Is a JMX Server running at this address?");
				} catch (SecurityException e) {
					LOGGER.warning("Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials");
				} catch (FailedLoginException e) {
					LOGGER.warning("Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials");
				}
			}
		}

	}

}
