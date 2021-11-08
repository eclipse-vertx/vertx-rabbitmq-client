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
 * Aimed to specify queue consumer settings when calling {@link RabbitMQClient#basicConsumer(String, QueueOptions, Handler)}
 */
@DataObject(generateConverter = true)
public class RabbitMQConsumerOptions {

  private static final int DEFAULT_QUEUE_SIZE = Integer.MAX_VALUE;
  private static final boolean DEFAULT_AUTO_ACK = true;
  private static final boolean DEFAULT_KEEP_MOST_RECENT = false;

  private boolean autoAck = DEFAULT_AUTO_ACK;
  private boolean keepMostRecent = DEFAULT_KEEP_MOST_RECENT;
  private int maxInternalQueueSize = DEFAULT_QUEUE_SIZE;


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
}
