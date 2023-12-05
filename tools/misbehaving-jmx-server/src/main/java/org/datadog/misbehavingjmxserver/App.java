package org.datadog.misbehavingjmxserver;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RMISocketFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.javalin.*;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import org.datadog.Defaults;

@Slf4j
class AppConfig {
    // RMI Port is used for both registry and the JMX service
    @Parameter(names = {"--rmi-port", "-rp"})
    public int rmiPort = Defaults.JMXSERVER_RMI_PORT;

    @Parameter(names = {"--rmi-host", "-rh"})
    public String rmiHost = Defaults.JMXSERVER_RMI_INTERFACE;

    @Parameter(names = {"--rng-seed", "-rs"})
    public Long rngSeed = 54321L;

    // Can only be set via env var
    public int controlPort = Defaults.JMXSERVER_CONTROL_PORT;

    @Parameter(names = {"--config-path", "-cfp"})
    public String config_path = "./misbehaving-config.yaml";

    public Configuration jmxConfiguration;

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
        val = System.getenv("RNG_SEED");
        if (val != null) {
            this.rngSeed = Long.parseLong(val);
        }
    }

    public void readConfigFileOnDisk () {
        File f = new File(config_path);
        String yamlPath = f.getPath();
        try{
            FileInputStream yamlInputStream = new FileInputStream(yamlPath);
            Yaml yaml = new Yaml(new Constructor(Configuration.class));
            jmxConfiguration = yaml.load(yamlInputStream);
            log.info("Configuration read from " + config_path + " is:\n" + jmxConfiguration);
        } catch (FileNotFoundException e) {
            log.warn("Could not find your config file at " + yamlPath);
            jmxConfiguration = null;
        }
    }

}

class Configuration {
    public Map<String,BeanSpec> domains;
    public Long seed;

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (domains != null) {
            for (Map.Entry<String,BeanSpec> entry: domains.entrySet()) {
                result.append("Domain: " + entry.getKey() + entry.getValue().toString() + "\n");
            }
        } else {
            result.append("No valid domain configurations\n");
        }

        if (seed != null) {
            result.append("RNG Seed: " + seed + "\n");
        }

        return result.toString();
    }
}
class BeanSpec {
    public int beanCount;
    public int scalarAttributeCount;
    public int tabularAttributeCount;
    public int compositeValuesPerTabularAttribute;

    public BeanSpec() {

    }

    public BeanSpec(int beanCount, int scalarAttributeCount, int tabularAttributeCount, int compositeValuesPerTabularAttribute) {
        this.beanCount = beanCount;
        this.scalarAttributeCount = scalarAttributeCount;
        this.tabularAttributeCount = tabularAttributeCount;
        this.compositeValuesPerTabularAttribute = compositeValuesPerTabularAttribute;
    }

    @Override
    public String toString() {
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
    final static String testDomain = "Bohnanza";
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
        config.readConfigFileOnDisk();

        InterruptibleRMISocketFactory customRMISocketFactory = new InterruptibleRMISocketFactory();
        // I don't think this call is actually important for jmx, the below 'env' param to JMXConnectorServerFactory is the important one
        RMISocketFactory.setSocketFactory(customRMISocketFactory);


        // Explicitly set RMI hostname to specified argument value
        // This value is returned from the RMI registry as the hostname that the client should connect out to.
        System.setProperty("java.rmi.server.hostname", config.rmiHost);

        // It is ridiculously hard to find good reference docs for the system properties controlling RMI ports.
        // There are 3 ports relevant to JMX over RMI
        // 1. port that RMI registry is available on. The initial network request for a RMI connection
        //    comes into this port.
        //    This java option is the one that controls the RMI registry port
        System.setProperty("com.sun.management.jmxremote.rmi.port", "" + config.rmiPort);//
        // If we don't create this registry explicitly, the above system property should be used.
        LocateRegistry.createRegistry(config.rmiPort, null, customRMISocketFactory);
        // 2. port that RMI advertises in the initial handshake. This defaults to a random port unless
        //    the system property is set. Some docs call this the "RMI Server Port", with there being
        //    a distinction between "RMI Registry" and "RMI Server".
        System.setProperty("com.sun.management.jmxremote.port", "" + config.rmiPort);//
        // 3. A local port that is used for the AttachAPI. This doesn't matter for the purposes of our jmx-test-server,
        // com.sun.management.jmxremote.local.port=<port#>
        //
        // References:
        // https://www.oracle.com/java/technologies/javase/15all-relnotes.html#JDK-8234484


        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        MetricDAO mDao = new MetricDAO();
        mDao.runTickLoop();

        if (config.jmxConfiguration != null && config.jmxConfiguration.seed != null) {
            config.rngSeed = config.jmxConfiguration.seed;
        }
        log.info("RNG initializing with seed: {}", config.rngSeed);
        BeanManager bm = new BeanManager(mbs, mDao, config.rngSeed);

        // Set up test domain
        BeanSpec testDomainBeanSpec = new BeanSpec(1, 1, 0, 0);
        bm.setMBeanState(testDomain, testDomainBeanSpec);

        // Set up initial beans for all the domains found in config file
        if (config.jmxConfiguration != null){
            for (Map.Entry<String,BeanSpec> entry: config.jmxConfiguration.domains.entrySet()) {
                bm.setMBeanState(entry.getKey(), entry.getValue());
            }
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

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + config.rmiPort + "/jmxrmi");
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
