package org.datadog.jmxfetch.util;

import java.io.OutputStream;
import java.util.logging.ConsoleHandler;

public class StdoutConsoleHandler extends ConsoleHandler {
  protected void setOutputStream(OutputStream out) throws SecurityException {
    // force ConsoleHandler to set its output to System.out
    // instead of System.err
    super.setOutputStream(System.out);
  }
}
