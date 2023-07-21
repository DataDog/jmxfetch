package org.datadog.jmxfetch;

import java.io.File;

public class ExitWatcher {

  private String exitFileLocation;
  private boolean isEnabled;

  /** Default constructor. */
  public ExitWatcher() {
    this(null);
  }

  public ExitWatcher(String exitFileLocation) {
    this.exitFileLocation = exitFileLocation;
    this.isEnabled = this.exitFileLocation != null;
  }

  public String getExitFileLocation() {
    return exitFileLocation;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  /** Evaluates if its time to exit according to exit-file presence. */
  public boolean shouldExit() {
    if (isEnabled()) {
      File file = new File(exitFileLocation);
      if (file.exists() && !file.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return exitFileLocation;
  }
}
