package io.vertx.rabbitmq;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.rabbitmq.RabbitMQPublisherOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.rabbitmq.RabbitMQPublisherOptions} original class using Vert.x codegen.
 */
public class RabbitMQPublisherOptionsConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RabbitMQPublisherOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
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
    json.put("resendOnReconnect", obj.isResendOnReconnect());
  }
}
