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
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.rabbitmq.RabbitMQChannelBuilder;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQOptions;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

  private String connectionTarget;

  public RabbitMQConnectionImpl(Vertx vertx, RabbitMQOptions config) {
    this.vertx = vertx;
    this.context = vertx.getOrCreateContext();
    this.config = new RabbitMQOptions(config);
  }

  public Vertx getVertx() {
    return vertx;
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
      connectionTarget = "amqp"
              + (cf.isSSL() ? "s" : "")
              + "://"
              + cf.getUsername()
              + "@"
              + (addresses.size() == 1 ? addresses.get(0) : addresses)
              + "/"
              + URLEncoder.encode(cf.getVirtualHost(), "UTF-8");
    } else {
      connectionTarget = "amqp"
              + (cf.isSSL() ? "s" : "")
              + "://"
              + cf.getUsername()
              + "@"
              + cf.getHost()
              + ":"
              + cf.getPort()
              + "/"
              + URLEncoder.encode(cf.getVirtualHost(), "UTF-8");
    }
    logger.info((connectCount.get() > 0 ? "Rec" : "C") + "onnecting to " + connectionTarget);

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

    connectionName = config.getConnectionName();
    if (connectionName == null || connectionName.isEmpty()) {
      connectionName = fabricateConnectionName();
    }
    Connection conn = addresses == null ? cf.newConnection(connectionName) : cf.newConnection(addresses, connectionName);
    lastConnectedInstance = connectCount.incrementAndGet();
    conn.setId(Long.toString(lastConnectedInstance));
    logger.info("Established connection to " + connectionTarget);
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

  /**
   * Return a derived named for the connection that should be sufficient to identify it.
   * <p>
   * Connection names are very useful for broker adminstrators and should always be provided.
   * If nothing else can be done this method should return something useful.
   * <p>
   * @return
   */
  private String fabricateConnectionName() {
    try {
      return ManagementFactory.getRuntimeMXBean().getName();
    } catch (Throwable ex) {
      return "JavaProcess";
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
    ContextInternal ctx = ((VertxInternal) vertx).getOrCreateContext();
    PromiseInternal<Connection> promise = ctx.promise();
    connectBlocking(promise);
    return promise.map(this);
  }

  private void connectBlocking(Promise<Connection> promise) {
    vertx.executeBlocking(() -> {
      synchronized(connectionLock) {
        if (connection == null || !connection.isOpen()) {
          connection = rawConnect();
          connectedAtLeastOnce = true;
        }
        return connection;
      }
    }).onComplete(ar -> {
      if (ar.succeeded()) {
        promise.complete(ar.result());
      } else {
        logger.error("Failed to create connection to " + connectionTarget + ": ", ar.cause());
        if (shouldRetryConnection()) {
          vertx.setTimer(config.getReconnectInterval(), time -> {
            connectBlocking(promise);
          });
        } else {
          promise.fail(ar.cause());
        }
      }
    });
}

  Future<Channel> openChannel(long lastInstance) {
    synchronized (connectingPromiseLock) {
      logger.debug("ConnectionFuture: " + connectingFuture + ", lastInstance: " + lastInstance + ", connectCount: " + connectCount.get() + ", closed: " + closed);
      if (((connectingFuture == null) || (lastInstance != this.connectCount.get())) && !closed) {
        synchronized (connectionLock) {
          if (lastConnectedInstance != connectCount.get()) {
            reconnectCount = 0;
          }
        }
        Promise<Connection> connectingPromise = Promise.promise();
        connectingFuture = connectingPromise.future();
        connectBlocking(connectingPromise);
      }
      return connectingFuture
        .compose(conn -> context
          .executeBlocking(() -> conn.createChannel())
          .transform(ar -> {
            if (ar.failed()) {
              Throwable cause = ar.cause();
              if (cause instanceof AlreadyClosedException || cause instanceof IOException) {
                logger.error("Failed to create channel: ", cause);
                if (shouldRetryConnection()) {
                  synchronized (connectingPromiseLock) {
                    try {
                      conn.abort();
                    } catch (Throwable ex2) {
                      logger.warn("Failed to abort existing connect (should be harmless): ", cause);
                    }
                    connectingFuture = null;
                  }
                  return openChannel(lastInstance);
                }
              }
            }
            return (Future<Channel>) ar;
          }));
    }
  }

  @Override
  public RabbitMQChannelBuilder createChannelBuilder() {
    return new RabbitMQChannelBuilder(this);
  }

  @Override
  public int getConfiguredReconnectAttempts() {
    return config.getReconnectAttempts();
  }

  @Override
  public Future<Void> close(int closeCode, String closeMessage, int timeout) {
    Connection conn = connection;
    closed = true;
    if (conn == null) {
      return Future.succeededFuture();
    }
    return context.executeBlocking(() -> {
      conn.close(closeCode, closeMessage, timeout);
      return null;
    });
  }

  @Override
  public Future<Void> close() {
    return close(AMQP.REPLY_SUCCESS, "OK", config.getHandshakeTimeout());
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

}
