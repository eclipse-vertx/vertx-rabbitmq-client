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
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 *
 * @author jtalbut
 */
@DataObject(generateConverter = true, inheritConverter = true)
public class RabbitMQPublishOptions {
  
  /**
   * The default MessageCodec = {@code null}.
   */
  public static final String DEFAULT_CODEC = null;

  /**
   * The default waitForConfirm = {@code false}.
   */
  public static final boolean DEFAULT_WAIT_FOR_CONFIRM = false;
  
  private String codec;
  private boolean waitForConfirm;
  private Handler<Long> deliveryTagHandler;
  
  /**
   * Default constructor.
   */
  public RabbitMQPublishOptions() {
    init();
  }

  /**
   * Constructor for JSON representation.
   * @param json A set of RabbitMQOptions as JSON.
   */
  public RabbitMQPublishOptions(JsonObject json) {
    init();
    RabbitMQPublishOptionsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor.
   * @param other Another instance of RabbitMQOptions.
   */
  public RabbitMQPublishOptions(RabbitMQPublishOptions other) {
    this.codec = other.codec;
    this.waitForConfirm = other.waitForConfirm;
    this.deliveryTagHandler = other.deliveryTagHandler;
  }

  /**
   * Set all values that have non-null defaults to their default values.
   */
  private void init() {
    this.codec = DEFAULT_CODEC;
    this.waitForConfirm = DEFAULT_WAIT_FOR_CONFIRM;
  }  

  /**
   * Convert this object to JSON.
   * @return this object, as JSON.
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RabbitMQPublishOptionsConverter.toJson(this, json);
    return json;
  }
  
  /**
   * Get the MessageCodec to use for publishing this message.
   * @return the MessageCodec to use for publishing this message.
   */
  public String getCodec() {
    return codec;
  }

  /**
   * Set the MessageCodec to use for publishing this message.
   * @param codec the MessageCodec to use for publishing this message.
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQPublishOptions setCodec(String codec) {
    this.codec = codec;
    return this;
  }

  /**
   * Get the waitForConfirm flag.
   * When set to true for all calls to publish on this channel to wait until this message has been confirmed.
   * @return the waitForConfirm flag.
   */
  public boolean isWaitForConfirm() {
    return waitForConfirm;
  }

  /**
   * Set to true for all calls to publish on this channel to wait until this message has been confirmed.
   * @param waitForConfirm when true all calls to publish on this channel wait until this message has been confirmed.
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQPublishOptions setWaitForConfirm(boolean waitForConfirm) {
    this.waitForConfirm = waitForConfirm;
    return this;
  }

  /**
   * Get the handler to be called, before the message is sent, with the deliveryTag for this message.
   * @return the handler to be called, before the message is sent, with the deliveryTag for this message.
   */
  public Handler<Long> getDeliveryTagHandler() {
    return deliveryTagHandler;
  }

  /**
   * Set the handler to be called, before the message is sent, with the deliveryTag for this message.
   * @param deliveryTagHandler the handler to be called, before the message is sent, with the deliveryTag for this message.
   * @return a reference to this, so the API can be used fluently
   */
  public RabbitMQPublishOptions setDeliveryTagHandler(Handler<Long> deliveryTagHandler) {
    this.deliveryTagHandler = deliveryTagHandler;
    return this;
  }
  
  
}
