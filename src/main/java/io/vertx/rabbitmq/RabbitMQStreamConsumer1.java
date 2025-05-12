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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * A stream of messages from a rabbitmq queue.
 */
@VertxGen
public interface RabbitMQStreamConsumer1<T> extends ReadStream<RabbitMQMessage<T>> {

  /**
   * Begin consuming from the queue.
   * The RabbitMQConsumer mandates the values used for many of the arguments to basicConsume, with the exception of exclusive and arguments.
   *
   * consume can only be called once for a given instance.
   * Furthermore only one RabbitMQConsumer can be used per RabbitMQChannel (because the channelId is used as the consumerTag).
   * It would be possible to work around this, but it would involve tracking a lot of extra state and channels are cheap.
   *
   * @param exclusive true if this is an exclusive consumer.
   * See <a href="https://www.rabbitmq.com/consumers.html#exclusivity">https://www.rabbitmq.com/consumers.html#exclusivity</a>.
   * It is recommended that this be set to false, be sure you understand the implications and have read
   * <a href="https://www.rabbitmq.com/consumers.html#single-active-consumer">https://www.rabbitmq.com/consumers.html#single-active-consumer</a> before setting to true.
   * @param arguments a set of arguments for the consume
   * Set to null unless there is a good reason not to.
   * @return A Future containing either the consumerTag associated with the new consumer or a failure.
   */
  // Future<String> consume(boolean exclusive, Map<String, Object> arguments);

  /**
   * Get the channel that is dedicated to this consumer.
   * @return the channel that is dedicated to this consumer.
   */
  @GenIgnore
  RabbitMQChannel getChannel();

  /**
   * Set an exception handler on the read stream.
   *
   * @param exceptionHandler the exception handler
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  RabbitMQStreamConsumer1<T> exceptionHandler(Handler<Throwable> exceptionHandler);

  /**
   * Set a message handler. As message appear in a queue, the handler will be called with the message.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  RabbitMQStreamConsumer1<T> handler(Handler<RabbitMQMessage<T>> messageArrived);

  /**
   * Pause the stream of incoming messages from queue.
   * <p>
   * The messages will continue to arrive, but they will be stored in a internal queue.
   * If the queue size would exceed the limit provided by {@link RabbitMQStreamConsumer1#size(int)}, then incoming messages will be discarded.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  RabbitMQStreamConsumer1<T> pause();

  /**
   * Resume reading from a queue. Flushes internal queue.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  RabbitMQStreamConsumer1<T> resume();

  /**
   * Set an end handler. Once the stream has cancelled successfully, the handler will be called.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  RabbitMQStreamConsumer1<T> endHandler(Handler<Void> endHandler);

  /**
   * @return a consumer tag
   */
  String consumerTag();

  /**
   * Stop message consumption from a queue.
   * <p>
   * The operation is asynchronous. When consumption is stopped, you can also be notified via {@link RabbitMQStreamConsumer1#endHandler(Handler)}.
   * The channel that was opened for this consumer will be closed.
   *
   * @return a future through which you can find out the operation status.
   */
  Future<Void> cancel();

  /**
   * Return {@code true} if cancel() has been called.
   * @return {@code true}  if cancel() has been called.
   */
  boolean isCancelled();

  /**
   * @return is the stream paused?
   */
  boolean isPaused();

  /**
   * Fetch the specified {@code amount} of elements. If the {@code ReadStream} has been paused, reading will
   * recommence with the specified {@code amount} of items, otherwise the specified {@code amount} will
   * be added to the current stream demand.
   *
   * @return a reference to this, so the API can be used fluently
   */
  RabbitMQStreamConsumer1<T> fetch(long amount);

}
