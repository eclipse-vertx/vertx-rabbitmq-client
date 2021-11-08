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
package io.vertx.rabbitmq.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQMessage;

public class RabbitMQMessageImpl implements RabbitMQMessage {

  private Buffer body;
  private String consumerTag;
  private Envelope envelope;
  private BasicProperties properties;
  private Integer messageCount;

  /**
   * Construct a new message
   *
   * @param consumerTag the <i>consumer tag</i> associated with the consumer
   * @param envelope    packaging data for the message
   * @param properties  content header data for the message
   * @param body        the message body (opaque, client-specific byte array)
   */
  RabbitMQMessageImpl(byte[] body, String consumerTag, com.rabbitmq.client.Envelope envelope, AMQP.BasicProperties properties, Integer messageCount) {
    this.body = Buffer.buffer(body);
    this.consumerTag = consumerTag;
    this.envelope = envelope;
    this.properties = properties;
    this.messageCount = messageCount;
  }

  @Override
  public Buffer body() {
    return body;
  }

  @Override
  public String consumerTag() {
    return consumerTag;
  }

  @Override
  public Envelope envelope() {
    return envelope;
  }

  @Override
  public BasicProperties properties() {
    return properties;
  }

  @Override
  public Integer messageCount() {
    return messageCount;
  }
}
