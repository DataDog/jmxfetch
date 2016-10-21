/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.datadog.jmxfetch;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * A sample gRPC server that serve the ServiceDiscovery (see route_guide.proto) service.
 */
public class ServiceDiscoveryServer {
    private static final Logger logger = Logger.getLogger(ServiceDiscoveryServer.class.getName());

    private final int port;
    private final Server server;

    public ServiceDiscoveryServer(int port, App app) throws IOException {
        this(ServerBuilder.forPort(port), port, app);
    }

    /** Create a ServiceDiscovery server using serverBuilder as a base and features as data. */
    public ServiceDiscoveryServer(ServerBuilder<?> serverBuilder, int port, App app) {
        this.port = port;
        server = serverBuilder.addService(new ServiceDiscoveryService(app))
            .build();
    }

    /** Start serving requests. */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Our implementation of ServiceDiscovery service.
     *
     * <p>See route_guide.proto for details of the methods.
     */
    private static class ServiceDiscoveryService extends ServiceDiscoveryGrpc.ServiceDiscoveryImplBase {
        private final App app;

        ServiceDiscoveryService(App app) {
            this.app = app;
        }

        @Override
        public void setConfig(SDConfig request, StreamObserver<Confirmation> responseObserver) {
            responseObserver.onNext(setSDConfig(request));
            responseObserver.onCompleted();
        }

        private Confirmation setSDConfig(SDConfig config) {
            InputStream stream = new ByteArrayInputStream(config.getConfig().getBytes(StandardCharsets.UTF_8));
            YamlParser yaml = new YamlParser(stream);

            boolean applied = app.addConfig(config.getName(), yaml);
            if (applied) {
                logger.info("Configuration was successfully applied for " + config.getName());
            } else {
                logger.info("Configuration failed for " + config.getName());
            }
            return Confirmation.newBuilder().setSuccess(applied).build();
        }
    }
}
