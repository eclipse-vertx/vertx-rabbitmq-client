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
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Aimed to specify queue consumer settings when calling {@link RabbitMQClient#basicConsumer(String, QueueOptions, Handler)}
 */
@DataObject(generateConverter = true)
public class RabbitMQConsumerOptions {

  private static final int DEFAULT_QUEUE_SIZE = Integer.MAX_VALUE;
  private static final boolean DEFAULT_AUTO_ACK = true;
  private static final boolean DEFAULT_KEEP_MOST_RECENT = false;
  private static final long DEFAULT_RECONNECT_INTERVAL = 1000;

  private boolean autoAck = DEFAULT_AUTO_ACK;
  private boolean keepMostRecent = DEFAULT_KEEP_MOST_RECENT;
  private int maxInternalQueueSize = DEFAULT_QUEUE_SIZE;
  private long reconnectInterval = DEFAULT_RECONNECT_INTERVAL;


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
   * @param keepMostRecent {@code true} for discarding old messages instead of recent ones,
   *                       otherwise use {@code false}
   */
  public RabbitMQConsumerOptions setKeepMostRecent(boolean keepMostRecent) {
    this.keepMostRecent = keepMostRecent;
    return this;
  }


  /**
   * @param maxInternalQueueSize the size of internal queue
   */
  public RabbitMQConsumerOptions setMaxInternalQueueSize(int maxInternalQueueSize) {
    this.maxInternalQueueSize = maxInternalQueueSize;
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
   * @return the size of internal queue
   */
  public int getMaxInternalQueueSize() {
    return maxInternalQueueSize;
  }

  /**
   * @return {@code true} if old messages will be discarded instead of recent ones,
   * otherwise use {@code false}
   */
  public boolean isKeepMostRecent() {
    return keepMostRecent;
  }

  public long getReconnectInterval() {
    return reconnectInterval;
  }

  public RabbitMQConsumerOptions setReconnectInterval(long reconnectInterval) {
    this.reconnectInterval = reconnectInterval;
    return this;
  }
  
  
}
