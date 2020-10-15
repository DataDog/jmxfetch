package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class ReporterValidator implements IParameterValidator {

    private static final String STATSD_PREFIX = "statsd:";
    private final PositiveIntegerValidator positiveIntegerValidator =
            new PositiveIntegerValidator();

    /** Validates a reporter configurations (console, statsd). */
    public void validate(String name, String value) throws ParameterException {
        if (value.startsWith(STATSD_PREFIX) && value.length() > STATSD_PREFIX.length()) {
            return;
        }

        if (!value.equals("console") && !value.equals("json")) {
            throw new ParameterException(
                    "Parameter "
                            + name
                            + " should be either 'console', 'json', 'statsd:[STATSD_PORT]' "
                            + "or 'statsd:[STATSD_HOST]:[STATSD_PORT]'");
        }
    }
}
