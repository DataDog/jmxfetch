package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.management.ManagementFactory;

@Slf4j
public class JvmDirectConnection extends Connection {

    public JvmDirectConnection() throws IOException {
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
