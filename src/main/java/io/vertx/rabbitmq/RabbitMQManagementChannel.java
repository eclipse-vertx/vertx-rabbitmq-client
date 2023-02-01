/*
 * Copyright 2023 Eclipse.
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

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Consumer;
import io.vertx.core.Future;
import java.util.Map;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQManagementChannel {
    
  /**
   * Returns an identifier of the underlying channel.
   * This is made up of the connection instance and the channel number.
   * @return an identifier of the underlying channel.
   */
  String getChannelId();
    
  /**
   * Abort this channel.
   *
   * Forces the channel to close and waits (on blocking thread) for the close operation to complete.
   * Any encountered exceptions in the close operation are silently discarded.
   * 
   * @param closeCode The close code (such as {@link com.rabbitmq.client.AMQP#REPLY_SUCCESS}).
   * @param closeMessage The close message (such as "OK");
   * @return a Future that will be completed when the channel has aborted.
   */
  Future<Void> abort(int closeCode, String closeMessage);
  
  Future<Void> exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, Map<String,Object> arguments);
  
  Future<Void> exchangeDeclarePassive(String exchange);

  Future<Void> exchangeBind(String destination, String source, String routingKey, Map<String,Object> arguments);
  
  Future<String> queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String,Object> arguments);
  
  Future<Void> queueDeclarePassive(String queue);
          
  Future<Void> queueBind(String queue, String exchange, String routingKey, Map<String,Object> arguments);
  
  Future<Void> confirmSelect();

  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage);
    
}
