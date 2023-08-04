package org.datadog.misbehavingjmxserver;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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

import org.datadog.Defaults;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

@Slf4j
class AppConfig {
    // RMI Port is used for both registry and the JMX service
    @Parameter(names = {"--rmi-port", "-rp"})
    public int rmiPort = Defaults.JMXSERVER_RMI_PORT;

    @Parameter(names = {"--rmi-host", "-rh"})
    public String rmiHost = Defaults.JMXSERVER_RMI_INTERFACE;

    // Can only be set via env var
    public int controlPort = Defaults.JMXSERVER_CONTROL_PORT;

    @Parameter(names = {"--config-path", "-cfp"})
    public String config_path = "configs/benchmark_config.yaml";

    public JmxDomainConfigurations jmxDomainConfigurations;

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
        val = System.getenv("CONFIG_PATH");
        if (val != null) {
            this.config_path = val;
        }
    }

    public void readConfigFileOnDisk () {
        File f = new File(config_path);
        String yamlPath = f.getAbsolutePath();
        try{
            FileInputStream yamlInputStream = new FileInputStream(yamlPath);
            Yaml yaml = new Yaml(new Constructor(JmxDomainConfigurations.class));
            jmxDomainConfigurations = yaml.load(yamlInputStream);
            log.info("JmxDomainConfigurations read from " + config_path + " is:\n" + jmxDomainConfigurations);
        } catch (FileNotFoundException e) {
            log.warn("Could not find your config file at " + yamlPath);
            jmxDomainConfigurations = null;
            log.warn("JmxDomainConfigurations is null");
        }
    }
}

class JmxDomainConfigurations {
    public Map<String,BeanSpec> domains;

    @Override
    public String toString(){
        StringBuilder result = new StringBuilder();
        if (domains != null) {
            for (Map.Entry<String,BeanSpec> entry: domains.entrySet()){
            result.append("Domain: " + entry.getKey() + entry.getValue().toString() + "\n");
            }
        }else{
            return "No valid domain configurations";
        }

        return result.toString();
    }
}
class BeanSpec {
    public int beanCount;
    public int scalarAttributeCount;
    public int tabularAttributeCount;
    public int compositeValuesPerTabularAttribute;

    @Override
    public String toString(){
        return  "\n\t-beanCount: " + beanCount +
        "\n\t-scalarAttributeCount: " + scalarAttributeCount +
        "\n\t-tabularAttributeCount: " + tabularAttributeCount +
        "\n\t-compositeValuesPerTabularAttribute: " + compositeValuesPerTabularAttribute;
    }
}

@Slf4j
public class App
{
    private static boolean started = false;
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
        //get bean count and properties from config file
        config.readConfigFileOnDisk();

        InterruptibleRMISocketFactory customRMISocketFactory = new InterruptibleRMISocketFactory();
        // I don't think this call is actually important for jmx, the below 'env' param to JMXConnectorServerFactory is the important one
        RMISocketFactory.setSocketFactory(customRMISocketFactory);

        // Uncomment this to be able to access jmx server on MacOS
        // System.setProperty("java.rmi.server.hostname", config.rmiHost);

        // Initialize RMI registry at same port as the jmx service
        LocateRegistry.createRegistry(config.rmiPort, null, customRMISocketFactory);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        MetricDAO mDao = new MetricDAO();
        mDao.runTickLoop();

        BeanManager bm = new BeanManager(mbs, mDao);

        //set up initial beans for all the domains found in config file
        for (Map.Entry<String,BeanSpec> entry: config.jmxDomainConfigurations.domains.entrySet()){
            bm.setMBeanState(entry.getKey(), entry.getValue());
        }

        Javalin controlServer = Javalin.create();

        controlServer.get("/ready", ctx -> {
            if (started) {
                ctx.status(200);
            } else {
                ctx.status(500);
            }
        });

        controlServer.post("/cutNetwork", ctx -> {
            customRMISocketFactory.setClosed(true);
            customRMISocketFactory.closeAllSockets();
            ctx.result("JMX network cut off").status(200);
        });
        controlServer.post("/restoreNetwork", ctx -> {
            customRMISocketFactory.setClosed(false);
            ctx.result("JMX network restored").status(200);
        });

        controlServer.get("/beans/{domain}", ctx -> {
            String domain = ctx.pathParam("domain");
            Optional<List<DynamicMBeanMetrics>> bs = bm.getMBeanState(domain);
            if (bs.isPresent()) {
                List<String> metricNames = bs.get().stream().map(metric -> metric.name).collect(Collectors.toList());

                ctx.status(200).json(metricNames);
            } else {
                ctx.status(404);
            }
        });

        controlServer.post("/beans/{domain}", ctx -> {
            String domain = ctx.pathParam("domain");

            BeanSpec beanSpec;
            try {
                beanSpec = ctx.bodyAsClass(BeanSpec.class);
            } catch (Exception e) {
                ctx.status(400).result("Invalid JSON format");
                return;
            }

            // This should block until the mbeanserver reaches the desired state
            bm.setMBeanState(domain, beanSpec);

            ctx.status(200).result("Received bean request for domain: " + domain);
        });
        controlServer.start(config.controlPort);


        Map<String, Object> env = new HashMap<>();
        // IMPORTANT! Without this, the custom RMI socket factory will not be used for JMX connections
        env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, customRMISocketFactory);

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + config.rmiHost + ":" + config.rmiPort + "/jmxrmi");
        log.info("JMXRMI Service listening at {}", url);
        JMXConnectorServer connector = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);

        connector.start();
        started = true;
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
