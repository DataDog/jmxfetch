package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;

public class JmxfetchRmiClientSocketFactory implements RMIClientSocketFactory {
    private final int timeoutMs;
    private final int connectionTimeoutMs;
    private final RMIClientSocketFactory factory;

    /**
     * JmxfetchRmiClientSocketFactory constructor with socket timeout (milliseconds), a socket
     * connection timeout (millisecondes) and a flag to enable/disable SSL.
     */
    public JmxfetchRmiClientSocketFactory(
            final int timeoutMs, final int connectionTimeoutMs,final boolean ssl) {
        this.timeoutMs = timeoutMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.factory = 
            ssl ? new SslRMIClientSocketFactory() : RMISocketFactory.getDefaultSocketFactory();
    }

    @Override
    public Socket createSocket(final String host, final int port) throws IOException {
        Socket socket = null;
        final AsyncSocketFactory f = new AsyncSocketFactory(factory, host, port);
        final Thread t = new Thread(f);
        try {
            synchronized (f) {
                t.start();
                try {
                    long now = System.currentTimeMillis();
                    final long until = now + connectionTimeoutMs;
                    do {
                        f.wait(until - now);
                        socket = getSocketFromFactory(f);
                        if (socket != null) {
                            break;
                        }
                        now = System.currentTimeMillis();
                    } while (now < until);
                } catch (final InterruptedException e) {
                    throw new InterruptedIOException(
                        "interrupted during socket connection attempt");
                }
            }
        } catch (IOException e) {
            /* will close socket if it ever connects */
            f.clean();
            throw e;
        }
        if (socket == null) {
            throw new IOException("connect timed out: " + host + ":" + port);
        }
        socket.setSoTimeout(timeoutMs);
        socket.setSoLinger(false, 0);
        return socket;
    }

    Socket getSocketFromFactory(final AsyncSocketFactory factory) throws IOException {
        final Exception e = factory.getException();
        if (e != null) {
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
        private Exception exception = null;
        private Socket socket = null;
        private boolean shouldClose = false;
        
        AsyncSocketFactory(
                final RMIClientSocketFactory factory,final String host, final int port) {
            this.factory = factory;
            this.host = host;
            this.port = port;
        }

        public void run() {
            try {
                final Socket s = factory.createSocket(host, port);
                synchronized (this) {
                    socket = s;
                    notify();
                }
                synchronized (this) {
                    if (shouldClose) {
                        try {
                            s.close();
                        } catch (final IOException e) { /* empty on purpose */ }
                    }
                }
            } catch (final Exception e) {
                synchronized (this) {
                    exception = e;
                    notify();
                }
            }
        }

        synchronized void clean() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException e) { /* empty on purpose */ }
            }
            shouldClose = true;
        }

        private synchronized Exception getException() {
            return exception;
        }

        private synchronized Socket getSocket() {
            return socket;
        }
    }
}
