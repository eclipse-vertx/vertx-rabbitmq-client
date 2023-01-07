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
package io.vertx.rabbitmq.impl;

import com.rabbitmq.client.ConfirmListener;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import java.io.IOException;

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
    logger.debug("handleAck(" + deliveryTag + ", " + multiple + ")");
    this.handlerContext.runOnContext(v -> handleAck(deliveryTag, multiple, true));
  }

  @Override
  public void handleNack(long deliveryTag, boolean multiple) throws IOException {
    logger.debug("handleNack(" + deliveryTag + ", " + multiple + ")");
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
