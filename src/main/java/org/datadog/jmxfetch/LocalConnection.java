package org.datadog.jmxfetch;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.management.ManagementFactory;


public class LocalConnection extends Connection {
    private static final Logger LOGGER = Logger.getLogger(LocalConnection.class.getName());

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
