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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.function.Supplier;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQConnection {
  
  /**
   * Open a channel.
   * @return A Future that will be completed once the channel has been established.
   */
  Future<RabbitMQChannel> openChannel();
  
  /**
   * Open a channel with a handler that will be called each time the channel is (re)opened.
   * 
   * This handler must be idempotent - it will be called each time a connection is established, which may be multiple times against the same instance.
   *
   * The handler should be used to establish any Rabbit MQ server objects that are required - exchanges, queues, bindings, etc.
   * The instance of the RabbitMQChannel will be passed into the handler and the handler must complete the Future that it returns in order for processing to continue.
   * If the Future returned by the handler fails the RabbitMQClient will be unable to make a connection (it will attempt to connect again according to its retry configuration).
   * If the Future is not completed by the handler the RabbitMQClient will not start (it will hang indefinitely).
   *
   * Other methods on the channel may be used in the callback -
   * it is specifically expected that RabbitMQ objects will be declared, but the publish and consume methods must not be used.
   *
   * The channel opened handler is particularly important with the RabbitMQPublisher and RabbitMQConsumer when they are used with
   * servers that may failover to another instance of the server that does not have the same exchanges/queues configured on it.
   * In this situation these handlers are the only opportunity to create exchanges, queues and bindings before the client will attempt to use them when it
   * re-establishes connection.
   * If your failover cluster is guaranteed to have the appropriate objects already configured then it is not necessary to use the handler 
   * (though it is a convenient place to declare objects anyway).
   * 
   * @param channelOpenedHandler handler to be called whenever a new channel is opened.
   * @return A Future that will be completed once the channel has been established and the callback has completed.
   */
  Future<RabbitMQChannel> openChannel(AsyncHandler<RabbitMQChannel> channelOpenedHandler);

  /**
   * Creates a RabbitMQPublisher (using a new channel on this connection) that reliably sends messages.
   * @param <T> The type of data that will be passed in to the Publisher.
   * @param channelOpenedHandler A handler that should be used to create the Rabbit MQ server objects required by this publisher (at least the exchange).
   * May be null if the server objects are configured externally.
   * @param codec The codec that will be used to encode the messages passed in to the Publisher.
   * If set to null the 
   * @param exchange The exchange that messages are to be sent to.
   * @param options Options for configuring the publisher.
   * @return a RabbitMQPublisher on this channel that reliably sends messages.
   */
  <T> Future<RabbitMQPublisher<T>> createPublisher(
          @Nullable AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , @Nullable RabbitMQMessageCodec<T> codec
          , String exchange
          , RabbitMQPublisherOptions options
  );

  /**
   * Create a RabbitMQConsumer (using a new channel on this connection) that reliably receives messages.
   * @param channelOpenedHandler A handler that should be used to create the Rabbit MQ server objects required by this consumer (at least the queue, typically the exchange and binding too).
   * May be null if the server objects are configured externally.
   * @param queue The queue that messages are being pushed from.
   * @param options Options for configuring the consumer.
   * @param messageHandler Handler for messages as they arrive.
   * @return a RabbitMQConsumer on this channel that can reliably receives messages.
   * After being constructed and configured the RabbitMQConsumer should be passed to the basicConsume method.
   */
  Future<RabbitMQConsumer<byte[]>> createConsumer(
          @Nullable AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , String queue
          , RabbitMQConsumerOptions options
          , Handler<RabbitMQMessage<byte[]>> messageHandler
  );
  
  /**
   * Create a RabbitMQConsumer (using a new channel on this connection) that reliably receives messages.
   * @param <T> The type of data that will be received by the Consumer.
   * @param channelOpenedHandler A handler that should be used to create the Rabbit MQ server objects required by this consumer (at least the queue, typically the exchange and binding too).
   * May be null if the server objects are configured externally.
   * @param codec The codec that will be used to decode the messages received by the Consumer.
   * @param queue The queue that messages are being pushed from.
   * @param queueNameSuppler Functional supplier of the queue name, if this is non-null the queue parameter will be ignored.
   * @param options Options for configuring the consumer.
   * @param messageHandler Handler for messages as they arrive.
   * @return a RabbitMQConsumer on this channel that can reliably receives messages.
   * After being constructed and configured the RabbitMQConsumer should be passed to the basicConsume method.
   */
  <T> Future<RabbitMQConsumer<T>> createConsumer(
          @Nullable AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , RabbitMQMessageCodec<T> codec
          , String queue
          , Supplier<String> queueNameSuppler
          , RabbitMQConsumerOptions options
          , Handler<RabbitMQMessage<T>> messageHandler
  );
  
  /**
   * Returns a strictly increasing identifier of the underlying connection (a number that is incremented each time the connection is reconnected).
   * @return an identifier of the underlying connection.
   */
  long getConnectionInstance();
  
  String getConnectionName();
  
  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage, int timeout);
    
}
