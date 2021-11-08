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
