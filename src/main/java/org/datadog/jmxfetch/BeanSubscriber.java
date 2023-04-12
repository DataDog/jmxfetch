package org.datadog.jmxfetch;

import java.util.concurrent.CompletableFuture;
import java.util.List;

public class BeanSubscriber implements Runnable {
    private List<String> beanScopes;
    private Connection connection;
    private BeanListener listener;
    public CompletableFuture<Boolean> subscriptionSuccessful;

    BeanSubscriber(List<String> beanScopes, Connection connection, BeanListener listener) {
        this.beanScopes = beanScopes;
        this.connection = connection;
        this.listener = listener;
        this.subscriptionSuccessful = new CompletableFuture<Boolean>();
    }

    public void run() {
        try {
            connection.subscribeToBeanScopes(beanScopes, this.listener);
            this.subscriptionSuccessful.complete(true);

            Thread.currentThread().join();
        } catch (Exception e) {
            this.subscriptionSuccessful.complete(false);
        }
    }
}