package org.datadog.jmxfetch.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileHelper {
  /** Touches file. */
  public static void touch(File file) throws IOException {
    long timestamp = System.currentTimeMillis();
    touch(file, timestamp);
  }

  /** Touches file and set last modified timestamp. */
  public static void touch(File file, long timestamp) throws IOException {
    if (!file.exists()) {
      new FileOutputStream(file).close();
    }

    file.setLastModified(timestamp);
  }
}
