package org.datadog.jmxfetch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

import org.apache.log4j.Logger;

public class RemoteConnection extends Connection {

    private String host;
    private Integer port;
    private String user;
    private String password;
    private String path = "jmxrmi";
    private String jmx_url;
    private static final String TRUST_STORE_PATH_KEY = "trust_store_path";
    private static final String TRUST_STORE_PASSWORD_KEY = "trust_store_password";
    private final static Logger LOGGER = Logger.getLogger(Connection.class.getName());

    public RemoteConnection(LinkedHashMap<String, Object> connectionParams)
            throws IOException {
        host = (String) connectionParams.get("host");
        port = (Integer) connectionParams.get("port");
        user = (String) connectionParams.get("user");
        password = (String) connectionParams.get("password");
        jmx_url = (String) connectionParams.get("jmx_url");
        if (connectionParams.containsKey("path")){
            path = (String) connectionParams.get("path");
        }
        env = getEnv(connectionParams);
        address = getAddress(connectionParams);

        String trustStorePath;
        String trustStorePassword;
        if (connectionParams.containsKey(TRUST_STORE_PATH_KEY)
                && connectionParams.containsKey(TRUST_STORE_PASSWORD_KEY)) {
            trustStorePath = (String) connectionParams.get(TRUST_STORE_PATH_KEY);
            trustStorePassword = (String) connectionParams.get(TRUST_STORE_PASSWORD_KEY);
            if (trustStorePath != null && trustStorePassword != null) {
                System.setProperty("javax.net.ssl.trustStore", trustStorePath);
                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);

                LOGGER.info("Setting trustStore path: " + trustStorePath + " and trustStorePassword");
            }

        }
        createConnection();

    }

    private HashMap<String, Object> getEnv(
            LinkedHashMap<String, Object> connectionParams) {

        HashMap<String, Object> environment = new HashMap<String, Object>();
        environment.put(JMXConnector.CREDENTIALS, new String[]{user, password});
        return environment;
    }

    private JMXServiceURL getAddress(
            LinkedHashMap<String, Object> connectionParams) throws MalformedURLException {
        if (this.jmx_url != null) {
            return new JMXServiceURL(this.jmx_url);
        }
        return new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + this.host + ":" + this.port +"/" + this.path);
    }


}
