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

import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageCodec;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQConnection {
  
  RabbitMQChannel createChannel();
  
  /**
   * Returns a strictly increasing identifier of the underlying connection (essentially a number that is incremented each time the connection is reconnected).
   * @return an identifier of the underlying connection.
   */
  long getConnectionInstance();
  
  String getConnectionName();
  
  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage, int timeout);
    
  /**
   * Register a message codec.
   * 
   * You can register a message codec if you want to send any non standard message to the broker. E.g. you might want to send POJOs directly to the broker.
   * 
   * The MessageCodecs used by the RabbitMQ client are compatible with those used by the Vert.x EventBus
   * , but there are some differences that mean the default implementations are not the same implementations for both:
   * 
   * <ul>
   * <li>The EventBus codecs are natively working with a Buffer that may contain more bytes than the message being processed
   * RabbitMQ messages are natively considered to be byte arrays, so the Buffer seen by a codec is guaranteed to only be used for that purpose.
   * <li>EventBus messages are only ever seen by Vert.x clients.
   * RabbitMQ messages are written by other processes that could have nothing to do with Vert.x.
   * </ul>
   * 
   * The result of these differences is that the RabbitMQ JSON codecs cannot prefix the JSON data with a length value (as the Event Bus Json MessageCodecs do), because that is not
   * useful and is not the expected behaviour when handling JSON data in a RabbitMQ message.
   * 
   * Most of the message codecs for base types are the same (i.e. this library uses the EventBus MessageCodecs for most base types)
   * , however the logic of the RabbitMQ Boolean MessageCodec is the inverse of the Event Bus Boolean Message Codec - the Rabbit MQ codec uses 1 for true and 0 for false.
   * RabbitMQ messages tend not to consist of just a single base value, but they can (as long as the recipient expects big-endian values).
   * 
   * @param <T> The type that the codec can encode and decode.
   * @param codec the message codec to register
   * @return a reference to this, so the API can be used fluently
   */
  <T> RabbitMQConnection registerCodec(MessageCodec<T,?> codec);
  
  <T> RabbitMQConnection unregisterCodec(String name);
  
  <T> RabbitMQConnection registerDefaultCodec(Class<T> clazz, MessageCodec<T,?> codec);
  
  <T> RabbitMQConnection unregisterDefaultCodec(Class<T> clazz);
  
}
