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
package io.vertx.rabbitmq.impl;

import com.rabbitmq.client.ConfirmListener;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.rabbitmq.RabbitMQChannel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class RabbitMQConfirmListenerImpl implements ConfirmListener {

  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfirmListenerImpl.class);
  
  private final Context handlerContext;
  private final RabbitMQChannel channel;
  private final Handler<RabbitMQConfirmation> handler;

  @Override
  public void handleAck(long deliveryTag, boolean multiple) throws IOException {
    this.handlerContext.runOnContext(v -> handleAck(deliveryTag, multiple, true));
  }

  @Override
  public void handleNack(long deliveryTag, boolean multiple) throws IOException {
    this.handlerContext.runOnContext(v -> handleAck(deliveryTag, multiple, false));
  }
  
  public RabbitMQConfirmListenerImpl(RabbitMQChannel channel, Context context, Handler<RabbitMQConfirmation> handler) {
    this.handlerContext = context;
    this.channel = channel;
    this.handler = handler;
  }

  void handleAck(long deliveryTag, boolean multiple, boolean succeeded) {
    this.handler.handle(new RabbitMQConfirmation(channel.getChannelId(), deliveryTag, multiple, succeeded));
  }  

}
