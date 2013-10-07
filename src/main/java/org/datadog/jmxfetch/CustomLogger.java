package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CustomLogger {
  static private FileHandler handler;
  static private SimpleFormatter formatter;
  static private HashMap<String, Integer> message_stats = new HashMap<String, Integer>();
  static public void setup(Level level, String log_location) throws IOException {

    Logger logger = Logger.getLogger(CustomLogger.class.getPackage().getName());

    logger.setLevel(level);
    handler = new FileHandler(log_location);
   
    formatter = new SimpleFormatter();
    handler.setFormatter(formatter);
    handler.setLevel(level);
    logger.addHandler(handler);

  }
  
  static public void laconic(Logger logger, Level level, String message, int max) {
	  if (!message_stats.containsKey(message)) {
		  logger.log(level, message);
		  message_stats.put(message, 1);
	  }
	  else if( message_stats.get(message) < max) {
		  logger.log(level, message);
		  message_stats.put(message, message_stats.get(message) + 1);
	  }
	  
  }
  
} 