package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;

@Slf4j
public class RemoteConnection extends Connection {

    private String host;
    private Integer port;
    private String user;
    private String password;
    private String path = "jmxrmi";
    private String jmxUrl;
    private String rmiTimeout;
    private static final String TRUST_STORE_PATH_KEY = "trust_store_path";
    private static final String TRUST_STORE_PASSWORD_KEY = "trust_store_password";
    private static final String KEY_STORE_PATH_KEY = "key_store_path";
    private static final String KEY_STORE_PASSWORD_KEY = "key_store_password";
    private static final String DEFAULT_RMI_RESPONSE_TIMEOUT =
            "15000"; // Match the collection period default

    /** RemoteConnection constructor for specified remote connection parameters. */
    public RemoteConnection(Map<String, Object> connectionParams) throws IOException {
        host = (String) connectionParams.get("host");
        try {
            port = (Integer) connectionParams.get("port");
        } catch (ClassCastException e) {
            port = Integer.parseInt((String) connectionParams.get("port"));
        }

        try {
            rmiTimeout = (String) connectionParams.get("rmi_client_timeout");
        } catch (ClassCastException e) {
            rmiTimeout = Integer.toString((Integer) connectionParams.get("rmi_client_timeout"));
        }

        if (rmiTimeout == null) {
            rmiTimeout = DEFAULT_RMI_RESPONSE_TIMEOUT;
        }

        user = (String) connectionParams.get("user");
        password = (String) connectionParams.get("password");
        jmxUrl = (String) connectionParams.get("jmx_url");

        if (connectionParams.containsKey("path")) {
            path = (String) connectionParams.get("path");
        }
        env = getEnv(connectionParams);
        address = getAddress();

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

        // Set an RMI timeout so we don't get stuck waiting for a bean to report a value
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", rmiTimeout);

        RMISocketFactory.setSocketFactory(new SocketFactory(Integer.parseInt(rmiTimeout)));

        createConnection();
    }

    private Map<String, Object> getEnv(Map<String, Object> connectionParams) {

        Map<String, Object> environment = new HashMap<String, Object>();

        if (connectionParams.containsKey("rmi_registry_ssl")
                && (Boolean) connectionParams.get("rmi_registry_ssl")) {
            SslRMIClientSocketFactory csf = new SslRMIClientSocketFactory();
            environment.put("com.sun.jndi.rmi.factory.socket", csf);
            environment.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, csf);
        }

        environment.put(JMXConnector.CREDENTIALS, new String[] {user, password});
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
