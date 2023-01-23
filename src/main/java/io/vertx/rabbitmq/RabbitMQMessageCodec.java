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

/**
 * A message codec.
 * <p>
 * You can register a message codec if you want to send any non standard message to the broker. E.g. you might want to send POJOs
 * directly to the broker.
 * <p>
 * The MessageCodecs used by the RabbitMQ client are similar to those used by the Vert.x EventBus (@link io.vertx.core.eventbus.MessageCodec)
 * , but there are some differences that mean that they are not directly compatible:
 * <ul>
 * <li>The EventBus codecs are natively working with a Buffer that may contain more bytes than the message being processed
 * RabbitMQ messages are natively considered to be byte arrays, so the Buffer seen by a codec is guaranteed to only be used for
 * that purpose.
 * <li>EventBus messages are only ever seen by Vert.x clients. RabbitMQ messages are written by other processes that could have
 * nothing to do with Vert.x.
 * </ul>
 * <p>
 * One result of these differences is that the RabbitMQ JSON codecs cannot prefix the JSON data with a length value (as the Event
 * Bus Json MessageCodecs do), because that is not useful for RabbitMQ and is not the expected behaviour when handling JSON data in a RabbitMQ
 * message.
 * <p>
 * Most of the message codecs for base types are the same (i.e. this library uses the EventBus MessageCodecs for most base types)
 * , however the logic of the RabbitMQ Boolean MessageCodec is the inverse of the Event Bus Boolean Message Codec - the Rabbit MQ
 * codec uses 1 for true and 0 for false. 
 * RabbitMQ messages tend not to consist of just a single base value, but they can (as long as the recipient expects big-endian values).
 * <p>
 * Codec configuration is scoped to the RabbitMQChannel.
 * <p>
 * Codecs are registered with {@link RabbitMQChannleBuilder#withCodec} and  {@link RabbitMQChannleBuilder#withDefaultCodec}
 * , but may also be explicitly specified when creating a {@link RabbitMQConsumer} or {@link RabbitMQPublisher}.
 * <p>
 * When publishing messages a different codec may be used for each message, but when consuming messages a single codec has to be 
 * configured for the consumer (and all messages delivered by that consumer will use the same codec).
 * It is possible for the author of a consuming class to use information in the message properties to select a codec and explicitly 
 * convert the content but this cannot be done in a generic way and is thus not attempted by this library.
 * An example is provided.
 * <p>
 * The {@link io.vertx.rabbitmq.impl.codecs.RabbitMQByteArrayMessageCodec} is effectively a no-op codec and is the default.
 * <p>
 * @author jtalbut
 */
public interface RabbitMQMessageCodec<T> {

  public String codecName();

  public byte[] encodeToBytes(T value);

  public T decodeFromBytes(byte[] data);

  public String getContentType();

  public String getContentEncoding();

}
