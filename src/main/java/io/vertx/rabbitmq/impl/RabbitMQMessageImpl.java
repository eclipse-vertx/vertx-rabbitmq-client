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
import io.vertx.core.Future;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQMessage;

public class RabbitMQMessageImpl<T> implements RabbitMQMessage<T> {

  private final RabbitMQChannel channel;
  private final int channelNumber;
  private final T body;
  private final String consumerTag;
  private final Envelope envelope;
  private final BasicProperties properties;

  /**
   * Construct a new message
   *
   * @param channel     the RabbitMQChannel that received this message
   * @param consumerTag the <i>consumer tag</i> associated with the consumer
   * @param envelope    packaging data for the message
   * @param properties  content header data for the message
   * @param body        the message body (opaque, client-specific byte array)
   */
  public RabbitMQMessageImpl(RabbitMQChannel channel, int channelNumber, T body, String consumerTag, com.rabbitmq.client.Envelope envelope, AMQP.BasicProperties properties) {
    this.channel = channel;
    this.channelNumber = channelNumber;
    this.body = body;
    this.consumerTag = consumerTag;
    this.envelope = envelope;
    this.properties = properties;
  }

  @Override
  public RabbitMQChannel getChannel() {
    return channel;
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
  public Future<Void> basicAck() {
    return channel.basicAck(channelNumber, envelope.getDeliveryTag(), false);
  }

  @Override
  public Future<Void> basicNack(boolean requeue) {
    return channel.basicNack(channelNumber, envelope.getDeliveryTag(), false, requeue);
  }
  
  
}
