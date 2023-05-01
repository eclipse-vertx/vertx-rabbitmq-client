/*
  * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
  *
  * This program and the accompanying materials are made available under the
  * terms of the Eclipse Public License 2.0 which is available at
  * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
  * which is available at https://www.apache.org/licenses/LICENSE-2.0.
  *
  * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
  */
package io.vertx.rabbitmq;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class Proxy {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(Proxy.class);
  
  private final Vertx vertx;
  private final int srcPort;
  private final int dstPort;

  private NetServer proxyServer;
  private NetClient proxyClient;
  
  public Proxy(Vertx vertx, int srcPort, int dstPort) {
    this.vertx = vertx;
    this.srcPort = srcPort;
    this.dstPort = dstPort;
  }
  
  public Proxy(Vertx vertx, int dstPort) throws IOException {
    this.vertx = vertx;
    this.dstPort = dstPort;
    this.srcPort = findPort();
  }

  public int getProxyPort() {
    return srcPort;
  }

  private static int findPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();     
    }    
  }
  
  public void startProxy() throws Exception {
    CompletableFuture<Void> latch = new CompletableFuture<>();
    NetClientOptions clientOptions = new NetClientOptions();

    proxyClient = vertx.createNetClient(clientOptions);

    proxyServer = vertx.createNetServer().connectHandler(serverSocket -> {
      serverSocket.pause();
      logger.warn("Proxy server connected: {} -> {}", serverSocket.remoteAddress(), serverSocket.localAddress());
      proxyClient.connect(dstPort, "localhost", ar -> {
        if (ar.succeeded()) {
          NetSocket clientSocket = ar.result();
          logger.warn("Proxy client connected: {} -> {}", clientSocket.localAddress(), clientSocket.remoteAddress());
          serverSocket.handler(clientSocket::write);
          serverSocket.exceptionHandler(err -> {
            logger.warn("Proxy server exception: ", err);
            serverSocket.close();
          });
          serverSocket.closeHandler(v -> clientSocket.close());
          clientSocket.handler(serverSocket::write);
          clientSocket.exceptionHandler(err -> {
            logger.warn("Proxy client exception: ", err);
            clientSocket.close();
          });
          clientSocket.closeHandler(v -> serverSocket.close());
          serverSocket.resume();
        } else {
          logger.warn("Proxy client not connected: {}", ar.cause());
          serverSocket.close();;
        }
      });
    }).listen(srcPort, "localhost", ar -> {
      if (ar.succeeded()) {
        logger.warn("Proxy started from {} to {}", srcPort, dstPort);
        latch.complete(null);
      } else {
        logger.warn("Proxy failed from {} to {}: ", srcPort, dstPort, ar.cause());
        latch.completeExceptionally(ar.cause());
      }
    });
    latch.get(10, TimeUnit.SECONDS);    
  }
  
  public void stopProxy() {
    if (proxyServer != null) {
      proxyServer.close();
    }
    if (proxyClient != null) {
      proxyClient.close();
    }    
  }
  
}
