package org.datadog.jmxfetch.converter;

import com.beust.jcommander.IStringConverter;
import org.datadog.jmxfetch.ExitWatcher;

public class ExitWatcherConverter implements IStringConverter<ExitWatcher> {

    public ExitWatcher convert(String value) {
        return new ExitWatcher(value);
    }
}
