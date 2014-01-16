package org.datadog.jmxfetch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.security.auth.login.FailedLoginException;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

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
        AppConfig config = AppConfig.getInstance();
        JCommander jCommander = null;
        try{
        	jCommander = new JCommander(config, args);
        } catch(ParameterException e) {
        	System.out.println(e.getMessage());
        	System.exit(1);
        }
        if(config.help || config.getAction().equals(AppConfig.ACTION_HELP)) {
        	jCommander.usage();
        	System.exit(0);
        }
        
        // Set up the logger to add file handler
        try {
        	CustomLogger.setup(Level.toLevel(config.logLevel), config.logLocation);
        } catch (IOException e) {
            LOGGER.error("Unable to setup file handler to file: " + config.logLocation, e);
        }
        
        if (!AppConfig.ACTIONS.contains(config.getAction())) {
        	LOGGER.fatal(config.getAction() + " is not in " + AppConfig.ACTIONS + ". Exiting.");
        	System.exit(1);
        }
        
        if (!config.getAction().equals(AppConfig.ACTION_COLLECT) && !config.isConsoleReporter()) {
        	LOGGER.fatal(config.getAction() + " argument can only be used with the console reporter. Exiting.");
        	System.exit(1);
        }
        
        // Set up the shutdown hook to properly close resources
        attachShutdownHook();
    
        LOGGER.info("JMX Fetch has started");
           
        // Set up the metric reporter (Statsd Client)
        Reporter reporter = config.reporter;
        
        // Initiate JMX Connections, get attributes that match the yaml configuration
        init(config.confdDirectory, config.yamlFileList, false);

        if (config.getAction().equals(AppConfig.ACTION_COLLECT)) {
        	// Start the main loop
        	_doLoop(config.loopPeriod, config.confdDirectory, reporter, config.yamlFileList);
        }
        
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


    private static void _doLoop(int loopPeriod, String confdDirectory, Reporter reporter, List<String> yamlFileList) {
        // Main Loop that will periodically collect metrics from the JMX Server
        while(true) {
            long start = System.currentTimeMillis();
            if (_instances.size() > 0) {
                doIteration(reporter);
            } else {
                LOGGER.warn("No instance could be initiated. Retrying initialization.");
                status.flush();
                init(confdDirectory, yamlFileList, true);       
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

    

    public static void doIteration(Reporter metricReporter) { 
        _loopCounter++;

        Iterator<Instance> it = _instances.iterator();
        while (it.hasNext() ){
            Instance instance = it.next();
            LinkedList<HashMap<String, Object>> metrics;
            String instanceStatus = Status.STATUS_OK;
            String instanceMessage = null;
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
            } else if ( instance.isLimitReached()) { 
            	instanceMessage = "Number of returned metrics is too high for instance: " 
                        + instance.getName() 
                        + ". Please get in touch with Datadog Support for more details. Truncating to " + instance.getMaxNumberOfMetrics() + " metrics.";
            	
            	instanceStatus = Status.STATUS_WARNING;
            	// We don't want to log the warning at every iteration so we use this custom logger.
            	CustomLogger.laconic(LOGGER, Level.WARN, instanceMessage, 0);
            }
                metricReporter.sendMetrics(metrics, instance.getName());
                status.addInstanceStats(instance.getName(), metrics.size(), instanceMessage, instanceStatus);
            
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
   
    private static HashMap<String, YamlParser> _getConfigs(String confdDirectory, final List<String> yamlList) {
    	HashMap<String, YamlParser> configs = new HashMap<String, YamlParser>();
    	YamlParser fileConfig;
    	for (String fileName : yamlList) {
    		File f = new File(confdDirectory, fileName);
    		String name = f.getName().replace(".yaml", "");
    		try {
    			LOGGER.info("Reading " + f.getAbsolutePath());
				fileConfig = new YamlParser(f.getAbsolutePath());
				configs.put(name, fileConfig);
			} catch (FileNotFoundException e) {
				LOGGER.warn("Cannot find " + f.getAbsolutePath());
			} catch (Exception e) {
                LOGGER.warn("Cannot parse yaml file " + f.getAbsolutePath(), e);
			}
    	}
    	LOGGER.info("Found " + configs.size() + " config files");
    	return configs;
    }
      
    @SuppressWarnings("unchecked")
    public static void init(String confdDirectory, final List<String> yamlList, boolean forceNewConnection) {
        
        status = Status.getInstance();
 
        // Reset the list of instances
        _brokenInstances = new LinkedList<Instance>();
        _instances = new ArrayList<Instance>();
        
        HashMap<String, YamlParser> configs = _getConfigs(confdDirectory, yamlList);
        
        Iterator<Entry<String, YamlParser>> it = configs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, YamlParser> entry = (Map.Entry<String, YamlParser>)it.next();
            String name = entry.getKey();
            YamlParser config = entry.getValue();
            it.remove(); 
            
   
            ArrayList<LinkedHashMap<String, Object>> configInstances = ((ArrayList<LinkedHashMap<String, Object>>) config.getYamlInstances());
            if ( configInstances == null || configInstances.size() == 0) {
                String warning = "No instance found in :" + name;
                LOGGER.warn(warning);
                status.addInstanceStats(name, 0,  warning, Status.STATUS_ERROR);
                continue;
            }
            
            for(Iterator<LinkedHashMap<String,Object>> i = configInstances.iterator(); i.hasNext(); ) { 
                Instance instance = null;
                //Create a new Instance object
                try {
                    instance = new Instance(i.next(),  ((LinkedHashMap<String, Object>) config.getInitConfig()), name);
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
    
}
