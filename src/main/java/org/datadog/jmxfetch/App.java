package org.datadog.jmxfetch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import javax.security.auth.login.FailedLoginException;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class App 
{
	private static ArrayList<Instance> _instances = new ArrayList<Instance>();
	private static LinkedList<Instance> _brokenInstances = new LinkedList<Instance>();
	private final static Logger LOGGER = Logger.getLogger(App.class.getName()); 
	private static int _loopCounter;
	private static Status status = null;
	
	/**
	 * Main entry of JMX Fetch
	 * Args are not parsed so far, hence order matters
	 * See AppConfig class for more details
	 */
	public static void main( String[] args ) {
		
		// Load the config from the args
		AppConfig config = AppConfig.getInstance(args);

		// Set up the logger to add file handler
		try {
			CustomLogger.setup(Level.toLevel(config.logLevel), config.logLocation);
		} catch (IOException e) {
			LOGGER.error("Unable to setup file handler to file: " + config.logLocation, e);
		}
		
		LOGGER.info("JMX Fetch has started");
		
		// Set up the metric reporter (Statsd Client)
    	MetricReporter metricReporter = new StatsdMetricReporter(config.statsdPort);

		// Initiate JMX Connections, get attributes that match the yaml configuration
		init(config.confdDirectory, config.yamlFileList, config.statusFileLocation);
		
		// Set up the shutdown hook to properly close resources
		attachShutdownHook();
				
		// Start the main loop
		_doLoop(config.loopPeriod, config.confdDirectory, metricReporter, config.yamlFileList, config.statusFileLocation);
		
	}
	
	/**
	 * Attach a Shutdown Hook that will be called when SIGTERM will be send to JMXFetch
	 */
	@SuppressWarnings("unchecked")
	private static void attachShutdownHook() {
		class ShutdownHook {
			public void attachShutDownHook(){
				Runtime.getRuntime().addShutdownHook(new Thread() {
					@Override
					public void run() {
						LOGGER.info("JMXFetch is closing");
						Status.getInstance().deleteStatus();
						
						// Properly close log handlers
						Enumeration<Appender> enume = LOGGER.getAllAppenders();
						while (enume.hasMoreElements()) {
							Appender h = enume.nextElement();
							h.close();
						}
					}
				});
			}
		}

		ShutdownHook shutdownHook = new ShutdownHook();
		shutdownHook.attachShutDownHook();

	}


	private static void _doLoop(int loopPeriod, String confdDirectory, MetricReporter metricReporter, String yamlFileList, String statusFileLocation) {
		// Main Loop that will periodically collect metrics from the JMX Server
		while(true) {
			long start = System.currentTimeMillis();
			if (_instances.size() > 0) {
				doIteration(metricReporter);
			} else {
				LOGGER.warn("No instance could be initiated. Retrying initialization.");
				status.flush();
				init(confdDirectory, yamlFileList, statusFileLocation, true);		
			}
			long length = System.currentTimeMillis() - start;
			LOGGER.debug("Iteration ran in " + length  + " ms");
			// Sleep until next collection
			try {
				LOGGER.debug("Sleeping for " + loopPeriod + " ms.");
				Thread.sleep(loopPeriod);
			} catch (InterruptedException e) {
				LOGGER.warn(e.getMessage(), e);
			}
		}

	}

	

	public static void doIteration(MetricReporter metricReporter) {	
		_loopCounter++;

		Iterator<Instance> it = _instances.iterator();
		while (it.hasNext() ){
			Instance instance = it.next();
			LinkedList<HashMap<String, Object>> metrics;
			try {
				metrics = instance.getMetrics();
			} catch (IOException e) {
				String warning = "Unable to refresh bean list for instance " + instance;
				LOGGER.warn(warning, e);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
				_brokenInstances.add(instance);
				continue;
			}

			if (metrics.size() == 0) {
				String warning = "Instance " + instance + " didn't return any metrics";
				LOGGER.warn(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
				_brokenInstances.add(instance);
				continue;
			}
						
			if ( instance.isLimitReached() ) {
				 // Max number of metrics reached so we truncate it and add a warning in the status so it appears in the agent info page.
				
				 LinkedList<HashMap<String, Object>> truncatedMetrics = new LinkedList<HashMap<String, Object>>(metrics.subList(0, instance.getMaxNumberOfMetrics()));
				 metricReporter.sendMetrics(truncatedMetrics, instance.getName());
				 String warning = "Number of returned metrics is too high for instance: " 
						 + instance.getName() 
						 + ". Please get in touch with Datadog Support for more details. Truncating to " + instance.getMaxNumberOfMetrics() + " metrics.";
				 status.addInstanceStats(instance.getName(), truncatedMetrics.size(), warning, Status.STATUS_WARNING);
				 
				 // We don't want to log the warning at every iteration so we use this custom logger.
				 CustomLogger.laconic(LOGGER, Level.WARN, warning, 0);
				 
			 } else {
				 // All is well
				 metricReporter.sendMetrics(metrics, instance.getName());
				 status.addInstanceStats(instance.getName(), metrics.size(), null, Status.STATUS_OK);
			 }

		}
		

		// Iterate over broken" instances to fix them by resetting them
		it = _brokenInstances.iterator();
		while(it.hasNext()) {
			Instance instance = it.next();
			
			// Clearing rates aggregator so we won't compute wrong rates if we can reconnect
			metricReporter.clearRatesAggregator(instance.getName());
			
			LOGGER.warn("Instance " + instance + " didn't return any metrics. Maybe the server got disconnected ? Trying to reconnect.");
			
			// Remove the broken instance from the good instance list so jmxfetch won't try to collect metrics from this broken instance during next collection
			_instances.remove(instance);
			
			// Resetting the instance
			Instance newInstance = new Instance(instance.getYaml(), instance.getInitConfig(), instance.getCheckName());
			try {
				// Try to reinit the connection and force to renew it
				LOGGER.info("Trying to reconnecting to: " + newInstance);
				newInstance.init(true);		
				// If we are here, the connection succeeded, the instance is fixed. It can be readded to the good instances list
				_instances.add(newInstance);
				it.remove();
			} catch (IOException e) {
				String warning = "Cannot connect to instance " + instance + ". Is a JMX Server running at this address?";
				LOGGER.warn(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			} catch (SecurityException e) {
				String warning = "Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials";
				LOGGER.warn(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			} catch (FailedLoginException e) {
				String warning = "Cannot connect to instance " + instance + " because of bad credentials. Please check your credentials";
				LOGGER.warn(warning);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			} catch (Exception e) {
				String warning = "Cannot connect to instance " + instance + " for an unknown reason." + e.getMessage();
				LOGGER.fatal(warning, e);
				status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
			}
		}
		
		try {
			status.flush();
		} catch (Exception e) {
			LOGGER.error("Unable to flush stats.", e);
		}
	}

	public static void init(String confdDirectory, String yamlFileList, String statusFileLocation) {
		init(confdDirectory, yamlFileList, statusFileLocation, false);
		
	}
	@SuppressWarnings("unchecked")
	public static void init(String confdDirectory, String yamlFileList, String statusFileLocation, boolean forceNewConnection) {
		
		// Set up the Status writer
		status = Status.getInstance();
		status.configure(statusFileLocation);
		
		// Reset the list of instances
		_brokenInstances = new LinkedList<Instance>();
		_instances = new ArrayList<Instance>();
		
		// Load JMX Yaml files				
		File conf_d_dir = new File(confdDirectory);
		final List<String> yamlList = Arrays.asList(yamlFileList.split(","));
		
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
				if (yamlList.contains(name)) {
					return true;
				} else {
					YamlParser config;
					File file = new File(dir.getAbsolutePath(), name);
					try {
						config = new YamlParser(file.getAbsolutePath());
						return config.isJmx();
					} catch (FileNotFoundException e) {
						LOGGER.warn("Cannot find " + file.getAbsolutePath());
						return false;
					} catch (Exception e) {
						LOGGER.warn("Cannot parse yaml file " + file.getAbsolutePath(), e);
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
				LOGGER.warn("Cannot find " + path);
				continue;
			} catch (Exception e) {
				LOGGER.warn("Cannot parse yaml file " + path, e);
				continue;
			}

			ArrayList<LinkedHashMap<String, Object>> configInstances = ((ArrayList<LinkedHashMap<String, Object>>) config.getYamlInstances());
			if ( configInstances == null || configInstances.size() == 0) {
				String warning = "No instance found in :" + path;
				LOGGER.warn(warning);
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
					String warning = "Unable to create instance. Please check your yaml file";
					status.addInstanceStats(name, 0, warning, Status.STATUS_ERROR);
					LOGGER.error(warning, e);
					continue;
				}
				try {
					//  initiate the JMX Connection
					instance.init(forceNewConnection);
					_instances.add(instance);
				} catch (IOException e) {
					_brokenInstances.add(instance);
					String warning = "Cannot connect to instance " + instance + " " + e.getMessage();
					status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
					LOGGER.error(warning);
				} catch (Exception e) {
					_brokenInstances.add(instance);
					String warning = "Unexpected exception while initiating instance "+ instance + " : " + e.getMessage(); 
					status.addInstanceStats(instance.getName(), 0, warning, Status.STATUS_ERROR);
					LOGGER.error(warning, e);
				}
			}
		}

	}
	
	public static int getLoopCounter() {
		return _loopCounter;
	}
	
	public static class AppConfig {
		private static volatile AppConfig _instance = null;
		
		public String confdDirectory = null;
		public int statsdPort = 0;
		public int loopPeriod = 0;
		public String logLocation = null;
		public String logLevel = null;
		public String statusFileLocation = null;
		public String yamlFileList = null;
		
		public static AppConfig getInstance(String[] args) {
			if (_instance == null) {
				_instance = new AppConfig(args);
			}
			return _instance;
		}
		
		private AppConfig(String[] args) {
			// We check the arguments passed are valid
			if (args.length != 7) {
				// TODO: Add a real args parser here
				LOGGER.fatal("All arguments are required!");
				System.exit(1);
			}
			try{
				confdDirectory = args[0];
				statsdPort = Integer.valueOf(args[1]);
				loopPeriod = Integer.valueOf(args[2]);
				logLocation = args[3];
				logLevel = args[4];
				yamlFileList = args[5];
				statusFileLocation = args[6];			
			} catch (Exception e) {
				LOGGER.fatal("Arguments are not valid.", e);
				System.exit(1);
			}
			
		}
	}
}
