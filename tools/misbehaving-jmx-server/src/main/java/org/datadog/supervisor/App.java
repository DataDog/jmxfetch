package org.datadog.supervisor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

import io.javalin.*;

class AppConfig {
    public int controlHttpPort;
    public String rmiHostname;

    public AppConfig() {
        String controlHttpPortStr = System.getenv("SUPERVISOR_PORT");
        this.rmiHostname = null;
        if (controlHttpPortStr != null) {
            this.controlHttpPort = Integer.parseInt(controlHttpPortStr);
        } else {
            this.controlHttpPort = 8088;
        }
    }
}

@Slf4j
public class App {
    private static Process process = null;
    private static AtomicBoolean running = new AtomicBoolean(false);
    private static final String jmxServerEntrypoint = "org.datadog.misbehavingjmxserver.App";
    private static String selfJarPath;
    private static AppConfig config;

    public static void main(String[] args) throws IOException {
        App.config = new AppConfig();

        try {
            selfJarPath = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())
                    .getAbsolutePath();
        } catch (Exception e) {
            log.warn("Could not get self jar path");
            e.printStackTrace();
            System.exit(1);
        }

        Javalin app = Javalin.create();

        app.post("/config/", ctx -> {
            if (ctx.formParam("rmiHostname") != null) {
                App.config.rmiHostname = ctx.formParam("rmiHostname");
                log.info("Setting RMI Hostname to {}. Restarting now...", App.config.rmiHostname);
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
            }
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
        ProcessBuilder pb;
        if (App.config.rmiHostname != null) {
            pb = new ProcessBuilder("java",
                    "-cp",
                    selfJarPath,
                    jmxServerEntrypoint,
                    "--rmi-host",
                    App.config.rmiHostname);
        } else {
            pb = new ProcessBuilder("java",
                    "-cp",
                    selfJarPath,
                    jmxServerEntrypoint);
        }
        pb.redirectErrorStream(true);
        process = pb.start();
        running.set(true);

        // Read the output of the child process
        InputStream inputStream = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);

            // Check if the desired string is found
            if (line.contains("IAMREADY")) {
                // When subprocess is ready, return from this, but continue re-printing subprocess output
                new Thread(() -> {
                    String l;
                    try {
                        while ((l = reader.readLine()) != null) {
                            System.out.println(l);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
                log.info("JMX server has started");
                return;
            }
        }

    }
}
