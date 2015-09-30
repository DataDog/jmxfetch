package org.datadog.jmxfetch;

import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.util.CustomLogger;
import org.junit.After;
import org.junit.BeforeClass;

import com.beust.jcommander.JCommander;

import org.apache.log4j.Level;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;


public class TestCommon {
	AppConfig appConfig = new AppConfig();
	App app;
	MBeanServer mbs;
	ArrayList<ObjectName> objectNames = new ArrayList<ObjectName>();

	/**
	 * Setup logger.
	 */
	@BeforeClass
    public static void init() throws Exception {
        CustomLogger.setup(Level.toLevel("ALL"), "/tmp/jmxfetch_test.log");
    }

	/**
	 * Register a MBean with the given name, and application attributes.
	 * @throws NotCompliantMBeanException
	 * @throws MBeanRegistrationException
	 * @throws InstanceAlreadyExistsException
	 * @throws MalformedObjectNameException
	 */
	protected void registerMBean(Object application, String objectStringName) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
		mbs = (mbs == null)? ManagementFactory.getPlatformMBeanServer() : mbs;
		ObjectName objectName = new ObjectName(objectStringName);
		objectNames.add(objectName);
		mbs.registerMBean(application, objectName);
	}

	/**
	 * Unregister MBeans.
	 * Note: executed after execution of every test.
	 * @throws InstanceNotFoundException
	 * @throws MBeanRegistrationException
	 */
	@After
	public void unregisterMBean() throws MBeanRegistrationException, InstanceNotFoundException{
		if (mbs != null) {
			for (ObjectName objectName: objectNames ) {
				mbs.unregisterMBean(objectName);
			}
		}
	}

	/**
	 * Init JMXFetch with the given YAML configuration file.
	 */
	protected void initApplication(String yamlFileName){
		// We do a first collection
		// We initialize the main app that will collect these metrics using JMX
		String confdDirectory = Thread.currentThread().getContextClassLoader().getResource(yamlFileName).getPath();
		confdDirectory = new String(confdDirectory.substring(0, confdDirectory.length() - yamlFileName.length()));
		String[] params = {"--reporter", "console", "-c", yamlFileName, "--conf_directory", confdDirectory, "collect"};
		new JCommander(appConfig, params);

		app = new App(appConfig);
		app.init(false);
	}

	/**
	 * Run a JMXFetch iteration.
	 */
	protected void run(){
		if (app != null) {
			app.doIteration();
		}
	}

	/**
	 * Return JMXFetch reporter.
	 */
	protected Reporter getReporter(){
		return appConfig.getReporter();
	}

	/**
	 * Return the metrics collected by JMXFetch.
	 */
	protected LinkedList<HashMap<String, Object>> getMetrics(){
		return ((ConsoleReporter) appConfig.getReporter()).getMetrics();
	}


	/**
	 * Return the service checks collected by JMXFetch.
	 */
	protected LinkedList<HashMap<String, Object>> getServiceChecks(){
		return ((ConsoleReporter) appConfig.getReporter()).getServiceChecks();
	}

}
