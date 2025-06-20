package org.datadog.jmxfetch.util;

import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@Slf4j
public class JmxfetchRmiClientSocketFactoryTest {

    static interface MyService extends Remote {
        String pause(int sleepMs) throws RemoteException;
    }
    
    static class MyServiceImpl extends UnicastRemoteObject implements MyService{

        protected MyServiceImpl() throws RemoteException {
            super(0);
        }

        protected MyServiceImpl(RMIClientSocketFactory csf,
                                RMIServerSocketFactory ssf) throws RemoteException {
            super(0, csf, ssf);
        }
    
        public String pause(int sleepMs) throws RemoteException {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "OK";
        }
    }

    private int countTestThreads(String namePrefix) {;
        int count = 0;
        int totalCount = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            totalCount++;
            if (t.getName().contains(namePrefix)) {
                log.warn("threadSet : " + t.getName());
                count++;
            }
        }
        log.info("countTestThreads : " + count);
        log.info("totalCount : " + totalCount);
        log.info("Thread.activeCount : " + Thread.activeCount());
        log.info("--------------------------------");
        return count;
    }
    

    static class InterruptibleRMISocketFactory extends RMISocketFactory implements Serializable { 
        private static final long serialVersionUID = 1L;
        private volatile boolean ignoreCall = true;

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'createSocket'");
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            final ServerSocket serverSocket = new ServerSocket(port) {
                @Override
                public Socket accept() throws IOException {
                    // First call, return a socket as needed by the RMI registry
                    if (ignoreCall) {
                        ignoreCall = false;
                        return super.accept();
                    }
                    log.info("Simulating a timeout");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    throw new IOException("Timeout");
                }
            };
            return serverSocket;
        }
    }

    @Test
    public void testNoThreadLeakOnNullSocketFromFactory() throws Exception {
        // Given RMIClientSocketFactory returns null
        final AtomicInteger count = new AtomicInteger(0);
        final JmxfetchRmiClientSocketFactory factory = new JmxfetchRmiClientSocketFactory(100, 200, new RMIClientSocketFactory() {
            
            @Override
            public Socket createSocket(String host, int port) throws IOException {
                count.incrementAndGet();
                return null;
            }
        });
        // And a registry and a service setup
        final int registryPort = findAvailablePort();
        final Registry registry = LocateRegistry.createRegistry(registryPort);
        final MyService service = new MyServiceImpl();
        
        try {
            registry.rebind("MyService", service);
            // And threads before
            final int beforeRMI = countTestThreads("RMI");

            final Registry r = LocateRegistry.getRegistry("localhost", registryPort, factory);
            for (int i = 0; i < 10; i++) {
                try {
                    final MyService s = (MyService)  r.lookup("MyService");
                    log.info("Called pause : " + s.pause(200));
                } catch (IOException e) {
                    // fail("Exception : " + e.getMessage());
                    log.info("Exception : " + e.getMessage());
                }
            }
            final int afterRMI = countTestThreads("RMI");
            assertEquals(beforeRMI, afterRMI);
            assertTrue("Count should be greater than 0", count.get() > 0);
            log.info("Count : " + count.get());

        } finally {
            UnicastRemoteObject.unexportObject(service, true);
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }

    @Test
    public void testNoThreadLeakOnTimeout() throws Exception {
        final int registryPort = findAvailablePort();
        log.info("Setting up registry on port : " + registryPort);
        final JmxfetchRmiClientSocketFactory factory = new JmxfetchRmiClientSocketFactory(100, 100, false);
        final InterruptibleRMISocketFactory interruptibleFactory = new InterruptibleRMISocketFactory();
        final Registry registry = LocateRegistry.createRegistry(registryPort, factory, new RMIServerSocketFactory() {
            @Override
            public ServerSocket createServerSocket(int port) throws IOException {
                return new ServerSocket(port);
            }
        });
        final MyService service = new MyServiceImpl(null, interruptibleFactory);

        try {
            registry.rebind("MyService", service);
            final int before = Thread.activeCount();
            final int beforeRMI = countTestThreads("RMI");
            log.info("---------Running--------");
            // Use reflection to inject our custom factory
            final Registry r = LocateRegistry.getRegistry("localhost", registryPort, factory);
            
            try {
                final MyService s = (MyService)  r.lookup("MyService");
                log.info("Called pause : " + s.pause(200));
                registry.unbind("MyService");
            } catch (IOException e) {
                // fail("Exception : " + e.getMessage());
            }
            // Wait a bit for threads to terminate
            Thread.sleep(1500);

            int after = Thread.activeCount();

            // Allow a small margin for unrelated threads
            log.info("Before : " + before + " After : " + after);
            assertTrue("Thread leak detected", after <= before + 1);
            
            log.info("---------Assert--------");
            final int afterRMI = countTestThreads("RMI");
            assertEquals(beforeRMI, afterRMI);
        } finally {
            UnicastRemoteObject.unexportObject(service, true);
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }

    private int findAvailablePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

