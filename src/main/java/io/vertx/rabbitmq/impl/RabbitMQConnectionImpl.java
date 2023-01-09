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
package io.vertx.rabbitmq.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.rabbitmq.AsyncHandler;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQMessage;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import io.vertx.rabbitmq.impl.codecs.RabbitMQByteArrayMessageCodec;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;


/**
 *
 * @author jtalbut
 */
public class RabbitMQConnectionImpl implements RabbitMQConnection, ShutdownListener {
  
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQConnectionImpl.class);
  
  private final Vertx vertx;
  private final Context context;
  private final RabbitMQOptions config;
  private String connectionName;
  
  private boolean connectedAtLeastOnce;
  private boolean established;
  private final Object connectingPromiseLock = new Object();
  private volatile Future<Connection> connectingFuture;
  private final Object connectionLock = new Object();
  private volatile Connection connection;  

  private int reconnectCount;
  private long lastConnectedInstance = -1;
  
  private final AtomicLong connectCount = new AtomicLong();
  private volatile boolean closed;
  
  public RabbitMQConnectionImpl(Vertx vertx, RabbitMQOptions config) {
    this.vertx = vertx;
    this.context = vertx.getOrCreateContext();    
    this.config = new RabbitMQOptions(config);
  }

  @Override
  public long getConnectionInstance() {
    return connectCount.get();
  }
  
  public boolean isEstablished() {
    return established;
  }

  public int getReconnectCount() {
    return reconnectCount;
  }

  @Override
  public String getConnectionName() {
    return connectionName;
  }
  
  private Connection rawConnect() throws Exception {
    List<Address> addresses = null;
    ConnectionFactory cf = new ConnectionFactory();
    String uriString = config.getUri();
    
    String username = config.getUser();
    String password = config.getPassword();
    String vhost = config.getVirtualHost();
    
    // Use uri if set, otherwise support individual connection parameters    
    if (uriString != null) {      
      logger.debug("Attempting connection to " + uriString);
      URI uri = null;
      try {
        uri = new URI(uriString);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid rabbitmq connection uri ", e);
      }
      if ("amqps".equals(uri.getScheme())) {
        configureTlsProtocol(cf);
      }
      cf.setUri(uri);
        
      // Override the user/pass/vhost values, but only if they are set in the URI and are NOT set in the config
      String rawUserInfo = uri.getRawUserInfo();
      if (rawUserInfo != null && !rawUserInfo.isEmpty()) {
        String parts[] = rawUserInfo.split(":", 2);
        if (RabbitMQOptions.DEFAULT_USER.equals(username)) {
          username = URLDecoder.decode(parts[0], "UTF-8");
        }
        if (parts.length > 1 && RabbitMQOptions.DEFAULT_PASSWORD.equals(password)) {
          password = URLDecoder.decode(parts[1], "UTF-8");
        }
      }
      String rawPath = uri.getRawPath();      
      if (rawPath != null && !rawPath.isEmpty() && RabbitMQOptions.DEFAULT_VIRTUAL_HOST.equals(vhost)) {
        if (rawPath.startsWith("/")) {
          rawPath = rawPath.substring(1);
        }
        vhost = URLDecoder.decode(rawPath, "UTF-8");
      }
    } else {
      addresses = config.getAddresses().isEmpty()
        ? Collections.singletonList(new Address(config.getHost(), config.getPort()))
        : config.getAddresses();
      logger.debug("Attempting connection to " + addresses);
    }
    // Note that this intentionally allows the configuration to override properties from the URL.
    if (config.getUser() != null && !config.getUser().isEmpty()) {
      cf.setUsername(username);
    }
    if (config.getPassword() != null && !config.getPassword().isEmpty()) {
      cf.setPassword(password);
    }
    if (config.getVirtualHost() != null && !config.getVirtualHost().isEmpty()) {
      cf.setVirtualHost(vhost);
    }
    if (config.isSsl()) {
      configureTlsProtocol(cf);
    }
    if (addresses != null) {
      logger.info(
              (connectCount.get() > 0 ? "Rec" : "C")
              + "onnecting to amqp"
              + (cf.isSSL() ? "s" : "")
              + "://"
              + cf.getUsername()
              + "@"
              + (addresses.size() == 1 ? addresses.get(0) : addresses)
              + "/"
              + URLEncoder.encode(cf.getVirtualHost(), "UTF-8")
      );
    } else {
      logger.info(
              (connectCount.get() > 0 ? "Rec" : "C")
              + "onnecting to amqp"
              + (cf.isSSL() ? "s" : "")
              + "://"
              + cf.getUsername()
              + "@"
              + cf.getHost()
              + ":"
              + cf.getPort()
              + "/"
              + URLEncoder.encode(cf.getVirtualHost(), "UTF-8")
      );
    }
    
    cf.setConnectionTimeout(config.getConnectionTimeout());
    cf.setShutdownTimeout(config.getShutdownTimeout());
    cf.setWorkPoolTimeout(config.getWorkPoolTimeout());
    cf.setRequestedHeartbeat(config.getRequestedHeartbeat());
    cf.setHandshakeTimeout(config.getHandshakeTimeout());
    cf.setRequestedChannelMax(config.getRequestedChannelMax());
    cf.setRequestedFrameMax(config.getRequestedFrameMax());
    cf.setNetworkRecoveryInterval(config.getNetworkRecoveryInterval());
    cf.setAutomaticRecoveryEnabled(config.isAutomaticRecoveryEnabled());
    if (config.getTopologyRecoveryEnabled() == null) {
      cf.setTopologyRecoveryEnabled(config.isAutomaticRecoveryEnabled());      
    } else {
      cf.setTopologyRecoveryEnabled(config.getTopologyRecoveryEnabled());
    }

    cf.useNio();

    cf.setChannelRpcTimeout(config.getChannelRpcTimeout());
    cf.setChannelShouldCheckRpcResponseType(config.isChannelShouldCheckRpcResponseType());
    cf.setClientProperties(config.getClientProperties());
    if (config.getConnectionRecoveryTriggeringCondition() != null) {
      cf.setConnectionRecoveryTriggeringCondition(config.getConnectionRecoveryTriggeringCondition());
    }
    if (config.getCredentialsProvider() != null) {
      cf.setCredentialsProvider(config.getCredentialsProvider());
    }
    if (config.getCredentialsRefreshService() != null) {
      cf.setCredentialsRefreshService(config.getCredentialsRefreshService());
    }
    if (config.getErrorOnWriteListener() != null) {
      cf.setErrorOnWriteListener(config.getErrorOnWriteListener());
    }
    if (config.getExceptionHandler() != null) {
      cf.setExceptionHandler(config.getExceptionHandler());
    }
    if (config.getHeartbeatExecutor() != null) {
      cf.setHeartbeatExecutor(config.getHeartbeatExecutor());
    }
    if (config.getMetricsCollector() != null) {
      cf.setMetricsCollector(config.getMetricsCollector());
    }
    if (config.getNioParams() != null) {
      cf.setNioParams(config.getNioParams());
    }
    if (config.getRecoveredQueueNameSupplier() != null) {
      cf.setRecoveredQueueNameSupplier(config.getRecoveredQueueNameSupplier());
    }
    if (config.getRecoveryDelayHandler() != null) {
      cf.setRecoveryDelayHandler(config.getRecoveryDelayHandler());
    }
    if (config.getSaslConfig() != null) {
      cf.setSaslConfig(config.getSaslConfig());
    }
    if (config.getSharedExecutor() != null) {
      cf.setSharedExecutor(config.getSharedExecutor());
    }
    if (config.getShutdownExecutor() != null) {
      cf.setShutdownExecutor(config.getShutdownExecutor());
    }
    if (config.getSocketConfigurator() != null) {
      cf.setSocketConfigurator(config.getSocketConfigurator());
    }
    if (config.getSocketFactory() != null) {
      cf.setSocketFactory(config.getSocketFactory());
    }
    if (config.getSslContextFactory() != null) {
      cf.setSslContextFactory(config.getSslContextFactory());
    }    
    if (config.getThreadFactory() != null) {
      cf.setThreadFactory(config.getThreadFactory());
    }    
    if (config.getTopologyRecoveryExecutor() != null) {
      cf.setTopologyRecoveryExecutor(config.getTopologyRecoveryExecutor());
    }    
    if (config.getTopologyRecoveryFilter() != null) {
      cf.setTopologyRecoveryFilter(config.getTopologyRecoveryFilter());
    }    
    if (config.getTopologyRecoveryRetryHandler() != null) {
      cf.setTopologyRecoveryRetryHandler(config.getTopologyRecoveryRetryHandler());
    }    
    if (config.getTrafficListener() != null) {
      cf.setTrafficListener(config.getTrafficListener());
    }    

    Connection conn = addresses == null
           ? cf.newConnection(config.getConnectionName())
           : cf.newConnection(addresses, config.getConnectionName());
    lastConnectedInstance = connectCount.incrementAndGet();
    connectionName = config.getConnectionName();
    conn.setId(Long.toString(lastConnectedInstance));
    logger.info("Established connection to amqp"
            + (cf.isSSL() ? "s" : "")
            + "://"
            + cf.getUsername()
            + "@"
            + conn.getAddress()
            + "/"
            + URLEncoder.encode(cf.getVirtualHost(), "UTF-8")
    );      
    conn.addShutdownListener(this);
    conn.addBlockedListener(new BlockedListener() {
      @Override
      public void handleBlocked(String string) throws IOException {
        logger.info("Blocked: " + string);
      }

      @Override
      public void handleUnblocked() throws IOException {
        logger.info("Unblocked");
      }
    });
    
    return conn;
  }
  
  private void configureTlsProtocol(ConnectionFactory cf) throws Exception {
    if (config.isTrustAll()) {
      cf.useSslProtocol();      
    } else {
      String secureTransportProtocol = config.getSecureTransportProtocol();
      
      SSLContext sslContext = SSLContext.getInstance(secureTransportProtocol);
      JksOptions kso = config.getKeyStoreOptions();
      KeyManager km[] = null;
      if (kso != null) {
        KeyManagerFactory kmf = kso.getKeyManagerFactory(vertx);
        if (kmf != null) {
          km = kmf.getKeyManagers();
        }        
      }
      JksOptions tso = config.getTrustStoreOptions();
      TrustManager tm[] = null;
      if (tso != null) {
        TrustManagerFactory tmf = tso.getTrustManagerFactory(vertx);
        if (tmf  != null) {
          tm = tmf.getTrustManagers();
        }
      }
      sslContext.init(km, tm, null);      
      cf.useSslProtocol(sslContext);
      if (config.isTlsHostnameVerification()) {
        cf.enableHostnameVerification();
      }
    }
  }

  @Override
  public void shutdownCompleted(ShutdownSignalException cause) {
    logger.info("Connection " + ((Connection) cause.getReference()).getId() + " Shutdown: " + cause.getMessage());
  }
  
  protected boolean shouldRetryConnection() {
    if (closed) {
      logger.debug("Not retrying connection because close has been called");
      return false;      
    }
    if (config.getReconnectInterval() <= 0) {
      logger.debug("Not retrying connection because reconnect internal (" + config.getReconnectInterval() + ") <= 0");
      return false;      
    }
    if (connectedAtLeastOnce) {
      if (config.getReconnectAttempts() < 0) {
        logger.debug("Retrying because reconnect limit (" + config.getReconnectAttempts() + ") < 0"); 
        ++reconnectCount;
        return true;
      } else if (config.getReconnectAttempts() > reconnectCount) {
        logger.debug("Retrying because reconnect count (" + reconnectCount + ") < limit (" + config.getReconnectAttempts() + ")"); 
        ++reconnectCount;
        return true;
      } else {
        logger.debug("Not retrying connection because reconnect count (" + reconnectCount + ") >= limit (" + config.getReconnectAttempts() + ")"); 
        return false;
      }
    } else {
      if (config.getInitialConnectAttempts() < 0) {
        logger.debug("Retrying because initial reconnect limit (" + config.getInitialConnectAttempts() + ") < 0"); 
        ++reconnectCount;
        return true;
      } else if (config.getInitialConnectAttempts() > reconnectCount) {
        logger.debug("Retrying because reconnect count (" + reconnectCount + ") < initial limit (" + config.getInitialConnectAttempts() + ")"); 
        ++reconnectCount;
        return true;
      } else {
        logger.debug("Not retrying connection because reconnect count (" + reconnectCount + ") >= initial limit (" + config.getInitialConnectAttempts() + ")"); 
        return false;
      }
    }        
  }
  
  public Future<RabbitMQConnection> connect() {
    return vertx.<Connection>executeBlocking(promise -> {
      connectBlocking(promise);
    }).map(c -> this);    
  }

  private Future<Connection> connectBlocking(Promise<Connection> promise) {
    try {
      synchronized(connectionLock) {
        if (connection == null || !connection.isOpen()) {
          connection = rawConnect();
          connectedAtLeastOnce = true;
        }
        promise.complete(connection);
      }
    } catch(Throwable ex) {
      logger.error("Failed to create connection: ", ex);
      if (shouldRetryConnection()) {
        vertx.setTimer(config.getReconnectInterval(), time -> {
          vertx.executeBlocking(p -> {
            connectBlocking(promise)
                    .onComplete(ar -> {
                      if (ar.succeeded()) {
                        p.complete();
                      } else {
                        p.fail(ar.cause());
                      }
                    });
          });
        });
      } else {
        promise.fail(ex);
      }
    }
    return promise.future();
  }  
  
  Future<Channel> openChannel(long lastInstance) {
    synchronized(connectingPromiseLock) {
      logger.debug("ConnectionFuture: " + connectingFuture + ", lastInstance: " + lastInstance + ", connectCount: " + connectCount.get() + ", closed: " + closed);
      if (((connectingFuture == null) || (lastInstance != this.connectCount.get())) && !closed) {
        synchronized(connectionLock) {       
          if (lastConnectedInstance != connectCount.get()) {
            reconnectCount = 0;
          }
        }
        Promise<Connection> connectingPromise = Promise.promise();
        connectingFuture = connectingPromise.future();
        context.executeBlocking(execPromise -> connectBlocking(connectingPromise).onComplete(ar -> {
          if (ar.succeeded()) {
            execPromise.complete();
          } else {
            execPromise.fail(ar.cause());
          }
        }));
      }
      return connectingFuture
              .compose(conn -> {
                return context.executeBlocking(promise -> {
                  try {                    
                    promise.complete(conn.createChannel());
                  } catch(AlreadyClosedException | IOException ex) {
                    logger.error("Failed to create channel: ", ex);
                    if (shouldRetryConnection()) {
                      synchronized(connectingPromiseLock) {
                        try {
                          conn.abort();
                        } catch(Throwable ex2) {
                          logger.warn("Failed to abort existing connect (should be harmless): ", ex);
                        }
                        connectingFuture = null;
                      }
                      openChannel(lastInstance).onComplete(promise);
                    }
                    promise.fail(ex);
                  }
                });
              });
    }
  }

  @Override
  public Future<RabbitMQChannel> openChannel() {
    return openChannel(null);
  }

  @Override
  public Future<RabbitMQChannel> openChannel(AsyncHandler<RabbitMQChannel> channelOpenedHandler) {
    return new RabbitMQChannelImpl(vertx, this, config, channelOpenedHandler)
            .connect();
  }

  @Override
  public <T> Future<RabbitMQPublisher<T>> createPublisher(
          AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , RabbitMQMessageCodec<T> codec
          , String exchange
          , RabbitMQPublisherOptions options
  ) {
    return RabbitMQPublisherImpl.create(vertx, this, channelOpenedHandler, codec, exchange, options);
  }

  @Override
  public Future<RabbitMQConsumer<byte[]>> createConsumer(
          AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , String queue
          , RabbitMQConsumerOptions options
          , Handler<RabbitMQMessage<byte[]>> messageHandler
  ) {
    return createConsumer(channelOpenedHandler, new RabbitMQByteArrayMessageCodec(), queue, null, options, messageHandler);
  }
  
  @Override
  public <T> Future<RabbitMQConsumer<T>> createConsumer(
          AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , RabbitMQMessageCodec<T> codec
          , String queue
          , Supplier<String> queueNameSupplier
          , RabbitMQConsumerOptions options
          , Handler<RabbitMQMessage<T>> messageHandler
  ) {
    if (queueNameSupplier == null) {
      queueNameSupplier = () -> queue;
    }
    return RabbitMQConsumerImpl.create(vertx, vertx.getOrCreateContext(), this, channelOpenedHandler, codec, queueNameSupplier, options, messageHandler);    
  }
  
  @Override
  public Future<Void> close(int closeCode, String closeMessage, int timeout) {
    Connection conn = connection;
    closed = true;
    if (conn == null) {
      return Future.succeededFuture();
    }
    return context.executeBlocking(promise -> {
      try {        
        conn.close(closeCode, closeMessage, timeout);
        promise.complete();
      } catch(Throwable ex) {
        promise.fail(ex);
      }
    });
  }

  @Override
  public Future<Void> close() {
    return close(AMQP.REPLY_SUCCESS, "OK", config.getHandshakeTimeout());
  }

}
