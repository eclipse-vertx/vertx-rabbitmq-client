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
