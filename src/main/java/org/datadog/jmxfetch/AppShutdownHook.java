package org.datadog.jmxfetch;

public class AppShutdownHook extends Thread {

    private App app;

    public AppShutdownHook(App app) {
        this.app = app;
    }

    @Override
    public void run() {
        app.stop();
    }
}
