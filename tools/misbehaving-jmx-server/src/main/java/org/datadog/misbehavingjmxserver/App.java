package org.datadog.misbehavingjmxserver;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.javalin.*;
import lombok.extern.slf4j.Slf4j;

class AppConfig {
    // RMI Port is used for both registry and the JMX service
    @Parameter(names = {"--rmi-port", "-rp"})
    public int rmiPort = 1099;

    @Parameter(names = {"--rmi-host", "-rh"})
    public String rmiHost = "localhost";

    @Parameter(names = {"--control-port", "-cp"})
    public int controlPort = 8080;

    public void overrideFromEnv() {
        String val;
        val = System.getenv("RMI_PORT");
        if (val != null) {
            this.rmiPort = Integer.parseInt(val);
        }
        val = System.getenv("RMI_HOST");
        if (val != null) {
            this.rmiHost = val;
        }
        val = System.getenv("CONTROL_PORT");
        if (val != null) {
            this.controlPort = Integer.parseInt(val);
        }
    }
}

@Slf4j
public class App 
{
    public static void main( String[] args ) throws IOException, MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException
    {
        AppConfig config = new AppConfig();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        try {
            jCommander.parse(args);
            config.overrideFromEnv();
        } catch (Exception e) {
            jCommander.usage();
            System.exit(1);
        }

        InterruptibleRMISocketFactory customRMISocketFactory = new InterruptibleRMISocketFactory();
        // I don't think this call is actually important for jmx, the below 'env' param to JMXConnectorServerFactory is the important one
        RMISocketFactory.setSocketFactory(customRMISocketFactory);

        Javalin controlServer = Javalin.create();

        controlServer.post("/cutNetwork", ctx -> {
            customRMISocketFactory.setClosed(true);
            customRMISocketFactory.closeAllSockets();
            ctx.result("JMX network cut off").status(200);
        });
        controlServer.post("/restoreNetwork", ctx -> {
            customRMISocketFactory.setClosed(false);
            ctx.result("JMX network restored").status(200);
        });
        controlServer.start(config.controlPort);

        // Initialize RMI registry at same port as the jmx service
        LocateRegistry.createRegistry(config.rmiPort, null, customRMISocketFactory);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        String domain = "Bohnanza";
        ObjectName mbeanName = new ObjectName(domain + ":name=MyMBean");
        MyIntMBean mbean = new MyInt();
        mbs.registerMBean(mbean, mbeanName);

        Map<String, Object> env = new HashMap<>();
        // IMPORTANT! Without this, the custom RMI socket factory will not be used for JMX connections
        env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, customRMISocketFactory);

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + config.rmiHost + ":" + config.rmiPort + "/jmxrmi");
        log.info("JMXRMI Service listening at {}", url);
        JMXConnectorServer connector = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);

        connector.start();
        log.info("IAMREADY");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
