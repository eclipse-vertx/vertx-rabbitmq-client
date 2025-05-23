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
package io.vertx.rabbitmq;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 *
 * @author jtalbut
 */
@DataObject
@JsonGen(inheritConverter = true)
public class RabbitMQConfirmation {

  private long channelNumber;
  private long deliveryTag;
  private boolean multiple;
  private boolean succeeded;

  public RabbitMQConfirmation(long channelNumber, long deliveryTag, boolean multiple, boolean succeeded) {
    this.channelNumber = channelNumber;
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

  public long getChannelNumber() {
    return channelNumber;
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
