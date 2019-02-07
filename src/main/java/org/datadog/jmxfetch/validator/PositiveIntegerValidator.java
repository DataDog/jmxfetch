package org.datadog.jmxfetch.validator;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

public class PositiveIntegerValidator implements IParameterValidator {

    /** Validates whether an integer is positive. */
    public void validate(String name, String value) throws ParameterException {
        try {
            int num = Integer.parseInt(value);
            if (num <= 0) {
                throw new ParameterException(
                        "Parameter " + name + " should be positive (found " + value + ")");
            }
        } catch (NumberFormatException e) {
            throw new ParameterException(
                    "Parameter " + name + " should be an integer (found " + value + ")");
        }
    }
}
