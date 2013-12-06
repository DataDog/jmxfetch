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
	private static ArrayList<Instance> _instances = new ArrayList<Instance>();
	private static LinkedList<Instance> _brokenInstances = new LinkedList<Instance>();
	private final static Logger LOGGER = Logger.getLogger(App.class.getName()); 
	private static int _loopCounter;
	private static Status status = null;
	
	public static void main( String[] args ) {
		
		/**
		 * ShutdownHook that will be called when SIGTERM will be send to JMXFetch
		 *
		 */
		class ShutdownHook {
			 public void attachShutDownHook(){
			  Runtime.getRuntime().addShutdownHook(new Thread() {
			   @Override
			   public void run() {
				   LOGGER.info("JMXFetch is closing");
				   Status.getInstance().deleteStatus();
			   }
			  });
			 }
		}
		
		// We check the arguments passed are valid
		if (args.length != 7) {
			System.out.println("All arguments are required!");
			System.exit(1);
		}

		String confd_directory = null;
		int statsd_port = 0;
		int loop_period = 0;
		String log_location = null;
		String log_level = null;
		String status_file_location = null;
		String yaml_file_list = null;

		try{
			confd_directory = args[0];
			statsd_port = Integer.valueOf(args[1]);
			loop_period = Integer.valueOf(args[2]);
			log_location = args[3];
			log_level = args[4];
			yaml_file_list = args[5];
			status_file_location = args[6];			
		} catch (Exception e) {
			System.out.println("Arguments are not valid.");
			System.exit(1);
		}

		// We set up the logger
		try {
			CustomLogger.setup(Level.parse(log_level), log_location);
		} catch (IOException e) {
			System.out.println("Unable to setup logging");
			e.printStackTrace();
		}
		
		// Set up the metric reporter (Statsd Client)
    	MetricReporter metric_reporter = new StatsdMetricReporter(statsd_port);

		// Initiate JMX Connections, get attributes that match the yaml configuration
		init(confd_directory, yaml_file_list, status_file_location);
		
		// Add a shutdown hook to delete status file when jmxfetch closes
		ShutdownHook shutdownHook = new ShutdownHook();
		shutdownHook.attachShutDownHook();
		
		
		// Start the main loop
		_doLoop(loop_period, confd_directory, metric_reporter, yaml_file_list, status_file_location);
		
	}


	private static void _doLoop(int loop_period, String confd_directory, MetricReporter metric_reporter, String yaml_file_list, String status_file_location) {
		// Main Loop that will periodically collect metrics from the JMX Server
		while(true) {
			if (_instances.size() > 0) {
				doIteration(metric_reporter);
			} else {
				LOGGER.warning("No instance could be initiated. Retrying initialization.");
				status.flush();
				init(confd_directory, yaml_file_list, status_file_location);		
			}
			
			// Sleep until next collection
			try {
				Thread.sleep(loop_period);
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, e.getMessage());
			}
		}

	}

	public static void doIteration(MetricReporter metric_reporter) {	
		_loopCounter++;

		for (Instance instance : _instances) {
			LinkedList<HashMap<String, Object>> metrics;
			try {
				metrics = instance.getMetrics();
			} catch (IOException e) {
				String warning = "Unable to refresh bean list for instance " + instance;
				LOGGER.warning(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
				continue;
			}

			if (metrics.size() == 0) {
				_brokenInstances.add(instance);
				String warning = "Instance " + instance + " didn't return any metrics";
				LOGGER.warning(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
				continue;
			}
						
			if ( instance.isLimitReached() ) {
				 LinkedList<HashMap<String, Object>> truncatedMetrics = new LinkedList<HashMap<String, Object>>(metrics.subList(0, instance.getMaxNumberOfMetrics()));
				 metric_reporter.sendMetrics(truncatedMetrics, instance.getName());
				 String warning = "Number of returned metrics is too high for instance: " 
						 + instance.getName() 
						 + ". Please get in touch with Datadog Support for more details. Truncating to " + instance.getMaxNumberOfMetrics() + " metrics.";
				 CustomLogger.laconic(LOGGER, Level.WARNING, warning, 0);
				 status.addInstanceStats(instance.getName(), truncatedMetrics.size(), warning, Status.STATUS_WARNING);
			 } else {
				 metric_reporter.sendMetrics(metrics, instance.getName());
				 status.addInstanceStats(instance.getName(), metrics.size(), null, Status.STATUS_OK);
			 }

		}
		

		// We iterate over "broken" instances to "fix" them by resetting them
		Iterator<Instance> it = _brokenInstances.iterator();
		while(it.hasNext()) {
			Instance instance = it.next();
			
			// Clearing rates aggregator so we won't compute wrong rates if we can reconnect
			metric_reporter.clearRatesAggregator(instance.getName());
			
			LOGGER.warning("Instance " + instance + " didn't return any metrics. Maybe the server got disconnected ? Trying to reconnect.");
			
			// Remove the broken instance from the good instance list so jmxfetch won't try to collect metrics from this broken instance during next collection
			_instances.remove(instance);
			
			// Resetting the instance
			Instance newInstance = new Instance(instance.getYaml(), instance.getInitConfig(), instance.getCheckName());
			try {
				newInstance.init();
				
				// If we are here, the connection succeeded, the instance is "fixed". We can readd it to the "good" instances list
				_instances.add(newInstance);
				it.remove();
			} catch (IOException e) {
				String warning = "Cannot connect to instance " + instance + ". Is a JMX Server running at this address?";
				LOGGER.warning(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			} catch (SecurityException e) {
				String warning = "Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials";
				LOGGER.warning(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			} catch (FailedLoginException e) {
				String warning = "Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials";
				LOGGER.warning(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			} catch (Exception e) {
				String warning = "Cannot connect to instance " + instance + " for an unknown reason." + e.getMessage();
				LOGGER.log(Level.SEVERE, warning, e);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			}
		}
		
		status.flush();

	}

	public static void init(String confdDirectory, String yaml_file_list, String status_file_location) {
		
		// Set up the Status writer
		status = Status.getInstance();
		status.configure(status_file_location);
		
		// Reset the list of instances
		_brokenInstances = new LinkedList<Instance>();
		_instances = new ArrayList<Instance>();
		
		// Load JMX Yaml files				
		File conf_d_dir = new File(confdDirectory);
		final List<String> yaml_list = Arrays.asList(yaml_file_list.split(","));
		
		// Filter the files in the directory to only get valid yaml files
		File[] files = conf_d_dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				String extension = null;
				int i = name.lastIndexOf('.');
				if (i > 0) {
				    extension = name.substring(i+1);
				}
				if (extension == null) {
					return false;
				}
				if (!extension.equals("yaml")) {
					return false;
				}
				if (yaml_list.contains(name)) {
					return true;
				} else {
					YamlParser config;
					File file = new File(dir.getAbsolutePath(), name);
					try {
						config = new YamlParser(file.getAbsolutePath());
						return config.isJmx();
					} catch (FileNotFoundException e) {
						LOGGER.warning("Cannot find " + file.getAbsolutePath());
						return false;
					} catch (Exception e) {
						LOGGER.warning("Cannot parse yaml file " + file.getAbsolutePath());
						LOGGER.warning(e.getMessage());
						return false;
					}				
				}
			}
		});

		LOGGER.info("Found " + files.length + " yaml files.");

		// Iterate over all JMX Yaml files
		for (File file : files) {
			YamlParser config;
			String path = file.getAbsolutePath();
			String name = file.getName().replace(".yaml", "");
			try {
				LOGGER.info("Reading " + path);
				config = new YamlParser(path);
			} catch (FileNotFoundException e) {
				LOGGER.warning("Cannot find " + path);
				continue;
			} catch (Exception e) {
				LOGGER.warning("Cannot parse yaml file " + path);
				continue;
			}

			ArrayList<LinkedHashMap<String, Object>> configInstances = ((ArrayList<LinkedHashMap<String, Object>>) config.getYamlInstances());
			if ( configInstances == null || configInstances.size() == 0) {
				String warning = "No instance found in :" + path;
				LOGGER.warning(warning);
				status.addInstanceStats(name, 0,  warning, Status.STATUS_ERROR);
				continue;
			}
			for(Iterator<LinkedHashMap<String,Object>> i = configInstances.iterator(); i.hasNext(); ) {	
				Instance instance = null;
				//Create a new Instance object
				try {
					String check_name = file.getName().replace(".yaml", "");
					instance = new Instance(i.next(),  ((LinkedHashMap<String, Object>) config.getInitConfig()), check_name);
				} catch(Exception e) {
					e.printStackTrace();
					String warning = "Unable to create instance. Please check your yaml file";
					status.addInstanceStats(name, 0, warning, Status.STATUS_ERROR);
					LOGGER.severe(warning);
					continue;
				}
				try {
					//  initiate the JMX Connection
					instance.init();
					_instances.add(instance);
				} catch (IOException e) {
					_brokenInstances.add(instance);
					String warning = "Cannot connect to instance " + instance + " " + e.getMessage();
					status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
					LOGGER.severe(warning);
				} catch (Exception e) {
					_brokenInstances.add(instance);
					String warning = "Unexpected exception while initiating instance "+ instance + " : " + e.getMessage(); 
					status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
					LOGGER.log(Level.SEVERE, warning, e);
				}
			}
		}

	}
	
	public static int getLoopCounter() {
		return _loopCounter;
	}
}
