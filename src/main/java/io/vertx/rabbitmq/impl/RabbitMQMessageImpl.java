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
package io.vertx.rabbitmq.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.vertx.rabbitmq.RabbitMQMessage;

public class RabbitMQMessageImpl<T> implements RabbitMQMessage<T> {

  private T body;
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
  RabbitMQMessageImpl(T body, String consumerTag, com.rabbitmq.client.Envelope envelope, AMQP.BasicProperties properties, Integer messageCount) {
    this.body = body;
    this.consumerTag = consumerTag;
    this.envelope = envelope;
    this.properties = properties;
    this.messageCount = messageCount;
  }

  @Override
  public T body() {
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
