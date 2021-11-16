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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.LongString;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.RecoveryDelayHandler;
import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.SocketConfigurator;
import com.rabbitmq.client.SslContextFactory;
import com.rabbitmq.client.TrafficListener;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.CredentialsRefreshService;
import com.rabbitmq.client.impl.ErrorOnWriteListener;
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.client.impl.recovery.RecoveredQueueNameSupplier;
import com.rabbitmq.client.impl.recovery.RetryHandler;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import javax.net.SocketFactory;


/**
 * RabbitMQ client options, most
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true, inheritConverter = true)
public class RabbitMQOptions {

  /**
   * The default port = {@code - 1} - {@code 5671} for SSL otherwise {@code 5672}.
   */
  public static final int DEFAULT_PORT = -1;

  /**
   * The default host = {@code localhost}.
   */
  public static final String DEFAULT_HOST = ConnectionFactory.DEFAULT_HOST;

  /**
   * The default user = {@code guest}.
   */
  public static final String DEFAULT_USER = ConnectionFactory.DEFAULT_USER;

  /**
   * The default password = {@code guest}.
   */
  public static final String DEFAULT_PASSWORD = ConnectionFactory.DEFAULT_PASS;

  /**
   * The default virtual host = {@code /}.
   */
  public static final String DEFAULT_VIRTUAL_HOST = ConnectionFactory.DEFAULT_VHOST;

  /**
   * The default connection timeout = {@code 60000}.
   */
  public static final int DEFAULT_CONNECTION_TIMEOUT = ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT;
  
  /**
   * The default shutdown timeout = {@code 10000}.
   */
  public static final int DEFAULT_SHUTDOWN_TIMEOUT = ConnectionFactory.DEFAULT_SHUTDOWN_TIMEOUT;

  /**
   * The default work pool timeout = {@code -1}.
   */
  public static final int DEFAULT_WORK_POOL_TIMEOUT = ConnectionFactory.DEFAULT_WORK_POOL_TIMEOUT;

  /**
   * The default heartbeat delay = {@code 60}.
   */
  public static final int DEFAULT_REQUESTED_HEARTBEAT = ConnectionFactory.DEFAULT_HEARTBEAT;

  /**
   * The default handshake timeout = {@code 10000}.
   */
  public static final int DEFAULT_HANDSHAKE_TIMEOUT = ConnectionFactory.DEFAULT_HANDSHAKE_TIMEOUT;

  /**
   * The default maximum channel number = {@code 2047}.
   */
  public static final int DEFAULT_REQUESTED_CHANNEL_MAX = ConnectionFactory.DEFAULT_CHANNEL_MAX;

  /**
   * The default maximum frame size = {@code 0}.
   */
  public static final int DEFAULT_REQUESTED_FRAME_MAX = ConnectionFactory.DEFAULT_FRAME_MAX;

  /**
   * The default network recovery internal = {@code 5000}.
   */
  public static final long DEFAULT_NETWORK_RECOVERY_INTERNAL = 5000L;

  /**
   * The default automatic recovery enabled = {@code false}.
   */
  public static final boolean DEFAULT_AUTOMATIC_RECOVERY_ENABLED = false;

  /**
   * The default number of attempts to make an initial connection = {@code 0L}.
   */
  public static final long DEFAULT_INITIAL_CONNECT_ATTEMPTS = 0L;

  /**
   * The default connection retry delay = {@code 10000}.
   */
  public static final int DEFAULT_RECONNECT_INTERVAL = 10000;

  /**
   * The default connection retry delay = {@code 0}.
   */
  public static final int DEFAULT_RECONNECT_ATTEMPTS = 0;

  /**
   * The default ssl = {@code false}.
   */
  public static final boolean DEFAULT_SSL = false;

  /**
   * The default trustAll = {@code false}.
   */
  public static final boolean DEFAULT_TRUST_ALL = false;

  /**
   * The default ENABLED_SECURE_TRANSPORT_PROTOCOLS value = { "TLSv1.2" }
   * <p/>
   * RabbitMQ usually supports only TLSv1.2 and TLSv1.3 (if correctly configured).
   * There was an issue with the Java client that prevents TLSv1.3 from working with NIO (fixed in v5.14.0)
   * , if an earlier version of the library is being used please set the secureTransportProtocol to "TLSv1.2".
   * The RabbitMQ client does not do protocol negotiation, so this set should contain only one value.
   */
  public static final String DEFAULT_SECURE_TRANSPORT_PROTOCOL = "TLSv1.3";
  
  /**
   * The default DEFAULT_ENABLED_TLS_HOSTNAME_VERIFICATION value = true
   */
  public static final boolean DEFAULT_ENABLED_TLS_HOSTNAME_VERIFICATION = true;
  
  /**
   * The default connection name = {@code VertxRabbitMQ}.
   * It is strongly recommended that all clients change this to something more identifying.
   */
  public static final String DEFAULT_CONNECTION_NAME = "VertxRabbitMQ";

  /**
   * The default RPC timeout value = {@code 10 minutes}.
   */
  public static final int DEFAULT_CHANNEL_RPC_TIMEOUT = ConnectionFactory.DEFAULT_CHANNEL_RPC_TIMEOUT;
  
  /**
   * The default value for whether or not channels check the reply type of an RPC call = {@code false}.
   */
  public static final boolean DEFAULT_CHANNEL_SHOULD_CHECK_RPC_RESPONSE_TYPE = false;
  
  private String uri = null;
  private List<Address> addresses = Collections.emptyList();
  private String user;
  private String password;
  private String host;
  private String virtualHost;
  private int port;
  private int connectionTimeout;
  private int requestedHeartbeat;
  private int handshakeTimeout;
  private int requestedChannelMax;
  private int requestedFrameMax;
  private int shutdownTimeout;
  private int workPoolTimeout;
  
  private int channelRpcTimeout;
  private boolean channelShouldCheckRpcResponseType;
  
  private boolean tlsHostnameVerification;
  
  private int reconnectInterval;
  private int reconnectAttempts;
  private boolean ssl;
  private boolean trustAll;
  private String secureTransportProtocol;
  private JksOptions keyStoreOptions;
  private JksOptions trustStoreOptions;
  
  // These three control the java RabbitMQ client automatic recovery
  private boolean automaticRecoveryEnabled;
  private Boolean topologyRecoveryEnabled;
  private long networkRecoveryInterval;
  
  // This (and reconnectAttempts, reconnectInterval from NetClientOptions) control the reconnects implented in this library
  private long initialConnectAttempts;
  
  private String connectionName;

  private Map<String, Object> clientProperties;
  private Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition;
  private CredentialsProvider credentialsProvider;
  private CredentialsRefreshService credentialsRefreshService;
  private ErrorOnWriteListener errorOnWriteListener;
  private ExceptionHandler exceptionHandler;
  private ScheduledExecutorService heartbeatExecutor;
  private MetricsCollector metricsCollector;
  private NioParams nioParams;
  private RecoveredQueueNameSupplier recoveredQueueNameSupplier;
  private RecoveryDelayHandler recoveryDelayHandler;
  private SaslConfig saslConfig;
  private ExecutorService sharedExecutor;
  private ExecutorService shutdownExecutor;
  private SocketConfigurator socketConfigurator;
  private SocketFactory socketFactory;
  private SslContextFactory sslContextFactory;
  private ThreadFactory threadFactory;
  private ExecutorService topologyRecoveryExecutor;
  private TopologyRecoveryFilter topologyRecoveryFilter;
  private RetryHandler topologyRecoveryRetryHandler;
  private TrafficListener trafficListener;

  /**
   * Default constructor.
   */
  public RabbitMQOptions() {
    init();
  }

  /**
   * Constructor for JSON representation.
   * @param json A set of RabbitMQOptions as JSON.
   */
  public RabbitMQOptions(JsonObject json) {
    init();
    RabbitMQOptionsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor.
   * @param other Another instance of RabbitMQOptions.
   */
  public RabbitMQOptions(RabbitMQOptions other) {
    this.uri = other.uri;
    this.addresses = other.addresses;
    this.user = other.user;
    this.password = other.password;
    this.host = other.host;
    this.virtualHost = other.virtualHost;
    this.port = other.port;
    this.connectionTimeout = other.connectionTimeout;
    this.shutdownTimeout = other.shutdownTimeout;
    this.workPoolTimeout = other.workPoolTimeout;
    this.requestedHeartbeat = other.requestedHeartbeat;
    this.handshakeTimeout = other.handshakeTimeout;
    this.networkRecoveryInterval = other.networkRecoveryInterval;
    this.automaticRecoveryEnabled = other.automaticRecoveryEnabled;
    this.topologyRecoveryEnabled = other.topologyRecoveryEnabled;
    this.initialConnectAttempts = other.initialConnectAttempts;
    this.requestedChannelMax = other.requestedChannelMax;
    this.requestedFrameMax = other.requestedFrameMax;
    this.connectionName = other.connectionName;
    this.tlsHostnameVerification = other.tlsHostnameVerification;
    
    this.reconnectInterval = other.reconnectInterval;
    this.reconnectAttempts = other.reconnectAttempts;
    this.ssl = other.ssl;
    this.trustAll = other.trustAll;
    this.secureTransportProtocol = other.secureTransportProtocol;
    this.keyStoreOptions = other.keyStoreOptions;
    this.trustStoreOptions = other.trustStoreOptions;
    
    this.channelRpcTimeout = other.channelRpcTimeout;
    this.channelShouldCheckRpcResponseType = other.channelShouldCheckRpcResponseType;
    this.clientProperties = other.clientProperties;
    this.connectionRecoveryTriggeringCondition = other.connectionRecoveryTriggeringCondition;
    this.credentialsProvider = other.credentialsProvider;
    this.credentialsRefreshService = other.credentialsRefreshService;
    this.errorOnWriteListener = other.errorOnWriteListener;
    this.exceptionHandler = other.exceptionHandler;
    this.heartbeatExecutor = other.heartbeatExecutor;
    this.metricsCollector = other.metricsCollector;
    this.nioParams = other.nioParams;
    this.recoveredQueueNameSupplier = other.recoveredQueueNameSupplier;
    this.recoveryDelayHandler = other.recoveryDelayHandler;
    this.saslConfig = other.saslConfig;
    this.sharedExecutor = other.sharedExecutor;
    this.shutdownExecutor = other.shutdownExecutor;
    this.socketConfigurator = other.socketConfigurator;
    this.socketFactory = other.socketFactory;
    this.sslContextFactory = other.sslContextFactory;    
    this.threadFactory = other.threadFactory;
    this.topologyRecoveryExecutor = other.topologyRecoveryExecutor;
    this.topologyRecoveryFilter = other.topologyRecoveryFilter;
    this.topologyRecoveryRetryHandler = other.topologyRecoveryRetryHandler;
    this.trafficListener = other.trafficListener;
  }

  /**
   * Set all values that have non-null defaults to their default values.
   */
  private void init() {
    this.uri = null;
    this.addresses = Collections.emptyList();
    this.user = DEFAULT_USER;
    this.password = DEFAULT_PASSWORD;
    this.host = DEFAULT_HOST;
    this.virtualHost = DEFAULT_VIRTUAL_HOST;
    this.port = DEFAULT_PORT;
    this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    this.shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
    this.workPoolTimeout = DEFAULT_WORK_POOL_TIMEOUT;
    this.requestedHeartbeat = DEFAULT_REQUESTED_HEARTBEAT;
    this.handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;
    this.requestedChannelMax = DEFAULT_REQUESTED_CHANNEL_MAX;
    this.requestedFrameMax = DEFAULT_REQUESTED_FRAME_MAX;
    this.networkRecoveryInterval = DEFAULT_NETWORK_RECOVERY_INTERNAL;
    this.automaticRecoveryEnabled = DEFAULT_AUTOMATIC_RECOVERY_ENABLED;
    this.topologyRecoveryEnabled = null;
    this.initialConnectAttempts = DEFAULT_INITIAL_CONNECT_ATTEMPTS;
    this.connectionName = DEFAULT_CONNECTION_NAME;
    this.tlsHostnameVerification = DEFAULT_ENABLED_TLS_HOSTNAME_VERIFICATION;
    
    this.reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    this.reconnectAttempts = DEFAULT_RECONNECT_ATTEMPTS;
    this.ssl = DEFAULT_SSL;
    this.trustAll = DEFAULT_TRUST_ALL;
    this.secureTransportProtocol = DEFAULT_SECURE_TRANSPORT_PROTOCOL;
    
    this.channelRpcTimeout = DEFAULT_CHANNEL_RPC_TIMEOUT;
    this.channelShouldCheckRpcResponseType = DEFAULT_CHANNEL_SHOULD_CHECK_RPC_RESPONSE_TYPE;
    this.clientProperties = unLongStringMap(AMQConnection.defaultClientProperties());
  }
  
  /**
   * The default client properties for AMQConnection uses the @link com.rabbitmq.client.LongString} class, which cannot be converted to JSON.
   * This method converts those LongStrings to plain Strings.
   * @param src The Map that may include LongString objects.
   * @return A Map that does not contain any LongString objects.
   */
  private static Map<String, Object> unLongStringMap(Map<String, Object> src) {
    Map<String, Object> dst = new HashMap<>();
    src.forEach((k,v) -> {
      if (v instanceof LongString) {
        dst.put(k, v.toString());
      } else if (v instanceof Map) {
        dst.put(k, unLongStringMap((Map<String, Object>) v));
      } else {
        dst.put(k, v);
      }
    });
    return dst;
  }

  /**
   * Convert this object to JSON.
   * @return this object, as JSON.
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RabbitMQOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * Get multiple addresses for cluster mode.
   * This value will override the host and port properties, but will only be used if the URI is null.
   * @return addresses of AMQP cluster.
   */
  public List<Address> getAddresses() {
    return Collections.unmodifiableList(addresses);
  }

  /**
   * Set multiple addresses for cluster mode.
   * This value overrides the host and port properties, but will only be used if the URI is null.
   *
   * @param addresses addresses of AMQP cluster
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setAddresses(List<Address> addresses) {
    this.addresses = new ArrayList<>(addresses);
    return this;
  }

  /**
   * Get the fields host, port, username, password and virtual host in a single URI.
   * @return a single URI containing the fields host, port, username, password and virtual host.
   */
  public String getUri() {
    return uri;
  }

  /**
   * Set the fields protocol, host, port, username, password and virtual host in a single URI.
   * @param uri The AMQP URI.
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setUri(String uri) {
    this.uri = uri;
    return this;
  }

  /**
   * Get the AMQP user name to use when connecting to the broker.
   * Note that this values overrides the value set with {@link #setUri}.
   * @return the AMQP user name to use when connecting to the broker.
   */
  public String getUser() {
    return user;
  }

  /**
   * Set the AMQP user name to use when connecting to the broker.
   * Note that this will override the value set with {@link #setUri}.*
   * @param user the user name
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setUser(String user) {
    this.user = user;
    return this;
  }

  /**
   * Set the AMQP password to use when connecting to the broker.
   * Note that this will override the value set with {@link #setUri}.*
   * @return the password to use when connecting to the broker
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set the password to use when connecting to the broker.
   *
   * Note that this values overrides the value set with {@link #setUri}.
   * @param password the password
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get the host to use for connections.
   * This value will only be used if neither URI nor Addresses are set.
   * @return the host to use for connections
   */
  public String getHost() {
    return host;
  }

  /**
   * Set the host to use for connections.
   *
   * This value is only used if neither URI nor Addresses are set.
   * @param host the host
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Get the virtual host to use when connecting to the broker.
   * Note that this will override the value set with {@link #setUri}.*
   * @return the virtual host to use when connecting to the broker
   */
  public String getVirtualHost() {
    return virtualHost;
  }

  /**
   * Set the virtual host to use when connecting to the broker.
   * Note that this overrides the value set with {@link #setUri}.*
   *
   * @param virtualHost the virtual host
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setVirtualHost(String virtualHost) {
    this.virtualHost = virtualHost;
    return this;
  }

  /**
   * Get the server port to use for connections.
   * This value will only be used if neither URI nor Addresses are set.
   * @return the port to use for connections.
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the server port to use for connections.
   * This value is only used if neither URI nor Addresses are set.
   *
   * @param port the default port
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * Get the TCP connection timeout, in milliseconds.
   * 
   * @return the TCP connection timeout.
   * @see com.rabbitmq.client.ConnectionFactory#setConnectionTimeout
   */
  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  /**
   * Set the TCP connection timeout, in milliseconds, {@code zero} for infinite).
   *
   * @param connectionTimeout the timeouut in milliseconds.
   * @return a reference to this, so the API can be used fluently
   * @see com.rabbitmq.client.ConnectionFactory#getConnectionTimeout
   */
  public RabbitMQOptions setConnectionTimeout(int connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
    return this;
  }

  /**
   * @return the initially requested heartbeat interval
   */
  public int getRequestedHeartbeat() {
    return requestedHeartbeat;
  }

  /**
   * Set the initially requested heartbeat interval, in seconds, {@code zero} for none.
   *
   * @param requestedHeartbeat the request heartbeat interval
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setRequestedHeartbeat(int requestedHeartbeat) {
    this.requestedHeartbeat = requestedHeartbeat;
    return this;
  }

  /**
   * @return the AMQP 0-9-1 protocol handshake timeout
   */
  public int getHandshakeTimeout() {
    return handshakeTimeout;
  }

  /**
   * Set the AMQP 0-9-1 protocol handshake timeout, in milliseconds
   *
   * @param handshakeTimeout the timeout in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setHandshakeTimeout(int handshakeTimeout) {
    this.handshakeTimeout = handshakeTimeout;
    return this;
  }

  /**
   * @return the initially requested maximum channel number
   */
  public int getRequestedChannelMax() {
    return requestedChannelMax;
  }

  /**
   * Set the initially requested maximum channel number, {@code zero} for unlimited.
   *
   * @param requestedChannelMax the requested maximum channel number
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setRequestedChannelMax(int requestedChannelMax) {
    this.requestedChannelMax = requestedChannelMax;
    return this;
  }

  /**
   * @return automatic connection recovery interval
   */
  public long getNetworkRecoveryInterval() {
    return networkRecoveryInterval;
  }

  /**
   * Set how long in milliseconds will automatic recovery wait before attempting to reconnect, default is {@code 5000}
   *
   * @param networkRecoveryInterval the connection recovery interval
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setNetworkRecoveryInterval(long networkRecoveryInterval) {
    this.networkRecoveryInterval = networkRecoveryInterval;
    return this;
  }

  /**
   * @return {@code true} if automatic connection recovery is enabled, {@code false} otherwise
   */
  public boolean isAutomaticRecoveryEnabled() {
    return automaticRecoveryEnabled;
  }

  /**
   * Enables or disables automatic connection recovery.
   *
   * @param automaticRecoveryEnabled if {@code true}, enables connection recovery
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setAutomaticRecoveryEnabled(boolean automaticRecoveryEnabled) {
    this.automaticRecoveryEnabled = automaticRecoveryEnabled;
    return this;
  }

  public long getInitialConnectAttempts() {
    return initialConnectAttempts;
  }

  /**
   * Enable or disable reconnection, as implemented in this library, on initial connections.
   * 
   * In some situations (primarily dynamic test environments) brokers will be brought up at the same time as clients
   * , and may not be up in time for the connection.
   * 
   * To work around this initialConnectAttempts can be set to the number of attempts to make for that initial connection.
   * The default value of zero means that if the configuration is wrong it will be identified quickly.
   * 
   * Note that the Java client recovery process will never attempt recovery on the initial connection.
   * It should be possible to combine a non-zero value for initial connect attempts with the Java client recovery process:
   * <pre>
   * options.setReconnectAttempts(0);
   * options.setInitialConnectAttempts(10);
   * options.setAutomaticRecoveryEnabled(true);
   * </pre>
   * 
   * @param initialConnectAttempts number of attempts to make for the initial connection.
   * @return a reference to this, so the API can be used fluently
   * 
   */
  public RabbitMQOptions setInitialConnectAttempts(long initialConnectAttempts) {
    this.initialConnectAttempts = initialConnectAttempts;
    return this;
  }
  
  
  /**
   * @return {@code true} because NIO Sockets are always enabled
   */
  public boolean isNioEnabled() {
    return true;
  }

  /**
   * Set the value of reconnect attempts
   *
   * @param reconnectAttempts  the maximum number of reconnect attempts
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setReconnectAttempts(int reconnectAttempts) {
    this.reconnectAttempts = reconnectAttempts;
    return this;
  }

  /**
   * Get the time (in ms) between attempts to reconnect.
   * @param reconnectInterval the time (in ms) between attempts to reconnect.
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setReconnectInterval(int reconnectInterval) {
    this.reconnectInterval = reconnectInterval;
    return this;
  }

  /**
   * Set to true if the connection should connect using ssl.
   * This does not need to be called explicitly if an AMQPS URL is used.
   * @param ssl true if the connection should connect using ssl.
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQOptions setSsl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  /**
   * 
   * @param trustAll
   * @return 
   */
  public RabbitMQOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  public boolean isTrustAll() {
    return trustAll;
  }    

  public RabbitMQOptions setKeyStoreOptions(JksOptions options) {
    this.keyStoreOptions = options;
    return this;
  }

  public String getConnectionName() {
    return connectionName;
  }

  public RabbitMQOptions setConnectionName(String connectionName) {
    this.connectionName = connectionName;
    return this;
  }

  public int getChannelRpcTimeout() {
    return channelRpcTimeout;
  }

  public RabbitMQOptions setChannelRpcTimeout(int channelRpcTimeout) {
    this.channelRpcTimeout = channelRpcTimeout;
    return this;
  }

  public boolean isChannelShouldCheckRpcResponseType() {
    return channelShouldCheckRpcResponseType;
  }

  public RabbitMQOptions setChannelShouldCheckRpcResponseType(boolean channelShouldCheckRpcResponseType) {
    this.channelShouldCheckRpcResponseType = channelShouldCheckRpcResponseType;
    return this;
  }

  public Map<String, Object> getClientProperties() {
    return clientProperties;
  }

  public RabbitMQOptions setClientProperties(Map<String, Object> clientProperties) {
    this.clientProperties = clientProperties;
    return this;
  }

  public Predicate<ShutdownSignalException> getConnectionRecoveryTriggeringCondition() {
    return connectionRecoveryTriggeringCondition;
  }

  public RabbitMQOptions setConnectionRecoveryTriggeringCondition(Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition) {
    this.connectionRecoveryTriggeringCondition = connectionRecoveryTriggeringCondition;
    return this;
  }

  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public RabbitMQOptions setCredentialsProvider(CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
    return this;
  }

  public CredentialsRefreshService getCredentialsRefreshService() {
    return credentialsRefreshService;
  }

  public RabbitMQOptions setCredentialsRefreshService(CredentialsRefreshService credentialsRefreshService) {
    this.credentialsRefreshService = credentialsRefreshService;
    return this;
  }

  public ErrorOnWriteListener getErrorOnWriteListener() {
    return errorOnWriteListener;
  }

  public RabbitMQOptions setErrorOnWriteListener(ErrorOnWriteListener errorOnWriteListener) {
    this.errorOnWriteListener = errorOnWriteListener;
    return this;
  }

  public ExceptionHandler getExceptionHandler() {
    return exceptionHandler;
  }

  public RabbitMQOptions setExceptionHandler(ExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  public ScheduledExecutorService getHeartbeatExecutor() {
    return heartbeatExecutor;
  }

  public RabbitMQOptions setHeartbeatExecutor(ScheduledExecutorService heartbeatExecutor) {
    this.heartbeatExecutor = heartbeatExecutor;
    return this;
  }

  public MetricsCollector getMetricsCollector() {
    return metricsCollector;
  }

  public RabbitMQOptions setMetricsCollector(MetricsCollector metricsCollector) {
    this.metricsCollector = metricsCollector;
    return this;
  }

  public NioParams getNioParams() {
    return nioParams;
  }

  public RabbitMQOptions setNioParams(NioParams nioParams) {
    this.nioParams = nioParams;
    return this;
  }

  public int getRequestedFrameMax() {
    return requestedFrameMax;
  }

  public RabbitMQOptions setRequestedFrameMax(int requestedFrameMax) {
    this.requestedFrameMax = requestedFrameMax;
    return this;
  }

  public RecoveredQueueNameSupplier getRecoveredQueueNameSupplier() {
    return recoveredQueueNameSupplier;
  }

  public RabbitMQOptions setRecoveredQueueNameSupplier(RecoveredQueueNameSupplier recoveredQueueNameSupplier) {
    this.recoveredQueueNameSupplier = recoveredQueueNameSupplier;
    return this;
  }

  public RecoveryDelayHandler getRecoveryDelayHandler() {
    return recoveryDelayHandler;
  }

  public RabbitMQOptions setRecoveryDelayHandler(RecoveryDelayHandler recoveryDelayHandler) {
    this.recoveryDelayHandler = recoveryDelayHandler;
    return this;
  }

  public int getShutdownTimeout() {
    return shutdownTimeout;
  }

  public RabbitMQOptions setShutdownTimeout(int shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
    return this;
  }

  public SaslConfig getSaslConfig() {
    return saslConfig;
  }

  public RabbitMQOptions setSaslConfig(SaslConfig saslConfig) {
    this.saslConfig = saslConfig;
    return this;
  }

  public ExecutorService getSharedExecutor() {
    return sharedExecutor;
  }

  public RabbitMQOptions setSharedExecutor(ExecutorService sharedExecutor) {
    this.sharedExecutor = sharedExecutor;
    return this;
  }

  public ExecutorService getShutdownExecutor() {
    return shutdownExecutor;
  }

  public RabbitMQOptions setShutdownExecutor(ExecutorService shutdownExecutor) {
    this.shutdownExecutor = shutdownExecutor;
    return this;
  }

  public SocketConfigurator getSocketConfigurator() {
    return socketConfigurator;
  }

  public RabbitMQOptions setSocketConfigurator(SocketConfigurator socketConfigurator) {
    this.socketConfigurator = socketConfigurator;
    return this;
  }

  public SocketFactory getSocketFactory() {
    return socketFactory;
  }

  public RabbitMQOptions setSocketFactory(SocketFactory socketFactory) {
    this.socketFactory = socketFactory;
    return this;
  }

  public SslContextFactory getSslContextFactory() {
    return sslContextFactory;
  }

  public RabbitMQOptions setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  public Boolean getTopologyRecoveryEnabled() {
    return topologyRecoveryEnabled;
  }

  public RabbitMQOptions setTopologyRecoveryEnabled(Boolean topologyRecoveryEnabled) {
    this.topologyRecoveryEnabled = topologyRecoveryEnabled;
    return this;
  }

  public ThreadFactory getThreadFactory() {
    return threadFactory;
  }

  public RabbitMQOptions setThreadFactory(ThreadFactory threadFactory) {
    this.threadFactory = threadFactory;
    return this;
  }

  public ExecutorService getTopologyRecoveryExecutor() {
    return topologyRecoveryExecutor;
  }

  public RabbitMQOptions setTopologyRecoveryExecutor(ExecutorService topologyRecoveryExecutor) {
    this.topologyRecoveryExecutor = topologyRecoveryExecutor;
    return this;
  }

  public TopologyRecoveryFilter getTopologyRecoveryFilter() {
    return topologyRecoveryFilter;
  }

  public RabbitMQOptions setTopologyRecoveryFilter(TopologyRecoveryFilter topologyRecoveryFilter) {
    this.topologyRecoveryFilter = topologyRecoveryFilter;
    return this;
  }

  public RetryHandler getTopologyRecoveryRetryHandler() {
    return topologyRecoveryRetryHandler;
  }

  public RabbitMQOptions setTopologyRecoveryRetryHandler(RetryHandler topologyRecoveryRetryHandler) {
    this.topologyRecoveryRetryHandler = topologyRecoveryRetryHandler;
    return this;
  }

  public TrafficListener getTrafficListener() {
    return trafficListener;
  }

  public RabbitMQOptions setTrafficListener(TrafficListener trafficListener) {
    this.trafficListener = trafficListener;
    return this;
  }

  public int getWorkPoolTimeout() {
    return workPoolTimeout;
  }

  public RabbitMQOptions setWorkPoolTimeout(int workPoolTimeout) {
    this.workPoolTimeout = workPoolTimeout;
    return this;
  }

  public boolean isTlsHostnameVerification() {
    return tlsHostnameVerification;
  }

  public RabbitMQOptions setTlsHostnameVerification(boolean tlsHostnameVerification) {
    this.tlsHostnameVerification = tlsHostnameVerification;
    return this;
  }

  public int getReconnectInterval() {
    return reconnectInterval;
  }

  public int getReconnectAttempts() {
    return reconnectAttempts;
  }

  public String getSecureTransportProtocol() {
    return secureTransportProtocol;
  }

  public RabbitMQOptions setSecureTransportProtocol(String secureTransportProtocol) {
    this.secureTransportProtocol = secureTransportProtocol;
    return this;
  }

  public boolean isSsl() {
    return ssl;
  }

  public JksOptions getKeyStoreOptions() {
    return keyStoreOptions;
  }  

  public JksOptions getTrustStoreOptions() {
    return trustStoreOptions;
  }

  public RabbitMQOptions setTrustStoreOptions(JksOptions trustStoreOptions) {
    this.trustStoreOptions = trustStoreOptions;
    return this;
  }
  
  
}
