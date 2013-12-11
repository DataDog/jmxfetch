package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;


/**
 * Singleton used to share connections across instances in case you have multiple instances configured to the save MBeanServer (e.g. Solr and Tomcat)
 *
 */
public class ConnectionManager {
	private HashMap<LinkedHashMap<String, Object>, Connection> _connections;
	private static ConnectionManager _instance = null;
	private final static Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
	
	public static ConnectionManager getInstance() {
		if (_instance == null) {
			_instance = new ConnectionManager();
		}
		return _instance;
	}
	
	private ConnectionManager() {
		_connections = new HashMap<LinkedHashMap<String, Object>, Connection>();
	}
	
	 
	
	public Connection getConnection(LinkedHashMap<String, Object> connectionParams, boolean forceNewConnection) throws IOException {
		if (this._connections.containsKey(connectionParams) && !forceNewConnection) {
			return this._connections.get(connectionParams);
		}
		LOGGER.info("Connection not existing. Creating new connection");
		Connection jmxConnection = new Connection(connectionParams);
		this._connections.put(connectionParams, jmxConnection);
		return jmxConnection;
				
	}
	
}