package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

public class CustomLogger {
	static private HashMap<String, Integer> message_stats = new HashMap<String, Integer>();
	private final static Logger LOGGER = Logger.getLogger(CustomLogger.class.getName());
	static public void setup(Level level, String logLocation) throws IOException {

		if( logLocation != null) {
			FileAppender fa = new FileAppender();
			fa.setName("FileLogger");
			fa.setFile(logLocation);
			fa.setLayout(new PatternLayout("%d | %-5p| %c{1} | %m%n"));  
			fa.setThreshold(level);
			fa.setAppend(true);
			fa.activateOptions();
			Logger.getRootLogger().addAppender(fa);
			LOGGER.info("File Handler set");
		} else {
			System.out.println("Log location is not set, not logging to file");
		}

	}

	static public void laconic(Logger logger, Level level, String message, int max) {
		if (!message_stats.containsKey(message)) {
			logger.log(level, message);
			message_stats.put(message, 1);
		} else if( message_stats.get(message) < max) {
			logger.log(level, message);
			message_stats.put(message, message_stats.get(message) + 1);
		}

	}

} 