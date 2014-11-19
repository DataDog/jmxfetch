package org.datadog.jmxfetch;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import javax.management.remote.JMXServiceURL;

import org.apache.log4j.Logger;

public class AttachApiConnection extends Connection {
    private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
    private final static Logger LOGGER = Logger.getLogger(Connection.class.getName());
    private String processRegex;

    public AttachApiConnection(LinkedHashMap<String, Object> connectionParams) throws IOException {
        processRegex = (String) connectionParams.get("process_name_regex");
        this.env = new HashMap<String, Object>();
        this.address = getAddress(connectionParams);
        createConnection();
      }

    private JMXServiceURL getAddress(LinkedHashMap<String, Object> connectionParams) throws IOException {
        JMXServiceURL address;
        try {
            address = new JMXServiceURL(getJMXUrlForProcessRegex(processRegex));
        }  catch (com.sun.tools.attach.AttachNotSupportedException e) {
            throw new IOException("Unnable to attach to process regex:  "+ processRegex, e);
        }
        return address;
        
    }

     private String getJMXUrlForProcessRegex(String processRegex) throws com.sun.tools.attach.AttachNotSupportedException, IOException {
        for (com.sun.tools.attach.VirtualMachineDescriptor vmd : com.sun.tools.attach.VirtualMachine.list()) {
            if (vmd.displayName().matches(processRegex)) {
                com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(vmd);
                String connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
                //If jmx agent is not running in VM, load it and return the connector url
                if (connectorAddress == null) {
                    loadJMXAgent(vm);

                    // agent is started, get the connector address
                    connectorAddress = vm.getAgentProperties().getProperty(
                            CONNECTOR_ADDRESS);
                }

                return connectorAddress;
            }
        }
        throw new IOException("Cannot find JVM matching regex: " + processRegex);
    }

    private void loadJMXAgent(com.sun.tools.attach.VirtualMachine vm) throws IOException {
        String agent = vm.getSystemProperties().getProperty(
                "java.home")
                + File.separator
                + "lib"
                + File.separator
                + "management-agent.jar";
        try {
            vm.loadAgent(agent);
        } catch (Exception e) {
            LOGGER.warn("Error initializing JMX agent", e);
        }
    }
}
