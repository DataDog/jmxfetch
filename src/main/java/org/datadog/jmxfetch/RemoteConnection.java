package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.util.JmxfetchRmiClientSocketFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;

@Slf4j
public class RemoteConnection extends Connection {

    private String host;
    private Integer port;
    private String user;
    private String password;
    private String path = "jmxrmi";
    private String jmxUrl;
    private Integer rmiTimeout;
    private Integer rmiConnectionTimeout;
    private static final String TRUST_STORE_PATH_KEY = "trust_store_path";
    private static final String TRUST_STORE_PASSWORD_KEY = "trust_store_password";
    private static final String KEY_STORE_PATH_KEY = "key_store_path";
    private static final String KEY_STORE_PASSWORD_KEY = "key_store_password";
    private static final int DEFAULT_RMI_CONNECTION_TIMEOUT = 20000;
    private static final int DEFAULT_RMI_TIMEOUT =
            15000; // Match the collection period default

    /** RemoteConnection constructor for specified remote connection parameters. */
    public RemoteConnection(Map<String, Object> connectionParams) throws IOException {
        System.setProperty("sun.rmi.dgc.client.gcInterval", "180000");
        this.host = (String) connectionParams.get("host");
        try {
            this.port = (Integer) connectionParams.get("port");
        } catch (ClassCastException e) {
            this.port = Integer.parseInt((String) connectionParams.get("port"));
        }

        try {
            this.rmiTimeout = (Integer) connectionParams.get("rmi_client_timeout");
        } catch (final ClassCastException e) {
            this.rmiTimeout = Integer.parseInt((String) connectionParams.get("rmi_client_timeout"));
        }
        if (this.rmiTimeout == null) {
            this.rmiTimeout = DEFAULT_RMI_TIMEOUT;
        }

        try {
            this.rmiConnectionTimeout = (Integer) connectionParams.get("rmi_connection_timeout");
        } catch (final ClassCastException e) {
            this.rmiConnectionTimeout = 
                Integer.parseInt((String) connectionParams.get("rmi_connection_timeout"));
        }
        if (this.rmiConnectionTimeout == null) {
            this.rmiConnectionTimeout = DEFAULT_RMI_CONNECTION_TIMEOUT;
        }

        if (connectionParams.containsKey("user") && connectionParams.containsKey("password")) {
            if (connectionParams.get("user") != null && connectionParams.get("password") != null) {
                this.user = (String) connectionParams.get("user");
                this.password = (String) connectionParams.get("password");
            }
        }

        this.jmxUrl = (String) connectionParams.get("jmx_url");

        if (connectionParams.containsKey("path")) {
            this.path = (String) connectionParams.get("path");
        }

        this.env = getEnv(connectionParams);
        this.address = getAddress();

        String trustStorePath;
        String trustStorePassword;
        if (connectionParams.containsKey(TRUST_STORE_PATH_KEY)
                && connectionParams.containsKey(TRUST_STORE_PASSWORD_KEY)) {
            trustStorePath = (String) connectionParams.get(TRUST_STORE_PATH_KEY);
            trustStorePassword = (String) connectionParams.get(TRUST_STORE_PASSWORD_KEY);
            if (trustStorePath != null && trustStorePassword != null) {
                System.setProperty("javax.net.ssl.trustStore", trustStorePath);
                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);

                log.info(
                        "Setting trustStore path: " + trustStorePath + " and trustStorePassword");
            }
        }

        String keyStorePath;
        String keyStorePassword;
        if (connectionParams.containsKey(KEY_STORE_PATH_KEY)
                && connectionParams.containsKey(KEY_STORE_PASSWORD_KEY)) {
            keyStorePath = (String) connectionParams.get(KEY_STORE_PATH_KEY);
            keyStorePassword = (String) connectionParams.get(KEY_STORE_PASSWORD_KEY);
            if (keyStorePath != null && keyStorePassword != null) {
                System.setProperty("javax.net.ssl.keyStore", keyStorePath);
                System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);

                log.info("Setting keyStore path: " + keyStorePath + " and keyStorePassword");
            }
        }
        this.createConnection();
    }

    private Map<String, Object> getEnv(Map<String, Object> connectionParams) {
        Map<String, Object> environment = new HashMap<String, Object>();
        boolean useSsl = (connectionParams.containsKey("rmi_registry_ssl")
                && (Boolean) connectionParams.get("rmi_registry_ssl"));
        JmxfetchRmiClientSocketFactory csf = 
            new JmxfetchRmiClientSocketFactory(rmiTimeout, rmiConnectionTimeout, useSsl);
        environment.put("com.sun.jndi.rmi.factory.socket", csf);
        environment.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, csf);

        // Don't set `JMXConnector.CREDENTIALS` if `user` or `password` null as this will cause
        // a `NullPointerException` when creating a remote connection if null
        // https://github.com/DataDog/jmxfetch/issues/545
        if (this.user != null && this.password != null) {
            environment.put(JMXConnector.CREDENTIALS, new String[] { this.user, this.password });
        }
        return environment;
    }

    private JMXServiceURL getAddress()
            throws MalformedURLException {
        if (this.jmxUrl != null) {
            return new JMXServiceURL(this.jmxUrl);
        }
        return new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://" + this.host + ":" + this.port + "/" + this.path);
    }
}
