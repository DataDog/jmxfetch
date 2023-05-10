package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
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

    private void loadJmxAgent(com.sun.tools.attach.VirtualMachine vm) throws IOException {
        String agent =
                vm.getSystemProperties().getProperty("java.home")
                        + File.separator
                        + "lib"
                        + File.separator
                        + "management-agent.jar";
        try {
            vm.loadAgent(agent);
        } catch (Exception e) {
            log.warn("Error initializing JMX agent from management-agent.jar, trying 'startLocalManagementAgent' instead", e);
            // TODO this option doesn't exist in java 7, which we still support. How to call invoke it in a way that is safe for java7?
            vm.startLocalManagementAgent();
        }
    }
}
