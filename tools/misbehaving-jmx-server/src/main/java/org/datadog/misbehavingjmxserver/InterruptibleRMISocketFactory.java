package org.datadog.misbehavingjmxserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;


@Slf4j
class InterruptibleRMISocketFactory extends RMISocketFactory {
    private List<InterceptibleSocketServer> socketServers;
    private List<Socket> sockets;
    private AtomicBoolean closed;

    public InterruptibleRMISocketFactory() {
        this.socketServers = new ArrayList<>();
        this.sockets = new ArrayList<>();
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public synchronized ServerSocket createServerSocket(int port) throws IOException {
        if (this.closed.get()) {
            log.warn("Denied request for a serverSocket on :{} (network closed)", port);
            throw new IOException("JMX Network is closed, sorry, try again later.");
        }
        InterceptibleSocketServer serverSocket = new InterceptibleSocketServer(port);
        socketServers.add(serverSocket);
        log.debug("Creating serverSocket on :{}", port);
        return serverSocket;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        if (this.closed.get()) {
            log.warn("Denied request for a socket to {}:{} (network closed)", host, port);
            throw new IOException("JMX Network is closed, sorry, try again later.");
        }
        Socket socket = new Socket(host, port);
        sockets.add(socket);
        log.debug("Creating socket to {}:{}, now have {} sockets", host, port, sockets.size());
        return socket;
    }

    public void setClosed(boolean closed) {
        this.closed.set(closed);
        for (InterceptibleSocketServer socketServer : this.socketServers) {
            socketServer.setClosed(closed);
        }
    }

    public void closeClientSockets() {
        int closed = 0;
        for (Socket socket : this.sockets) {
            try {
                socket.close();
                closed++;
            } catch (IOException e) {
                log.error("Error closing socket ", e);
            }
        }
        log.info("Closed {} client sockets", closed);
        this.sockets.clear();
    }

    public void closeServerSockets() {
        int closed = 0;
        for (InterceptibleSocketServer socketServer : this.socketServers) {
            closed += socketServer.closeAcceptedSockets();
            // Closing the socketServer itself fubars JMX
            // Only close 'accepted' sockets from the socketservers
        }
        log.info("Closed {} accepted sockets", closed);
    }

    public void closeAllSockets() {
        this.closeClientSockets();
        this.closeServerSockets();
    }
}
