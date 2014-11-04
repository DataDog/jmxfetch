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
        return connectionParams.get("host") + ":" + connectionParams.get("port") + ":" + connectionParams.get("user");
    }

    public Connection getConnection(LinkedHashMap<String, Object> connectionParams, boolean forceNewConnection) throws IOException {
        String key = generateKey(connectionParams);
        Connection existingConnection = cache.get(key);
        if (existingConnection == null || !existingConnection.isAlive()) {
            LOGGER.info("Connection closed or does not exist. Creating a new connection!");
            cache.put(key, new Connection(connectionParams));
        } else {
            if (forceNewConnection) {
                LOGGER.info("Forcing the creation of a new connection");
                cache.get(key).close();
                cache.put(key, new Connection(connectionParams));
            } else {
                LOGGER.info("Connection already exists for key: " + key + " . Using it...");
            }
        }
        return cache.get(key);
    }

}