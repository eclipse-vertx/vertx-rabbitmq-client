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

import com.rabbitmq.client.AMQP;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQPublisher<T> {
  
  /**
   * Publish a message. 
   * 
   * @param routingKey the routing key
   * @param properties other properties for the message - routing headers etc
   * @param body the message body
   * @return A Future that will be completed when the message is confirmed, or failed if the channel is broken.
   * @see com.rabbitmq.client.Channel#basicPublish(String, String, AMQP.BasicProperties, byte[])
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> publish(String routingKey, AMQP.BasicProperties properties, T body);

  /**
   * Prevent any future asynchronous behaviour.
   * Any deliveries that are pending confirmations will be discarded and no attempt will be made to resend any messages.
   * Note that this does not disable any confirmations coming from RabbitMQ and it is not necessary to restart the publisher to send messages again.
   * If confirmations are received from RabbitMQ for deliveries that have been discarded they will be logged, but otherwise not do anything.
   * @return A Future that will be completed when the publisher has stopped.
   */
  Future<Void> stop();
  
}
