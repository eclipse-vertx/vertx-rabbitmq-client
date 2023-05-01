package io.vertx.rabbitmq;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.rabbitmq.RabbitMQConsumerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.rabbitmq.RabbitMQConsumerOptions} original class using Vert.x codegen.
 */
public class RabbitMQConsumerOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RabbitMQConsumerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "arguments":
          if (member.getValue() instanceof JsonObject) {
            java.util.Map<String, java.lang.Object> map = new java.util.LinkedHashMap<>();
            ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
              if (entry.getValue() instanceof Object)
                map.put(entry.getKey(), entry.getValue());
            });
            obj.setArguments(map);
          }
          break;
        case "autoAck":
          if (member.getValue() instanceof Boolean) {
            obj.setAutoAck((Boolean)member.getValue());
          }
          break;
        case "consumerTag":
          if (member.getValue() instanceof String) {
            obj.setConsumerTag((String)member.getValue());
          }
          break;
        case "exclusive":
          if (member.getValue() instanceof Boolean) {
            obj.setExclusive((Boolean)member.getValue());
          }
          break;
        case "reconnectInterval":
          if (member.getValue() instanceof Number) {
            obj.setReconnectInterval(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

  public static void toJson(RabbitMQConsumerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RabbitMQConsumerOptions obj, java.util.Map<String, Object> json) {
    if (obj.getArguments() != null) {
      JsonObject map = new JsonObject();
      obj.getArguments().forEach((key, value) -> map.put(key, value));
      json.put("arguments", map);
    }
    json.put("autoAck", obj.isAutoAck());
    if (obj.getConsumerTag() != null) {
      json.put("consumerTag", obj.getConsumerTag());
    }
    json.put("exclusive", obj.isExclusive());
    json.put("reconnectInterval", obj.getReconnectInterval());
  }
}
