package org.datadog.jmxfetch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;

public class SocketFactory extends RMISocketFactory {
    private final int timeout;

    SocketFactory(final int timeout) {
        this.timeout = timeout;
    }

    @Override
    public Socket createSocket(final String host, final int port) throws IOException {
        final Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeout);
        return socket;
    }

    @Override
    public ServerSocket createServerSocket(final int port) throws IOException {
        return new ServerSocket(port);
    }
}