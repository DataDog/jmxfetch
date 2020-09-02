package org.datadog.jmxfetch.validator;


import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import org.datadog.jmxfetch.util.StringUtils;

import java.util.Arrays;
import java.util.List;

public class Log4JLevelValidator implements IParameterValidator {
    public static final List<String> LOG4J_LEVELS =
            Arrays.asList(
                    "ALL", "DEBUG", "ERROR", "FATAL", "INFO", "OFF", "TRACE", "LEVEL", "WARN");

    /** Validates a string as a valid Log4J logging level. */
    public void validate(String name, String value) throws ParameterException {

        if (!LOG4J_LEVELS.contains(value)) {
            String message =
                    "Parameter "
                            + name
                            + " should be in ("
                            + StringUtils.join(",", LOG4J_LEVELS)
                            + ")";
            throw new ParameterException(message);
        }
    }
}
