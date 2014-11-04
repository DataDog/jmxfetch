package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class ReporterValidator implements IParameterValidator {

    private static final String STATSD_PREFIX = "statsd:";
    private final PositiveIntegerValidator positiveIntegerValidator = new PositiveIntegerValidator();

    @Override
    public void validate(String name, String value) throws ParameterException {
        if (value.startsWith(STATSD_PREFIX) && value.length() > STATSD_PREFIX.length()) {
            String port = new String(value.split(":")[1]);
            try {
                positiveIntegerValidator.validate(name, port);
            } catch (ParameterException pe) {
                throw new ParameterException("Statsd Port should be a positive integer (found " + port + ")");
            }
            return;
        }
        if (!value.equals("console")) {
            throw new ParameterException("Parameter " + name + " should be either 'console' or 'statsd:[STATSD_PORT]'");
        }
    }
}