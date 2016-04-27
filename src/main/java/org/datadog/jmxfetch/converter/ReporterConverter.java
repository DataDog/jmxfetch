package org.datadog.jmxfetch.converter;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.reporter.ReporterFactory;

import com.beust.jcommander.IStringConverter;

public class ReporterConverter implements IStringConverter<Reporter> {

    public Reporter convert(String value) {
        return ReporterFactory.getReporter(value);
    }
}
