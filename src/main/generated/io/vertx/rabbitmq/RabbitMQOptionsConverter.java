package io.vertx.rabbitmq;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.rabbitmq.RabbitMQOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.rabbitmq.RabbitMQOptions} original class using Vert.x codegen.
 */
public class RabbitMQOptionsConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RabbitMQOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "uri":
          if (member.getValue() instanceof String) {
            obj.setUri((String)member.getValue());
          }
          break;
        case "user":
          if (member.getValue() instanceof String) {
            obj.setUser((String)member.getValue());
          }
          break;
        case "password":
          if (member.getValue() instanceof String) {
            obj.setPassword((String)member.getValue());
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "virtualHost":
          if (member.getValue() instanceof String) {
            obj.setVirtualHost((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "connectionTimeout":
          if (member.getValue() instanceof Number) {
            obj.setConnectionTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "requestedHeartbeat":
          if (member.getValue() instanceof Number) {
            obj.setRequestedHeartbeat(((Number)member.getValue()).intValue());
          }
          break;
        case "handshakeTimeout":
          if (member.getValue() instanceof Number) {
            obj.setHandshakeTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "requestedChannelMax":
          if (member.getValue() instanceof Number) {
            obj.setRequestedChannelMax(((Number)member.getValue()).intValue());
          }
          break;
        case "networkRecoveryInterval":
          if (member.getValue() instanceof Number) {
            obj.setNetworkRecoveryInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "automaticRecoveryEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setAutomaticRecoveryEnabled((Boolean)member.getValue());
          }
          break;
        case "initialConnectAttempts":
          if (member.getValue() instanceof Number) {
            obj.setInitialConnectAttempts(((Number)member.getValue()).intValue());
          }
          break;
        case "nioEnabled":
          break;
        case "reconnectAttempts":
          if (member.getValue() instanceof Number) {
            obj.setReconnectAttempts(((Number)member.getValue()).intValue());
          }
          break;
        case "reconnectInterval":
          if (member.getValue() instanceof Number) {
            obj.setReconnectInterval(((Number)member.getValue()).intValue());
          }
          break;
        case "ssl":
          if (member.getValue() instanceof Boolean) {
            obj.setSsl((Boolean)member.getValue());
          }
          break;
        case "trustAll":
          if (member.getValue() instanceof Boolean) {
            obj.setTrustAll((Boolean)member.getValue());
          }
          break;
        case "keyStoreOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setKeyStoreOptions(new io.vertx.core.net.JksOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "connectionName":
          if (member.getValue() instanceof String) {
            obj.setConnectionName((String)member.getValue());
          }
          break;
        case "channelRpcTimeout":
          if (member.getValue() instanceof Number) {
            obj.setChannelRpcTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "channelShouldCheckRpcResponseType":
          if (member.getValue() instanceof Boolean) {
            obj.setChannelShouldCheckRpcResponseType((Boolean)member.getValue());
          }
          break;
        case "clientProperties":
          if (member.getValue() instanceof JsonObject) {
            java.util.Map<String, java.lang.Object> map = new java.util.LinkedHashMap<>();
            ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
              if (entry.getValue() instanceof Object)
                map.put(entry.getKey(), entry.getValue());
            });
            obj.setClientProperties(map);
          }
          break;
        case "requestedFrameMax":
          if (member.getValue() instanceof Number) {
            obj.setRequestedFrameMax(((Number)member.getValue()).intValue());
          }
          break;
        case "shutdownTimeout":
          if (member.getValue() instanceof Number) {
            obj.setShutdownTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "topologyRecoveryEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setTopologyRecoveryEnabled((Boolean)member.getValue());
          }
          break;
        case "workPoolTimeout":
          if (member.getValue() instanceof Number) {
            obj.setWorkPoolTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "tlsHostnameVerification":
          if (member.getValue() instanceof Boolean) {
            obj.setTlsHostnameVerification((Boolean)member.getValue());
          }
          break;
        case "secureTransportProtocol":
          if (member.getValue() instanceof String) {
            obj.setSecureTransportProtocol((String)member.getValue());
          }
          break;
        case "trustStoreOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setTrustStoreOptions(new io.vertx.core.net.JksOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
      }
    }
  }

  public static void toJson(RabbitMQOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RabbitMQOptions obj, java.util.Map<String, Object> json) {
    if (obj.getUri() != null) {
      json.put("uri", obj.getUri());
    }
    if (obj.getUser() != null) {
      json.put("user", obj.getUser());
    }
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    if (obj.getVirtualHost() != null) {
      json.put("virtualHost", obj.getVirtualHost());
    }
    json.put("port", obj.getPort());
    json.put("connectionTimeout", obj.getConnectionTimeout());
    json.put("requestedHeartbeat", obj.getRequestedHeartbeat());
    json.put("handshakeTimeout", obj.getHandshakeTimeout());
    json.put("requestedChannelMax", obj.getRequestedChannelMax());
    json.put("networkRecoveryInterval", obj.getNetworkRecoveryInterval());
    json.put("automaticRecoveryEnabled", obj.isAutomaticRecoveryEnabled());
    json.put("initialConnectAttempts", obj.getInitialConnectAttempts());
    json.put("nioEnabled", obj.isNioEnabled());
    json.put("reconnectAttempts", obj.getReconnectAttempts());
    json.put("reconnectInterval", obj.getReconnectInterval());
    json.put("ssl", obj.isSsl());
    json.put("trustAll", obj.isTrustAll());
    if (obj.getKeyStoreOptions() != null) {
      json.put("keyStoreOptions", obj.getKeyStoreOptions().toJson());
    }
    if (obj.getConnectionName() != null) {
      json.put("connectionName", obj.getConnectionName());
    }
    json.put("channelRpcTimeout", obj.getChannelRpcTimeout());
    json.put("channelShouldCheckRpcResponseType", obj.isChannelShouldCheckRpcResponseType());
    if (obj.getClientProperties() != null) {
      JsonObject map = new JsonObject();
      obj.getClientProperties().forEach((key, value) -> map.put(key, value));
      json.put("clientProperties", map);
    }
    json.put("requestedFrameMax", obj.getRequestedFrameMax());
    json.put("shutdownTimeout", obj.getShutdownTimeout());
    if (obj.getTopologyRecoveryEnabled() != null) {
      json.put("topologyRecoveryEnabled", obj.getTopologyRecoveryEnabled());
    }
    json.put("workPoolTimeout", obj.getWorkPoolTimeout());
    json.put("tlsHostnameVerification", obj.isTlsHostnameVerification());
    if (obj.getSecureTransportProtocol() != null) {
      json.put("secureTransportProtocol", obj.getSecureTransportProtocol());
    }
    if (obj.getTrustStoreOptions() != null) {
      json.put("trustStoreOptions", obj.getTrustStoreOptions().toJson());
    }
  }
}
