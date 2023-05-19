package org.datadog.misbehavingjmxserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;


@Slf4j
class InterceptibleSocketServer extends ServerSocket {
    private List<Socket> acceptedSockets;
    private AtomicBoolean closed;

    public InterceptibleSocketServer(int port) throws IOException {
        super(port);
        this.acceptedSockets = new ArrayList<>();
        this.closed = new AtomicBoolean(false);
    }

    public InterceptibleSocketServer(int port, int backlog) throws IOException {
        super(port, backlog);
        this.acceptedSockets = new ArrayList<>();
        this.closed = new AtomicBoolean(false);
    }

    public InterceptibleSocketServer(int port, int backlog, InetAddress bindAddr) throws IOException {
        super(port, backlog, bindAddr);
        this.acceptedSockets = new ArrayList<>();
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public Socket accept() throws IOException {
        Socket socket = super.accept();
        if (this.closed.get()) {
            socket.close();
            throw new IOException("JMX Network is closed, try again later");
        }
        log.debug("Accepted socket localPort {} port {} localAddress {} inetAddress {}", socket.getLocalPort(), socket.getPort(), socket.getLocalAddress(), socket.getInetAddress(), socket.getRemoteSocketAddress());
        acceptedSockets.add(socket);
        return socket;
    }
    
    public void setClosed(boolean closed) {
        this.closed.set(closed);
        log.debug("[{}] Socket server is now {}", this.toString(), closed);
    }

    public int closeAcceptedSockets() {
        int closed = 0;
        for (Socket socket : acceptedSockets) {
            try {
                log.debug("Closing socket localPort {} port {} localAddress {} inetAddress {}", socket.getLocalPort(), socket.getPort(), socket.getLocalAddress(), socket.getInetAddress(), socket.getRemoteSocketAddress());
                socket.close();
                closed++;
            } catch (IOException e) {
                log.error("Error closing socket ", e);
            }
        }
        return closed;
    }
}