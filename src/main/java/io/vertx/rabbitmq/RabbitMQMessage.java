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

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Envelope;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Represent a message received message received in a rabbitmq-queue.
 */
@VertxGen
public interface RabbitMQMessage {
  
  /**
   * @return the message body
   */
  @CacheReturn
  Buffer body();

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
}
