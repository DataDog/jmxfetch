package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import lombok.extern.java.Log;

@Log
public class JmxfetchRmiClientSocketFactory implements RMIClientSocketFactory {

    /* For autonumbering RMIClientSocketFactory threads. */
    private static int threadInitNumber;

    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    private final int timeoutMs;
    private final int connectionTimeoutMs;
    private final RMIClientSocketFactory factory;

    /**
     * JmxfetchRmiClientSocketFactory constructor with socket timeout (milliseconds), a socket
     * connection timeout (milliseconds) and a flag to enable/disable SSL.
     * @param timeoutMs socket timeout (milliseconds)
     * @param connectionTimeoutMs socket connection timeout (milliseconds)
     * @param ssl flag to enable/disable SSL
     */
    public JmxfetchRmiClientSocketFactory(
        final int timeoutMs, final int connectionTimeoutMs, final boolean ssl) {
        this(timeoutMs,
            connectionTimeoutMs,
            ssl ? new SslRMIClientSocketFactory() : RMISocketFactory.getDefaultSocketFactory());
    }

    /**
     * JmxfetchRmiClientSocketFactory constructor with socket timeout (milliseconds), a socket
     * connection timeout (milliseconds) and a RMIClientSocketFactory.
     * @param timeoutMs socket timeout (milliseconds)
     * @param connectionTimeoutMs socket connection timeout (milliseconds)
     * @param factory RMIClientSocketFactory
     */ 
    public JmxfetchRmiClientSocketFactory(
        final int timeoutMs, final int connectionTimeoutMs, final RMIClientSocketFactory factory) {
        this.timeoutMs = timeoutMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.factory = factory;
    }

    @Override
    public Socket createSocket(final String host, final int port) throws IOException {
        log.info("Creating socket for " + host + ":" + port);
        Socket socket = null;
        final AsyncSocketFactory f = new AsyncSocketFactory(factory, host, port);
        final Thread t = new Thread(f, 
            String.format("JmxfetchRmiClientSocketFactory-[%s:%d]-%d", 
                host, port, nextThreadNum()));
        try {
            synchronized (f.lock) {
                t.start();
                try {
                    long now = System.currentTimeMillis();
                    final long until = now + this.connectionTimeoutMs;
                    do {
                        f.lock.wait(until - now);
                        socket = getSocketFromFactory(f);
                        if (socket != null) {
                            break;
                        }
                        now = System.currentTimeMillis();
                    } while (now < until);
                } catch (final InterruptedException e) {
                    log.warning("Interrupted while waiting for socket: " + e.getMessage());
                    throw new InterruptedIOException(
                        "interrupted during socket connection attempt");
                }
            }
        } catch (IOException e) {
            log.warning("IOException while creating socket: " + e.getMessage());

            /* will close socket if it ever connects */
            f.clean();
            t.interrupt();
            throw e;
        } finally {
            log.info("Finally block for " + host + ":" + port + " with thread: " + t.getName() + " and isAlive: " + t.isAlive());
            if (socket != null) {
                log.info("Socket is not null for " + host + ":" + port);
            } else {
                log.info("Socket is null for " + host + ":" + port  );
            }
            if (t.isAlive()) {
                try {
                    t.join(1000);
                } catch (InterruptedException e) {
                    log.warning("Interrupted while waiting for thread to join for " + host + ":" + port);
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (socket == null) {
            log.warning("Connect timed out: " + host + ":" + port);
            throw new IOException("connect timed out: " + host + ":" + port);
        }
        socket.setSoTimeout(timeoutMs);
        socket.setSoLinger(false, 0);
        return socket;
    }

    Socket getSocketFromFactory(final AsyncSocketFactory factory) throws IOException {
        final Exception e = factory.getException();
        if (e != null) {
            log.warning("Exception in getSocketFromFactory: " + e.getMessage());
            e.fillInStackTrace();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new Error("unforeseen checked exception" + e.toString());
            }
        }
        return factory.getSocket();
    }

    private class AsyncSocketFactory implements Runnable {
        private final RMIClientSocketFactory factory;
        private final String host;
        private final int port;
        
        private volatile Exception exception;
        private volatile Socket socket;
        private volatile boolean shouldClose;
        private final Object lock = new Object();
        
        AsyncSocketFactory(
                final RMIClientSocketFactory factory,final String host, final int port) {
            this.factory = factory;
            this.host = host;
            this.port = port;
        }

        public void run() {
            log.info("Running AsyncSocketFactory for " + host + ":" + port);
            try {
                final Socket s = factory.createSocket(host, port);
                synchronized (this.lock) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Thread was interrupted, close socket and exit
                        try {
                            log.info("Closing socket due to thread interruption for " + host + ":" + port);
                            s.close();
                        } catch (final IOException e) { /* empty on purpose */ }
                        return;
                    }
                    this.socket = s;
                    this.lock.notify();
                }
                synchronized (this.lock) {
                    if (this.shouldClose) {
                        try {
                            log.info("Closing socket as shouldClose is true for " + host + ":" + port);
                            this.socket.close();
                        } catch (final IOException e) { /* empty on purpose */ }
                    }
                }
            } catch (final Exception e) {
                log.warning("Exception in AsyncSocketFactory: " + e.getMessage() + " for " + host + ":" + port);
                synchronized (this.lock) {
                    this.exception = e;
                    this.lock.notify();
                }
                this.clean();
            }
            log.info("AsyncSocketFactory finished for " + host + ":" + port);
        }

        void clean() {
            synchronized (this.lock) {
                log.info("Cleaning up socket for " + host + ":" + port);
                if (this.socket != null) {
                    try {
                        this.socket.close();
                        log.info("Socket closed for " + host + ":" + port);
                    } catch (final IOException e) { /* empty on purpose */ }
                }
                this.shouldClose = true;
            }
        }

        private Exception getException() {
            synchronized (this.lock) {
                return this.exception;
            }
        }

        private Socket getSocket() {
            synchronized (this.lock) {
                return this.socket;
            }
        }
    }
}
