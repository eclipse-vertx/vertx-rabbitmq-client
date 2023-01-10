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

import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;

/**
 * Convenience class providing a default implementation of {@link Consumer}.
 * <p>
 * This is the equivalent to the {@link com.rabbitmq.client.DefaultConsumer}, but it uses a RabbitMQChannel instead of a Channel.
 * Unlike the {@link com.rabbitmq.client.DefaultConsumer} this class is abstract and users are required to implement handleDelivery.
 * <p>
 * This class is for uses calling basicConsume directly, it is not required for users of the RabbitMQConsumer.
 * <p>
 * The class stores the {@link RabbitMQChannel} but does not use it internally.
 *<p>
 * The consumerTag is captured when the handleConsumeOk callback is called.
 * 
 * @author jtalbut
 */
public abstract class DefaultConsumer implements Consumer {
  
  private final RabbitMQChannel channel;
  private volatile String consumerTag;

  /**
   * Constructor.
   * 
   * @param channel The channel that was used to call basicConsume.
   * This channel is not used internally, it is stored purely for convenience.
   */
  public DefaultConsumer(RabbitMQChannel channel) {
    this.channel = channel;
  }

  /**
   * Implementation of {@link com.rabbitmq.client.Consumer#handleConsumeOk(java.lang.String)} that captures the consumerTag.
   * 
   * @param consumerTag the consumer tag associated with the consumer.
   */
  @Override
  public void handleConsumeOk(String consumerTag) {
    this.consumerTag = consumerTag;
  }

  /**
   * No-op implementation of {@link com.rabbitmq.client.Consumer#handleCancelOk(java.lang.String)}.
   * 
   * @param consumerTag the consumer tag associated with the consumer.
   */
  @Override
  public void handleCancelOk(String consumerTag) {
  }

  /**
   * No-op implementation of {@link com.rabbitmq.client.Consumer#handleCancel(java.lang.String)}.
   * 
   * @param consumerTag the consumer tag associated with the consumer.
   */
  @Override
  public void handleCancel(String consumerTag) throws IOException {
  }

  /**
   * No-op implementation of {@link com.rabbitmq.client.Consumer#handleShutdownSignal(java.lang.String, com.rabbitmq.client.ShutdownSignalException)}.
   * 
   * @param consumerTag the consumer tag associated with the consumer.
   * @param sig a {@link ShutdownSignalException} indicating the reason for the shut down.
   */
  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
  }

  /**
   * No-op implementation of {@link com.rabbitmq.client.Consumer#handleRecoverOk(java.lang.String)}.
   * 
   * @param consumerTag the consumer tag associated with the consumer.
   */
  @Override
  public void handleRecoverOk(String consumerTag) {
  }

  /**
   * Get the channel that the consumer is running on.
   * 
   * This class does make use of the channel at all.
   * 
   * @return the channel passed in to the constructor.
   */
  public RabbitMQChannel getChannel() {
    return channel;
  }

  /**
   * Get the consumer tag associated with this consumer.
   * 
   * This is the consumer tag passed in to the last call to handleConsumeOk.
   * 
   * @return the consumer tag associated with this consumer.
   */
  public String getConsumerTag() {
    return consumerTag;
  }
  
}
