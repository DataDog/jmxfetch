package org.datadog.jmxfetch;
import java.io.File;
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
    private static final List<String> LOG4J_LEVELS = Arrays.asList("ALL", "DEBUG", "ERROR", "FATAL", "INFO", "OFF", "TRACE", "LEVEL");

    public static final String ACTION_COLLECT = "collect";
    public static final String ACTION_LIST_EVERYTHING = "list_everything";
    public static final String ACTION_LIST_COLLECTED = "list_collected_attributes";
    public static final String ACTION_LIST_MATCHING = "list_matching_attributes";
    public static final String ACTION_LIST_NOT_MATCHING = "list_not_matching_attributes";
    public static final String ACTION_LIST_LIMITED = "list_limited_attributes";
    public static final String ACTION_HELP = "help";
    public static final List<String> ACTIONS = Arrays.asList(ACTION_COLLECT, ACTION_LIST_EVERYTHING, ACTION_LIST_COLLECTED, 
            ACTION_LIST_MATCHING, ACTION_LIST_NOT_MATCHING, ACTION_LIST_LIMITED, ACTION_HELP);

    private boolean _isConsoleReporter = false;


    @Parameter(names = {"--help", "-h"}, help = true, description = "Display this help page")
    public boolean help;


    @Parameter(names = {"--log_level", "-L"}, description = "Level of verbosity", validateWith = Log4JLevelValidator.class, required = false)
    public String logLevel = "INFO";

    @Parameter(names= {"--log_location", "-l"}, description = "Absolute path of the log file (default to null = no logging)", validateWith = LogWritableLocation.class, required = false)
    public String logLocation = null;

    @Parameter(names = {"--conf_directory", "-D"}, description = "Absolute path to the conf.d directory", required = true)
    public String confdDirectory = null;

    @Parameter(names = {"--reporter", "-r"}, description = "Reporter to use: should be either \"statsd:[STATSD_PORT]\" or \"console\"", 
            validateWith = ReporterConverter.class, converter = ReporterConverter.class, required = false)
    public Reporter reporter = new ConsoleReporter();

    @Parameter(names = {"--check", "-c"}, description = "Yaml file name to read (must be in the confd directory)", required = true, variableArity = true)
    public List<String> yamlFileList;

    @Parameter(names= {"--check_period", "-p"}, description = "Sleeping time during two iterations in ms", validateWith = PositiveInteger.class, required = false)
    public int loopPeriod = 15000;

    @Parameter(names= {"--status_location", "-s"}, description = "Absolute path of the status file. (default to null = no status file written)", validateWith = StatusWritableLocation.class,
            converter = StatusWritableLocation.class, required = false)
    public Status status = Status.getInstance();

    @Parameter(description="Action to take, should be in [help, collect, list_everything, list_collected_attributes, list_matching_attributes, list_not_matching_attributes, list_limited_attributes]", required = true)
    private List<String> action = null;

    public static AppConfig getInstance() {
        if (_instance == null) {
            _instance = new AppConfig();
        }
        return _instance;
    }

    private AppConfig(){
        // A private constructor to make sure that only one instance will be created
    }

    public String getAction() {
        return this.action.get(0);
    }

    public boolean isConsoleReporter() {
        return _isConsoleReporter;
    }

    public void setIsConsoleReporter(boolean b) {
        _isConsoleReporter = b;
    }

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

    public static class LogWritableLocation implements IParameterValidator {
        public void validate(String name, String value) {
            File f = new File(value);
            if (!f.canWrite()) {
                System.out.println("Warning - Log file: " + value + " is not writable.");
            }
        }
    }

    public static class ReporterConverter implements IParameterValidator, IStringConverter<Reporter> {
        private static Reporter _reporter;

        public void validate(String name, String value)
                throws ParameterException {

            if(value.equals("console")){    
                //Reporter is console
                _reporter = new ConsoleReporter();
                AppConfig.getInstance().setIsConsoleReporter(true);
            } else if (!value.equals("console")) {

                if(!value.contains(":")) {
                    throw new ParameterException("Parameter " + name +
                            " should be either \"console\" or \"statsd:[STATSD_PORT\"");
                }

                String[] split = value.split(":");
                if (!split[0].equals("statsd")) {
                    throw new ParameterException("Parameter " + name +
                            "should be either \"console\" or \"statsd:[STATSD_PORT\"");
                }

                int port;
                try {
                    port = Integer.parseInt(split[1]);
                } catch(Exception e) {
                    throw new ParameterException("Statsd port should be an integer");
                }

                if(port < 1) {
                    throw new ParameterException("Statsd port should be > 0");
                }

                _reporter = new StatsdReporter(port); 
            } else {
                throw new ParameterException(name + " should be either \"console\" or \"statsd:[STATSD_PORT]\"");
            }
        }

        public Reporter convert(String value) {
            // _reporter should have been set during validation
            return _reporter;
        }
    }

    public static class Log4JLevelValidator implements IParameterValidator {
        public void validate(String name, String value)
                throws ParameterException {

            if (!LOG4J_LEVELS.contains(value)) {
                throw new ParameterException("Parameter " + name + 
                        " should be in (\"ALL\", \"DEBUG\", \"ERROR\","
                        + " \"FATAL\", \"INFO\", \"OFF\", \"TRACE\", \"LEVEL\"");

            }

        }

    }

    public static class PositiveInteger implements IParameterValidator {
        public void validate(String name, String value)
                throws ParameterException {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new ParameterException("Parameter " + name + " should be positive (found " + value +")");
            }
        }
    }

}
