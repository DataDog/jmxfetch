package org.datadog.jmxfetch;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=")
public class AppConfig {
	private static volatile AppConfig _instance = null;
  
    public static AppConfig getInstance() {
        if (_instance == null) {
            _instance = new AppConfig();
        }
        return _instance;
    }
    
    private AppConfig(){
    	// A private constructor to make sure that only one instance will be created
    }

	private static final List<String> LOG4J_LEVELS = Arrays.asList("ALL", "DEBUG", "ERROR", "FATAL", "INFO", "OFF", "TRACE", "LEVEL");
	
	@Parameter(names = {"--help", "-h"}, help = true, description = "Display this help page")
	public boolean help;


	@Parameter(names = {"--log_level", "-L"}, description = "Level of verbosity", validateWith = Log4JLevelValidator.class, required = false)
	public String logLevel = "INFO";
	
	@Parameter(names= {"--log_location", "-l"}, description = "Absolute path of the log file (default to null = no logging)", validateWith = LogWritableLocation.class, required = false)
	public String logLocation = null;
	
	@Parameter(names = {"--conf_directory", "-D"}, description = "Absolute path to the conf.d directory", required = true)
	public String confdDirectory = null;

	@Parameter(names = {"--reporter", "-r"}, description = "Reporter to use: should be either \"statsd:[STATSD_PORT]\" or \"console\"", 
			validateWith = MetricReporterConverter.class, converter = MetricReporterConverter.class, required = false)
	public MetricReporter metricReporter = new ConsoleReporter();

	@Parameter(names = {"--check", "-c"}, description = "Yaml file name to read (must be in the confd directory)", required = true, variableArity = true)
	public List<String> yamlFileList;

	@Parameter(names= {"--check_period", "-p"}, description = "Sleeping time during two iterations in ms", validateWith = PositiveInteger.class, required = false)
	public int loopPeriod = 15000;

	@Parameter(names= {"--status_location", "-s"}, description = "Absolute path of the status file. (default to null = no status file written)", validateWith = LogWritableLocation.class,
			converter = StatusWritableLocation.class, required = false)
	public Status status = null;
	
	@Parameter(description="Action to take, should be either \"collect\" or \"list\"", required = true)
	public List<String> action = new ArrayList<String>();
	
	
	public static class StatusWritableLocation implements IParameterValidator, IStringConverter<Status> {
		public void validate(String name, String value) {
			File f = new File(value);
			if (!f.canWrite()) {
				throw new ParameterException("File " + name +
						"is not writable");
			}

			Status.getInstance().configure(value);
		}


		public Status convert(String arg0) {
			return Status.getInstance();
		}
	}

	public class LogWritableLocation implements IParameterValidator {
		public void validate(String name, String value) {
			File f = new File(value);
			if (!f.canWrite()) {
				System.out.println("Warning - Log file: " + value + " is not writable.");
			}
		}
	}

	public static class MetricReporterConverter implements IParameterValidator, IStringConverter<MetricReporter> {
		private static MetricReporter _reporter;

		public void validate(String name, String value)
				throws ParameterException {

			if (!value.equals("console")) {

				if(!value.contains(":")) {
					throw new ParameterException("Parameter " + name +
							"should be either \"console\" or \"statsd:[STATSD_PORT\"");
				}

				String[] split = value.split(":");
				if (!split[0].equals("statsd")) {
					throw new ParameterException("Parameter " + name +
							"should be either \"console\" or \"statsd:[STATSD_PORT\"");
				}

				int port;
				try {
					port = Integer.parseInt(value);
				} catch(Exception e) {
					throw new ParameterException("Statsd port should be an integer");
				}

				if(port < 1) {
					throw new ParameterException("Statsd port should be > 0");
				}

				_reporter = new StatsdMetricReporter(port);

			} else {	
				//Reporter is console
				_reporter = new ConsoleReporter();
			}
		}

		public MetricReporter convert(String value) {
			// _reporter should have been set during validation
			return _reporter;
		}
	}

	public class Log4JLevelValidator implements IParameterValidator {
		public void validate(String name, String value)
				throws ParameterException {

			if (!LOG4J_LEVELS.contains(value)) {
				throw new ParameterException("Parameter " + name + 
						" should be in (\"ALL\", \"DEBUG\", \"ERROR\","
						+ " \"FATAL\", \"INFO\", \"OFF\", \"TRACE\", \"LEVEL\"");

			}

		}

	}

	public class PositiveInteger implements IParameterValidator {
		public void validate(String name, String value)
				throws ParameterException {
			int n = Integer.parseInt(value);
			if (n < 0) {
				throw new ParameterException("Parameter " + name + " should be positive (found " + value +")");
			}
		}
	}

}
