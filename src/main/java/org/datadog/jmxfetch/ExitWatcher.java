package org.datadog.jmxfetch;

import java.io.File;

public class ExitWatcher {

    private String exitFileLocation;
    private boolean isEnabled;

    public ExitWatcher(){
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

    public boolean shouldExit(){
        if (isEnabled()) {
            File f = new File(exitFileLocation);
            if(f.exists() && !f.isDirectory()) {
                return true;
            }
        }
        return false;
    }

}
