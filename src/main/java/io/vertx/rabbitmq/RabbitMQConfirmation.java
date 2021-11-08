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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 *
 * @author jtalbut
 */
@DataObject(generateConverter = true)
public class RabbitMQConfirmation {
  
  private String channelId;
  private long deliveryTag;
  private boolean multiple;
  private boolean succeeded;

  public RabbitMQConfirmation(String channelId, long deliveryTag, boolean multiple, boolean succeeded) {
    this.channelId = channelId;
    this.deliveryTag = deliveryTag;
    this.multiple = multiple;
    this.succeeded = succeeded;
  }
  
  public RabbitMQConfirmation(JsonObject json) {
    RabbitMQConfirmationConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RabbitMQConfirmationConverter.toJson(this, json);
    return json;
  }

  public String getChannelId() {
    return channelId;
  }

  public long getDeliveryTag() {
    return deliveryTag;
  }

  public boolean isMultiple() {
    return multiple;
  }

  public boolean isSucceeded() {
    return succeeded;
  }

}
