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
package io.vertx.rabbitmq.performance;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQChannelBuilder;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQPublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author jtalbut
 */
public class Publisher implements RabbitMQPublisherStresser {

  private static final Logger log = LoggerFactory.getLogger(Publisher.class);

  private RabbitMQConnection connection;
  private RabbitMQPublisher publisher;
  private final boolean withRetries;

  private String exchange;

  public Publisher(boolean withRetries) {
    this.withRetries = withRetries;
  }

  @Override
  public String getName() {
    return "Future publisher 2 " + (withRetries ? "with" : "without") + " retries";
  }

  private void channelOpenHandler(Channel rawChannel) throws IOException {
    rawChannel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null);
  }

  @Override
  public Future<Void> init(RabbitMQConnection connection, String exchange) {
    this.connection = connection;
    this.exchange = exchange;
    return connection.createChannelBuilder()
            .withChannelOpenHandler(this::channelOpenHandler)
            .createPublisher(exchange, RabbitMQChannelBuilder.BYTE_ARRAY_MESSAGE_CODEC, new RabbitMQPublisherOptions().setResendOnReconnect(withRetries))
            .onSuccess(pub -> this.publisher = pub)
            .mapEmpty()
            ;
  }

  @Override
  public Future<Void> shutdown() {
    return publisher.cancel();
  }

  @Override
  public Future<Void> runTest(long iterations) {
    List<Future<?>> confirmations = new ArrayList<>();
    for (long i = 0; i < iterations; ++i) {
      String idString = "MSG:" + Long.toString(i);
      confirmations.add(
              publisher.publish(""
                      , new AMQP.BasicProperties.Builder()
                              .messageId(Long.toString(i))
                              .build()
                      , idString.getBytes()
              )
      );
    }
    return Future
            .all(confirmations)
            .compose(cf -> {
              return Future.<Void>succeededFuture();
            });
  }

}
