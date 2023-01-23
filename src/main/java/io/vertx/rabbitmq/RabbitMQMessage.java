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

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Represent a message received message received in a rabbitmq-queue.
 */
@VertxGen
public interface RabbitMQMessage<T> {
  
  /**
   * @return the message body
   */
  @CacheReturn
  T body();

  /**
   * @return the <i>consumer tag</i> associated with the consumer
   */
  @CacheReturn
  String consumerTag();

  /**
   * @return packaging data for the message
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @CacheReturn
  Envelope envelope();

  /**
   * @return content header data for the message
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @CacheReturn
  BasicProperties properties();

  /**
   * @return the message count for messages obtained with {@link RabbitMQClient#basicGet(String, boolean, Handler)}
   */
  @CacheReturn
  Integer messageCount();
  
  RabbitMQChannel getChannel();
  
  Future<Void> basicAck();
  
  Future<Void> basicNack();
  
}
