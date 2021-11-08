/*
 * Copyright 2021 Eclipse.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

/**
 *
 * @author jtalbut
 */
public class Proxy {
  
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
      proxyClient.connect(dstPort, "localhost", ar -> {
        if (ar.succeeded()) {
          NetSocket clientSocket = ar.result();
          serverSocket.handler(clientSocket::write);
          serverSocket.exceptionHandler(err -> serverSocket.close());
          serverSocket.closeHandler(v -> clientSocket.close());
          clientSocket.handler(serverSocket::write);
          clientSocket.exceptionHandler(err -> clientSocket.close());
          clientSocket.closeHandler(v -> serverSocket.close());
          serverSocket.resume();
        } else {
          serverSocket.close();;
        }
      });
    }).listen(srcPort, "localhost", ar -> {
      if (ar.succeeded()) {
        latch.complete(null);
      } else {
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
