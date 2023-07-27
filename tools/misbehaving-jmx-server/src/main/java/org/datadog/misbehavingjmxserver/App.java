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

class AppConfig {
    // RMI Port is used for both registry and the JMX service
    @Parameter(names = {"--rmi-port", "-rp"})
    public int rmiPort = Defaults.JMXSERVER_RMI_PORT;

    @Parameter(names = {"--rmi-host", "-rh"})
    public String rmiHost = Defaults.JMXSERVER_RMI_INTERFACE;

    // Can only be set via env var
    public int controlPort = Defaults.JMXSERVER_CONTROL_PORT;

    @Parameter(names = {"--bean-count", "-bc"})
    public int bean_count;

    @Parameter(names = {"--bean-domain", "-bd"})
    public String domain;

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
        val = System.getenv("BEAN_COUNT");
        if (val != null) {
            this.bean_count = Integer.parseInt(val);
            System.out.println("updated bean_count from env: " + bean_count);
        }
        val = System.getenv("BEAN_DOMAIN");
        if (val != null) {
            this.domain = val;
            System.out.println("updated domain from env: " + domain);
        }
    }

    public void initConfig() throws IOException {
        Map<String, Object> jmxConfig = getConfig();
        if (jmxConfig != null){
            System.out.println("jmx config file looks like: " + jmxConfig);
        } else {
            System.out.println("jmx config file not found");
            return;
        }

        int beans_val;
        beans_val = (Integer) jmxConfig.get("bean_count");
        if (beans_val != 0) {
            this.bean_count = beans_val;
            System.out.println("updated bean_count from config: " + bean_count);
        }

        String domain_val;
        domain_val = (String) jmxConfig.get("domain");
        if (domain_val != null) {
            this.domain = domain_val;
            System.out.println("updated domain from config: " + domain);
        }
    }

    public Map<String, Object> getConfig () throws IOException {
        File f = new File("configs/", "benchmark_config.yaml");
        String yamlPath = f.getAbsolutePath();
        try{
            FileInputStream yamlInputStream = new FileInputStream(yamlPath);
            YamlParser fileConfig = new YamlParser(yamlInputStream);
            List<Map<String, Object>> configs =
                    ((List<Map<String, Object>>) fileConfig.getGenConfig());
            for (Map<String, Object> conf : configs) {
                if (conf.get("jmx") != null) {
                    System.out.println("found a jmx config");
                    return (Map<String, Object>) (conf.get("jmx"));
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("could not find your config file at " + yamlPath);
            return null;
        }

        System.out.println("could not find jmx config in " + yamlPath);
        return null;
    }
}

class BeanSpec {
    public int numDesiredBeans;
}

@Slf4j
public class App
{
    private static boolean started = false;
    final static String testDomain = "Bohnanza";
    public static void main( String[] args ) throws IOException, MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException
    {
        AppConfig config = new AppConfig();

        config.initConfig();

        JCommander jCommander = JCommander.newBuilder()
                .addObject(config)
                .build();

        try {
            int temp_bean_count = config.bean_count;
            String temp_domain = config.domain;
            jCommander.parse(args);
            if (config.bean_count != temp_bean_count){
                System.out.println("updated bean_count from cli: " + config.bean_count);
            }
            if (!config.domain.equals(temp_domain)){
                System.out.println("updated domain from cli: " + config.domain);
            }
            config.overrideFromEnv();
        } catch (Exception e) {
            jCommander.usage();
            System.exit(1);
        }
        System.out.println("final init: bean_count = " + config.bean_count + " , bean_domain = " + config.domain);

        InterruptibleRMISocketFactory customRMISocketFactory = new InterruptibleRMISocketFactory();
        // I don't think this call is actually important for jmx, the below 'env' param to JMXConnectorServerFactory is the important one
        RMISocketFactory.setSocketFactory(customRMISocketFactory);

        // Explicitly set RMI hostname to specified argument value
        System.setProperty("java.rmi.server.hostname", config.rmiHost);

        // Initialize RMI registry at same port as the jmx service
        LocateRegistry.createRegistry(config.rmiPort, null, customRMISocketFactory);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        MetricDAO mDao = new MetricDAO();
        mDao.runTickLoop();

        BeanManager bm = new BeanManager(mbs, mDao);

        //set up initial beans
        bm.setMBeanState(config.domain, config.bean_count);

        // Register single static bean under known domain
        ObjectName mbeanName = new ObjectName(testDomain + ":name=MyMBean");
        SingleAttributeMetricMBean mbean = new SingleAttributeMetric(mDao);
        mbs.registerMBean(mbean, mbeanName);

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
            Optional<List<FourAttributeMetric>> bs = bm.getMBeanState(domain);
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
            bm.setMBeanState(domain, beanSpec.numDesiredBeans);

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
