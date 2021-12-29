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
import io.vertx.core.json.JsonObject;

/**
 *
 * @author njt
 */
@DataObject(generateConverter = true)
public class RabbitMQPublisherConfirmation {

  private String messageId;
  private boolean succeeded;

  public RabbitMQPublisherConfirmation(JsonObject json) {
    RabbitMQPublisherConfirmationConverter.fromJson(json, this);
  }
  
  public RabbitMQPublisherConfirmation(String messageId, boolean succeeded) {
    this.messageId = messageId;
    this.succeeded = succeeded;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RabbitMQPublisherConfirmationConverter.toJson(this, json);
    return json;
  }
  
  public String getMessageId() {
    return messageId;
  }

  public boolean isSucceeded() {
    return succeeded;
  }

}
