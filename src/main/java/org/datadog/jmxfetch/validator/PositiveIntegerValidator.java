package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class PositiveIntegerValidator implements IParameterValidator {
    public void validate(String name, String value) throws ParameterException {
        try {
            int n = Integer.parseInt(value);
            if (n <= 0) {
                throw new ParameterException("Parameter " + name
                        + " should be positive (found " + value + ")");
            }
        } catch (NumberFormatException e) {
            throw new ParameterException("Parameter " + name
                    + " should be an integer (found " + value + ")");
        }
    }
}
