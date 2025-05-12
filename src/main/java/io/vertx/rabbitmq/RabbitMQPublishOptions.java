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
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 *
 * @author jtalbut
 */
@DataObject
@JsonGen(inheritConverter = true)
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
