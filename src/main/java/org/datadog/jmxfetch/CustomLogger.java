package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CustomLogger {
  static private FileHandler handler;
  static private SimpleFormatter formatter;
  static public void setup(Level level, String log_location) throws IOException {

    Logger logger = Logger.getLogger(CustomLogger.class.getPackage().getName());

    logger.setLevel(level);
    handler = new FileHandler(log_location);
   
    formatter = new SimpleFormatter();
    handler.setFormatter(formatter);
    handler.setLevel(level);
    logger.addHandler(handler);

  }
} 