package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;


/**
 * Singleton used to share connections across instances in case you have multiple instances configured to the save MBeanServer (e.g. Solr and Tomcat)
 */
public class ConnectionManager {
    private final static Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    public static final String PROCESS_NAME_REGEX = "process_name_regex";
    private static ConnectionManager connectionManager = null;

    private ConnectionManager() {}

    public static ConnectionManager getInstance() {
        if (connectionManager == null) {
            connectionManager = new ConnectionManager();
        }
        return connectionManager;
    }

    private Connection createConnection(LinkedHashMap<String, Object> connectionParams) throws IOException {
        if (connectionParams.get(PROCESS_NAME_REGEX) != null) {
            try {
                Class.forName( "com.sun.tools.attach.AttachNotSupportedException" );
            } catch (ClassNotFoundException e) {
                throw new IOException("Unable to find tools.jar. Are you using a JDK and did you set the pass to tools.jar ?");
            }
            LOGGER.info("Connecting using Attach API");
            return new AttachApiConnection(connectionParams);

        }
        LOGGER.info("Connecting using JMX Remote");
        return new RemoteConnection(connectionParams);

    }

    public Connection getConnection(LinkedHashMap<String, Object> connectionParams, boolean forceNewConnection, Connection existingConnection) throws IOException {
        if (existingConnection == null || !existingConnection.isAlive()) {
            LOGGER.info("Connection closed or does not exist. Creating a new connection!");
            return createConnection(connectionParams);
        } else if (forceNewConnection) {
                LOGGER.info("Forcing the creation of a new connection");
                existingConnection.closeConnector();
                return createConnection(connectionParams);
        }
        return existingConnection;
    }

}