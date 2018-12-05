package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;


/**
 * Singleton used to create connections to the MBeanServer
 */
public class ConnectionFactory {
    private final static Logger LOGGER = Logger.getLogger(ConnectionFactory.class.getName());
    public static final String PROCESS_NAME_REGEX = "process_name_regex";
    private static ConnectionFactory connectionFactory = null;

    public static Connection createConnection(LinkedHashMap<String, Object> connectionParams) throws IOException {
        if (connectionParams.get(PROCESS_NAME_REGEX) != null) {
            try {
                Class.forName( "com.sun.tools.attach.AttachNotSupportedException" );
            } catch (ClassNotFoundException e) {
                throw new IOException("Unable to find tools.jar. Are you using a JDK and did you set the pass to tools.jar ?");
            }
            LOGGER.info("Connecting using Attach API");
            return new AttachApiConnection(connectionParams);

        }

        // This is used by dd-java-agent to enable directly connecting to the mbean server.
        // This works because jmxfetch is being run as a library inside the process.
        if("service:jmx:local:///".equals(connectionParams.get("jmx_url"))) {
            LOGGER.info("Connecting using JMX Local");
            return new LocalConnection();
        }

        LOGGER.info("Connecting using JMX Remote");
        return new RemoteConnection(connectionParams);

    }

}