package org.datadog.jmxfetch.converter;

import org.datadog.jmxfetch.ExitWatcher;

import com.beust.jcommander.IStringConverter;

public class ExitWatcherConverter implements IStringConverter<ExitWatcher> {

    public ExitWatcher convert(String value) {
        return new ExitWatcher(value);
    }
}
