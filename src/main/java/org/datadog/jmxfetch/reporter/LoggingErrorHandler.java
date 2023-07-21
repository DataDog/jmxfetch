package org.datadog.jmxfetch.reporter;

import com.timgroup.statsd.StatsDClientErrorHandler;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/** An error handler class to track errors as required. */
@Slf4j
public class LoggingErrorHandler implements StatsDClientErrorHandler {
  private AtomicInteger errors = new AtomicInteger();

  @Override
  public void handle(Exception exception) {
    errors.incrementAndGet();
    log.error("statsd client error:", exception);
  }

  public int getErrors() {
    return errors.get();
  }
}
