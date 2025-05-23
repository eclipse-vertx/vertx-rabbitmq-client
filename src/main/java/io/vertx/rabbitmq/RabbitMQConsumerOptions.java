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
import java.util.Collections;
import java.util.Map;

/**
 * Aimed to specify queue consumer settings when calling {@link RabbitMQClient#basicConsumer(String, QueueOptions, Handler)}
 */
@DataObject
@JsonGen(inheritConverter = true)
public class RabbitMQConsumerOptions {

  private static final boolean DEFAULT_AUTO_ACK = true;
  private static final boolean DEFAULT_EXCLUSIVE = false;
  private static final long DEFAULT_RECONNECT_INTERVAL = 1000;
  private static final String DEFAULT_CONSUMER_TAG = "";

  private boolean autoAck = DEFAULT_AUTO_ACK;
  private boolean exclusive = DEFAULT_EXCLUSIVE;
  private long reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
  private String consumerTag = DEFAULT_CONSUMER_TAG;

  private Map<String, Object> arguments = Collections.EMPTY_MAP;

  private Handler<RabbitMQConsumer> consumerOkHandler;
  private Handler<RabbitMQConsumer> cancelOkHandler;
  private Handler<RabbitMQConsumer> cancelHandler;
  private Handler<RabbitMQConsumer> shutdownSignalHandler;
  private Handler<RabbitMQConsumer> recoverOkHandler;

  public RabbitMQConsumerOptions() {
  }

  public RabbitMQConsumerOptions(JsonObject json) {
    this();
    RabbitMQConsumerOptionsConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    RabbitMQConsumerOptionsConverter.toJson(this, json);
    return json;
  }

  public RabbitMQConsumerOptions setConsumerOkHandler(Handler<RabbitMQConsumer> handler) {
    this.consumerOkHandler = handler;
    return this;
  }

  public RabbitMQConsumerOptions setCancelOkHandler(Handler<RabbitMQConsumer> handler) {
    this.cancelOkHandler = handler;
    return this;
  }

  public RabbitMQConsumerOptions setCancelHandler(Handler<RabbitMQConsumer> handler) {
    this.cancelHandler = handler;
    return this;
  }

  public RabbitMQConsumerOptions setShutdownSignalHandler(Handler<RabbitMQConsumer> handler) {
    this.shutdownSignalHandler = handler;
    return this;
  }

  public RabbitMQConsumerOptions setConsumerTag(String consumerTag) {
    this.consumerTag = consumerTag;
    return this;
  }

  public Handler<RabbitMQConsumer> getRecoverOkHandler() {
    return recoverOkHandler;
  }

  public RabbitMQConsumerOptions setRecoverOkHandler(Handler<RabbitMQConsumer> recoverOkHandler) {
    this.recoverOkHandler = recoverOkHandler;
    return this;
  }

  public Handler<RabbitMQConsumer> getConsumerOkHandler() {
    return consumerOkHandler;
  }

  public Handler<RabbitMQConsumer> getCancelOkHandler() {
    return cancelOkHandler;
  }

  public Handler<RabbitMQConsumer> getCancelHandler() {
    return cancelHandler;
  }

  public Handler<RabbitMQConsumer> getShutdownSignalHandler() {
    return shutdownSignalHandler;
  }

  public String getConsumerTag() {
    return consumerTag;
  }

  /**
   * @param autoAck true if the server should consider messages
   *                acknowledged once delivered; false if the server should expect
   *                explicit acknowledgements
   */
  public RabbitMQConsumerOptions setAutoAck(boolean autoAck) {
    this.autoAck = autoAck;
    return this;
  }

  /**
   * @return true if the server should consider messages
   * acknowledged once delivered; false if the server should expect
   * explicit acknowledgements
   */
  public boolean isAutoAck() {
    return autoAck;
  }

  /**
   * true if this is an exclusive consumer.
   * @return true if this is an exclusive consumer.
   */
  public boolean isExclusive() {
    return exclusive;
  }

  /**
   * Set whether or not this is an exclusive consumer.
   * <p>
   * See <a href="https://www.rabbitmq.com/consumers.html#exclusivity">https://www.rabbitmq.com/consumers.html#exclusivity</a>.
   * It is recommended that this be set to false, be sure you understand the implications and have read
   * <a href="https://www.rabbitmq.com/consumers.html#single-active-consumer">https://www.rabbitmq.com/consumers.html#single-active-consumer</a> before setting to true.
   * <p>
   * @param exclusive true if this is an exclusive consumer.
   */
  public RabbitMQConsumerOptions setExclusive(boolean exclusive) {
    this.exclusive = exclusive;
    return this;
  }

  public long getReconnectInterval() {
    return reconnectInterval;
  }

  public RabbitMQConsumerOptions setReconnectInterval(long reconnectInterval) {
    this.reconnectInterval = reconnectInterval;
    return this;
  }

  /**
   * Get custom arguments to be used in the call to basicConsume.
   *
   * @return
   */
  public Map<String, Object> getArguments() {
    return arguments;
  }

  /**
   * Set custom arguments to be used in the call to basicConsume.
   *
   * @param arguments
   */
  public RabbitMQConsumerOptions setArguments(Map<String, Object> arguments) {
    this.arguments = arguments;
    return this;
  }



}
