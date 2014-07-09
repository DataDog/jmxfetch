package org.datadog.jmxfetch.converter;

import com.beust.jcommander.IStringConverter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.reporter.ReporterFactory;

public class ReporterConverter implements IStringConverter<Reporter> {

    public Reporter convert(String value) {
        return ReporterFactory.getReporter(value);
    }
}
