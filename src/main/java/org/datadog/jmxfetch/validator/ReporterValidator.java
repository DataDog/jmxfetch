package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class ReporterValidator implements IParameterValidator {

    /** Validates a reporter configurations (console, statsd). */
    public void validate(String name, String value) throws ParameterException {
        if (!value.matches("^statsd:.+$") && !value.equals("console") && !value.equals("json")) {
            throw new ParameterException(
                    "Parameter "
                            + name
                            + " should be either 'console', 'json', 'statsd:[STATSD_HOST]:[STATSD_PORT]'"
                            + " or 'statsd:[STATSD_SOCKET_PATH]'");
        }
    }
}
