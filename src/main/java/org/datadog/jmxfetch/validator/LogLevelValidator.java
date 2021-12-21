package org.datadog.jmxfetch.validator;


import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import org.datadog.jmxfetch.util.StringUtils;

import java.util.Arrays;
import java.util.List;

public class LogLevelValidator implements IParameterValidator {
    // for history, there is a `FATAL` log level supported here as we were supporting it
    // before moving to the `java.util.logging` log system.
    // we keep it here since we still want it to be valid, but we consider it as ERROR
    // in jmxfetch
    // FIXME: remove the "LEVEL" log level, which was introduced by mistake early in JMXFetch's
    // development (currently defaults to INFO).
    public static final List<String> LOGLEVELS =
            Arrays.asList(
                    "ALL", "DEBUG", "ERROR", "FATAL", "INFO", "OFF", "TRACE", "LEVEL", "WARN");

    /** Validates a string as a valid logging level. */
    public void validate(String name, String value) throws ParameterException {

        if (!LOGLEVELS.contains(value.toUpperCase())) {
            String message =
                    "Parameter "
                            + name
                            + " should be in ("
                            + StringUtils.join(",", LOGLEVELS)
                            + ")";
            throw new ParameterException(message);
        }
    }
}
