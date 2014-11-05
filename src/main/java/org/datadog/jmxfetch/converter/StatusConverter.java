package org.datadog.jmxfetch.converter;

import com.beust.jcommander.IStringConverter;
import org.datadog.jmxfetch.Status;

public class StatusConverter implements IStringConverter<Status> {

    public Status convert(String value) {
        return new Status(value);
    }
}