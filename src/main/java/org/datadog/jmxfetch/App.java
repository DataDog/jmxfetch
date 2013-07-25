/*
 * App: Main class of the app
 * 
 * @author <a href="mailto:remi@datadoghq.com">Remi Hakim</a>
 * 
 */

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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.login.FailedLoginException;

public class App 
{
	public static ArrayList<Instance> _instances = new ArrayList<Instance>();

	private static LinkedList<Instance> _brokenInstances = new LinkedList<Instance>();

	private final static List<String> JMX_YAML_FILE = Arrays.asList("tomcat.yaml", "activemq.yaml", "jmx.yaml", "solr.yaml", "cassandra.yaml"); 

	private final static Logger LOGGER = Logger.getLogger(App.class.getName()); 

	private static MetricReporter _metricReporter;

	private static int _loopCounter;

	public static void main( String[] args ) {
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
		} catch (Exception e) {
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
		_metricReporter = new MetricReporter(statsd_port);

		// Initiate JMX Connections, get attributes that match the yaml configuration
		_init(confd_directory);

		// Start main loop
		_loopCounter = 0;
		_doLoop(loop_period, confd_directory);


	}

	private static void _doLoop(int loop_period, String confd_directory) {
		while(_instances.size() > 0) {
			// Main Loop that will regularly collect metrics from the JMX Server
			_doIteration();
			try {
				Thread.sleep(loop_period);
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, e.getMessage());
			}
		}

		while(_instances.size() == 0) {
			LOGGER.warning("No instance could be initiated. Will retry.");
			try {
				Thread.sleep(loop_period);
			} catch (InterruptedException e) {
				System.exit(1);
			}
			_init(confd_directory);
			_doLoop(loop_period, confd_directory);
		}
	}

	private static void _doIteration() {	
		_loopCounter++;

		if(_instances.size() == 0) {
			LOGGER.severe("No instance are initiated. Connector is exiting...");
			System.exit(1);
		}


		for (Instance instance : _instances) {
			LinkedList<HashMap<String, Object>> metrics;
			try {
				metrics = instance.getMetrics();
			} catch (IOException e) {
				LOGGER.warning("Unable to refresh bean list for instance " + instance);
				continue;
			}

			if (metrics.size() == 0) {
				_brokenInstances.add(instance);
				continue;
			}
			_metricReporter.sendMetrics(metrics, instance.getName());

		}

		Iterator<Instance> it = _brokenInstances.iterator();
		while(it.hasNext()) {
			Instance instance = it.next();
			LOGGER.warning("Instance " + instance + " didn't return any metrics. Maybe the server got disconnected ? Trying to reconnect.");
			_instances.remove(instance);
			Instance newInstance = new Instance(instance.getYaml(), instance.getInitConfig(), instance.getCheckName());
			try {
				newInstance.init();
				_instances.add(newInstance);
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

	private static void _init(String confdDirectory) {
		// Load JMX Yaml files				
		File conf_d_dir = new File(confdDirectory);
		File[] files = conf_d_dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return JMX_YAML_FILE.contains(name);
			}
		});


		// Iterate over all JMX Yaml files
		for (File file : files) {
			YamlParser config;
			try {
				config = new YamlParser(file.getAbsolutePath());
			} catch (FileNotFoundException e) {
				LOGGER.warning("Cannot find " + file.getAbsolutePath());
				continue;
			} catch (Exception e)
			{
				LOGGER.warning("Cannot parse yaml file " + file.getAbsolutePath());
				continue;
			}

			for(Iterator<LinkedHashMap<String,Object>> i = ((ArrayList<LinkedHashMap<String, Object>>) config.getYamlInstances()).iterator(); i.hasNext(); ) {	
				Instance instance = null;
				//Create a new Instance object
				try {
					String check_name = file.getName().replace(".yaml", "");
					instance = new Instance(i.next(),  ((LinkedHashMap<String, Object>) config.getInitConfig()), check_name);
				} catch(Exception e) {
					LOGGER.severe("Unable to create instance. Please check your yaml file");
					continue;
				}
				try {
					//  initiate the JMX Connection
					instance.init();
					_instances.add(instance);
				} catch (IOException e) {
					LOGGER.warning("Cannot connect to instance " + instance + ". Is a JMX Server running at this address?");
				} catch (SecurityException e) {
					LOGGER.warning("Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials");
				} catch (FailedLoginException e) {
					LOGGER.warning("Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials");
				} catch (Exception e) {
					LOGGER.warning("Unexpected exception while initiating instance "+ instance + " : " + e.getMessage());
				}
			}
		}

	}
	
	public static int getLoopCounter() {
		return _loopCounter;
	}
}
