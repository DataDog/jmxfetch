package org.datadog.jmxfetch;

public class AppHook extends Thread {

    private App app;

    public AppHook(App app) {
        this.app = app;
    }

    @Override
	public void run(){
        app.stop();
	}

}
