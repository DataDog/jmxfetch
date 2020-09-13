package org.datadog.jmxfetch.converter;

import org.datadog.jmxfetch.Status;

import com.beust.jcommander.IStringConverter;

public class StatusConverter implements IStringConverter<Status> {

    public Status convert(String value) {
        return new Status(value);
    }
}