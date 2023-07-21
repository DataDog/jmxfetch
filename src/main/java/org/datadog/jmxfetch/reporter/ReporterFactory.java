package org.datadog.jmxfetch.reporter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.datadog.jmxfetch.AppConfig;

public class ReporterFactory {

  /** Gets the reporter for the corresponding app config. */
  public static Reporter getReporter(AppConfig appConfig) {
    String type = appConfig.getReporterString();
    if (type == null || type.length() <= 0) {
      throw new IllegalArgumentException("Null or empty reporter type");
    }
    if ("console".equals(type)) {
      return new ConsoleReporter();
    } else if ("json".equals(type)) {
      return new JsonReporter();
    } else if (type.startsWith("statsd:")) {

      Matcher matcher = Pattern.compile("^statsd:(.*):(\\d+)$").matcher(type);
      if (matcher.find() && matcher.groupCount() == 2) {
        String host = matcher.group(1);
        Integer port = Integer.valueOf(matcher.group(2));
        return new StatsdReporter(
            host,
            port,
            appConfig.getStatsdTelemetry(),
            appConfig.getStatsdQueueSize(),
            appConfig.isStatsdNonBlocking(),
            appConfig.getStatsdBufferSize(),
            appConfig.getSocketTimeout());
      }

      matcher = Pattern.compile("^statsd:unix://(.*)$").matcher(type);
      if (matcher.find() && matcher.groupCount() == 1) {
        String socketPath = matcher.group(1);
        return new StatsdReporter(
            socketPath,
            0,
            appConfig.getStatsdTelemetry(),
            appConfig.getStatsdQueueSize(),
            appConfig.isStatsdNonBlocking(),
            appConfig.getStatsdBufferSize(),
            appConfig.getSocketTimeout());
      }
    }
    throw new IllegalArgumentException("Invalid reporter type: " + type);
  }

  private ReporterFactory() {}
}
