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

import com.rabbitmq.client.AMQP;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQFuturePublisher {
  
  /**
   * Publish a message. 
   * 
   * @param routingKey
   * @param properties
   * @param body
   * @return A Future that will be completed when the message is confirmed, or failed if the channel is broken.
   * @see com.rabbitmq.client.Channel#basicPublish(String, String, AMQP.BasicProperties, byte[])
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> publish(String routingKey, AMQP.BasicProperties properties, Buffer body);

  /**
   * Publish a message. 
   * 
   * @param routingKey
   * @param properties
   * @param body
   * @return A Future that will be completed when the message is confirmed, or failed if the channel is broken.
   * @see com.rabbitmq.client.Channel#basicPublish(String, String, AMQP.BasicProperties, byte[])
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> publish(String routingKey, AMQP.BasicProperties properties, byte[] body);

}
