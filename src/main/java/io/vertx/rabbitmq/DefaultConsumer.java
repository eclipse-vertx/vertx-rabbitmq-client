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
 * This is the equivalent to the com.rabbitmq.client.DefaultConsumer, but it uses a RabbitMQChannel instead of a Channel.
 * Unlike the com.rabbitmq.client.DefaultConsumer this class is abstract and users are required to implement handleDelivery.
 * @author jtalbut
 */
public abstract class DefaultConsumer implements Consumer {
  
  private final RabbitMQChannel channel;
  private volatile String consumerTag;

  public DefaultConsumer(RabbitMQChannel channel) {
    this.channel = channel;
  }

  @Override
  public void handleConsumeOk(String consumerTag) {
    this.consumerTag = consumerTag;
  }

  @Override
  public void handleCancelOk(String consumerTag) {
  }

  @Override
  public void handleCancel(String consumerTag) throws IOException {
  }

  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
  }

  @Override
  public void handleRecoverOk(String consumerTag) {
  }

  public RabbitMQChannel getChannel() {
    return channel;
  }

  public String getConsumerTag() {
    return consumerTag;
  }
  
}
