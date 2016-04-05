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
    private HashMap<String, Connection> cache;

    private ConnectionManager() {
        cache = new HashMap<String, Connection>();
    }

    public static ConnectionManager getInstance() {
        if (connectionManager == null) {
            connectionManager = new ConnectionManager();
        }
        return connectionManager;
    }

    private static String generateKey(LinkedHashMap<String, Object> connectionParams) {
        if (connectionParams.get(PROCESS_NAME_REGEX) != null) {
            return (String) connectionParams.get(PROCESS_NAME_REGEX);
        } else if (connectionParams.get("jmx_url") != null) {
            return (String) connectionParams.get("jmx_url");
        }
        return connectionParams.get("host") + ":" + connectionParams.get("port") + ":" + connectionParams.get("user");
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

    public Connection getConnection(LinkedHashMap<String, Object> connectionParams, boolean forceNewConnection) throws IOException {
        String key = generateKey(connectionParams);
        Connection existingConnection = cache.get(key);
        if (existingConnection == null || !existingConnection.isAlive()) {
            LOGGER.info("Connection closed or does not exist. Creating a new connection!");
            cache.put(key, createConnection(connectionParams));
        } else {
            if (forceNewConnection) {
                LOGGER.info("Forcing the creation of a new connection");
                cache.get(key).closeConnector();
                cache.put(key, createConnection(connectionParams));
            } else {
                LOGGER.info("Connection already exists for key: " + key + " . Using it...");
            }
        }
        return cache.get(key);
    }

}