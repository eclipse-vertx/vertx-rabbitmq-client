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
 * RabbitMQ client options, most
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class RabbitMQPublisherOptions {

  /**
   * The default connection retry delay = {@code 10000}
   */
  public static final boolean DEFAULT_RESEND_ON_RECONNECT = false;

  private boolean resendOnReconnect = DEFAULT_RESEND_ON_RECONNECT;
  
  public RabbitMQPublisherOptions() {
  }

  public RabbitMQPublisherOptions(JsonObject json) {
    this();
    RabbitMQPublisherOptionsConverter.fromJson(json, this);
  }

  public RabbitMQPublisherOptions(RabbitMQPublisherOptions that) {
    resendOnReconnect = that.resendOnReconnect;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RabbitMQPublisherOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * If set to true the Publisher will retain messages and will attempt to resent them if the underlying connection to the RabbitMQ server is remade.
   * @return Whether or not resending unconfirmed messages when the channel reconnects is enabled.
   */
  public boolean isResendOnReconnect() {
    return resendOnReconnect;
  }

  /**
   * If set to true the Publisher will retain messages and will attempt to resent them if the underlying connection to the RabbitMQ server is remade.
   * @param resendOnReconnect Whether or not resending unconfirmed messages when the channel reconnects is enabled.
   * @return this so that the method can be used in a fluent manner.
   */
  public RabbitMQPublisherOptions setResendOnReconnect(boolean resendOnReconnect) {
    this.resendOnReconnect = resendOnReconnect;
    return this;
  }

}
