package io.vertx.rabbitmq;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.rabbitmq.RabbitMQPublishOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.rabbitmq.RabbitMQPublishOptions} original class using Vert.x codegen.
 */
public class RabbitMQPublishOptionsConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RabbitMQPublishOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "codec":
          if (member.getValue() instanceof String) {
            obj.setCodec((String)member.getValue());
          }
          break;
        case "waitForConfirm":
          if (member.getValue() instanceof Boolean) {
            obj.setWaitForConfirm((Boolean)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(RabbitMQPublishOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RabbitMQPublishOptions obj, java.util.Map<String, Object> json) {
    if (obj.getCodec() != null) {
      json.put("codec", obj.getCodec());
    }
    json.put("waitForConfirm", obj.isWaitForConfirm());
  }
}
