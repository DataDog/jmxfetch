package org.datadog.supervisor;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

import io.javalin.*;

@Slf4j
class AppConfig {
    public int rmiPort;
    public int controlHttpPort;
    public String rmiInterface;

    public AppConfig() {
        String rmiPortStr = System.getenv("JJ_RMI_PORT");
        if (rmiPortStr != null) {
            this.rmiPort = Integer.parseInt(rmiPortStr);
        } else {
            this.rmiPort = 1099;
        }

        String controlHttpPortStr = System.getenv("JJ_CONTROL_HTTP_PORT");
        if (controlHttpPortStr != null) {
            this.controlHttpPort = Integer.parseInt(controlHttpPortStr);
        } else {
            this.controlHttpPort = 8080;
        }

        this.rmiInterface = System.getenv("JJ_RMI_INTERFACE");
        if (this.rmiInterface == null) {
            this.rmiInterface = "localhost";
        }
    }
}


@Slf4j
public class App {
    private static Process process = null;
    private static AtomicBoolean running = new AtomicBoolean(false);
    private static final String jmxServerEntrypoint = "org.datadog.misbehavingjmxserver.App";
    private static String selfJarPath;
    private static final int jmxServerHTTPControlPort = 44751;
    private static AppConfig config;

    public static void main(String[] args) throws IOException {
        App.config = new AppConfig();

        try {
            selfJarPath = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath();
        } catch (Exception e) {
            log.warn("Could not get self jar path");
            e.printStackTrace();
            System.exit(1);
        }


        Javalin app = Javalin.create();

        app.post("/start", ctx -> {
            // Start the process
            if (process != null) {
                ctx.result("JMX Server already running").status(400);
                return;
            }
            try {
                startJMXServer();
                ctx.result("JMX Server started").status(200);
            } catch (Exception e) {
                ctx.result("Error starting JMX server: " + e.getMessage()).status(500);
            }
        });

        app.post("/stop", ctx -> {
            // Stop the process
            if (process == null) {
                ctx.result("No process running").status(400);
                return;
            }
            process.destroyForcibly().waitFor();

            process = null;
            ctx.result("Process stopped").status(200);
        });

        app.post("/restart", ctx -> {
            // Restart the process
            if (process != null) {
                process.destroyForcibly().waitFor();
            }
            try {
                startJMXServer();
                ctx.result("Process restarted").status(200);
            } catch (Exception e) {
                ctx.result("Error restarting process: " + e.getMessage()).status(500);
            }
        });
        
        app.post("/config/set", ctx -> {
            if (ctx.formParam("rmiPort") != null) {
                App.config.rmiPort = Integer.parseInt(ctx.formParam("rmiPort"));
                log.info("Setting RMI port to {}. Post to /restart for changes to take effect.", App.config.rmiPort);
            }
        });

        // Forward all "/jmx" to jmx-failure-server control server
        app.post("/jmx/<endpoint>", ctx -> {
            // Get the endpoint from the request path
            String endpoint = ctx.pathParam("endpoint");
            // Build the URL for the endpoint
            String url = "http://localhost:" + jmxServerHTTPControlPort + "/" + endpoint;
            // Forward the request to the endpoint
            HttpResponse<String> response = Unirest.post(url)
                    .body(ctx.body())
                    .asString();
            // Return the response from the endpoint
            ctx.status(response.getStatus())
                .result(response.getBody());
        });
        
        log.info("Supervisor HTTP control interface at :{}", App.config.controlHttpPort);
        app.start(App.config.controlHttpPort);

        log.info("Starting JMX subprocess");
        startJMXServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                log.info("Stopping the sub-process....");
                try {
                    process.destroyForcibly().waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }));
    }
    static void startJMXServer() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("java",
          "-cp",
          selfJarPath,
          jmxServerEntrypoint,
          "--control-port",
          "" + jmxServerHTTPControlPort,
          "--rmi-port",
          "" + App.config.rmiPort,
          "--rmi-interface",
          "" + App.config.rmiInterface
          );
        pb.inheritIO();
        process = pb.start();
        running.set(true);
    }
}
