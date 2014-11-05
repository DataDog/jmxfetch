package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;

import java.util.Arrays;
import java.util.List;

public class Log4JLevelValidator implements IParameterValidator {
    public static final List<String> LOG4J_LEVELS = Arrays.asList("ALL", "DEBUG", "ERROR", "FATAL",
            "INFO", "OFF", "TRACE", "LEVEL", "WARN");

    public void validate(String name, String value)
            throws ParameterException {

        if (!LOG4J_LEVELS.contains(value)) {
            String message = "Parameter " + name + " should be in (" + Joiner.on(",").join(LOG4J_LEVELS) + ")";
            throw new ParameterException(message);
        }
    }
}