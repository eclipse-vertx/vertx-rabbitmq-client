package io.vertx.rabbitmq;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.rabbitmq.RabbitMQPublisherOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.rabbitmq.RabbitMQPublisherOptions} original class using Vert.x codegen.
 */
public class RabbitMQPublisherOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RabbitMQPublisherOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "messageCodec":
          if (member.getValue() instanceof String) {
            obj.setMessageCodec((String)member.getValue());
          }
          break;
        case "resendOnReconnect":
          if (member.getValue() instanceof Boolean) {
            obj.setResendOnReconnect((Boolean)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(RabbitMQPublisherOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RabbitMQPublisherOptions obj, java.util.Map<String, Object> json) {
    if (obj.getMessageCodec() != null) {
      json.put("messageCodec", obj.getMessageCodec());
    }
    json.put("resendOnReconnect", obj.isResendOnReconnect());
  }
}
