package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class ReporterValidator implements IParameterValidator {

    private static final String STATSD_PREFIX = "statsd:";
    private final PositiveIntegerValidator positiveIntegerValidator = new PositiveIntegerValidator();

    public void validate(String name, String value) throws ParameterException {
        if (value.startsWith(STATSD_PREFIX) && value.length() > STATSD_PREFIX.length()) {
            String[] splitValue = value.split(":");
            String port = splitValue[splitValue.length - 1];
            try {
                positiveIntegerValidator.validate(name, port);
            } catch (ParameterException pe) {
                throw new ParameterException("Statsd Port should be a positive integer (found " + port + ")");
            }
            return;
        }
        if (!value.equals("console")) {
            throw new ParameterException("Parameter " + name + " should be either 'console', 'statsd:[STATSD_PORT]' or 'statsd:[STATSD_HOST]:[STATSD_PORT]'");
        }
    }
}