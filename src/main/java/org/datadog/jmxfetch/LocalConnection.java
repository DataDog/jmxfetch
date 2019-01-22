package org.datadog.jmxfetch;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.apache.log4j.Logger;

public class LocalConnection extends Connection {
    private final static Logger LOGGER = Logger.getLogger(LocalConnection.class.getName());

    public LocalConnection() throws IOException {
        createConnection();
    }

    protected void createConnection() throws IOException {
        mbs = ManagementFactory.getPlatformMBeanServer();
    }

    public void closeConnector() {
        // ignore
    }

    public boolean isAlive() {
        return true;
    }
}
