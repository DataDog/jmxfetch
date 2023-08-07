package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.management.remote.JMXServiceURL;

@Slf4j
public class AttachApiConnection extends Connection {
    private static final String CONNECTOR_ADDRESS =
            "com.sun.management.jmxremote.localConnectorAddress";
    private String processRegex;

    /** AttachApiConnection constructor for specified connection parameters. */
    public AttachApiConnection(Map<String, Object> connectionParams) throws IOException {
        processRegex = (String) connectionParams.get("process_name_regex");
        this.env = new HashMap<String, Object>();
        this.address = getAddress(connectionParams);
        createConnection();
    }

    private JMXServiceURL getAddress(Map<String, Object> connectionParams)
            throws IOException {
        JMXServiceURL address;
        try {
            address = new JMXServiceURL(getJmxUrlForProcessRegex(processRegex));
        } catch (com.sun.tools.attach.AttachNotSupportedException e) {
            throw new IOException("Unnable to attach to process regex:  " + processRegex, e);
        }
        return address;
    }

    private String getJmxUrlForProcessRegex(String processRegex)
            throws com.sun.tools.attach.AttachNotSupportedException, IOException {
        for (com.sun.tools.attach.VirtualMachineDescriptor vmd :
                com.sun.tools.attach.VirtualMachine.list()) {
            if (vmd.displayName().matches(processRegex)) {
                com.sun.tools.attach.VirtualMachine vm =
                        com.sun.tools.attach.VirtualMachine.attach(vmd);
                String connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
                // If jmx agent is not running in VM, load it and return the connector url
                if (connectorAddress == null) {
                    loadJmxAgent(vm);

                    // agent is started, get the connector address
                    connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
                }

                return connectorAddress;
            }
        }

        throw new IOException(
                "No match found. Available JVMs can be listed with the `list_jvms` command.");
    }

    // management-agent.jar has been removed in java 8+
    // Once JMXFetch drops java 7 support, this should be simplified to simply invoke 
    // vm.startLocalManagementAgent which is accessible in java 8 if tools.jar is added 
    // to the classpath and java 9+ by default 
    // ref https://bugs.openjdk.org/browse/JDK-8179063
    private void loadJmxAgent(com.sun.tools.attach.VirtualMachine vm) throws IOException {
        try {
            Method method = com.sun.tools.attach.VirtualMachine
                .class.getMethod("startLocalManagementAgent");
            log.info("Found startLocalManagementAgent API, attempting to use it.");
            method.invoke(vm);
        } catch (NoSuchMethodException noMethodE) {
            log.warn("startLocalManagementAgent method not found, must be on java 7", noMethodE);
            String agent = vm.getSystemProperties().getProperty("java.home")
                + File.separator
                + "lib"
                + File.separator
                + "management-agent.jar";
            try {
                vm.loadAgent(agent);
            } catch (Exception e) {
                log.warn("Error initializing JMX agent from management-agent.jar", e);
            }
        } catch (Exception reflectionE) {
            log.warn("Error invoking the startLocalManagementAgent method", reflectionE);
        }
        
        
    }
}
