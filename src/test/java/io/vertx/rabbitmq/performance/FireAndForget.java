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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author jtalbut
 */
public class FireAndForget implements RabbitMQPublisherStresser {

  private final RabbitMQConnection connection;
  private RabbitMQChannel channel;
  private final AtomicLong counter = new AtomicLong();
  private String exchange;

  public FireAndForget(RabbitMQConnection connection) {
    this.connection = connection;
  }
  
  @Override
  public String getName() {
    return "Fire and forget (no message confirmation)";
  }

  @Override
  public Future<Void> init(String exchange) {
    this.exchange = exchange;
    return connection.createChannelBuilder().openChannel()
            .compose(chann -> {
              this.channel = chann;
              return channel.getManagementChannel().exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null);
            })
            ;
  }

  @Override
  public Future<Void> shutdown() {
    return channel.close();
  }

  @Override
  public Future<Void> runTest(long iterations) {
    counter.set(iterations);
    long iter;
    List<Future> futures = new ArrayList<>();
    while((iter = counter.decrementAndGet()) > 0) {
      Future pubFuture = channel.basicPublish(new RabbitMQPublishOptions(), exchange, "", true, new AMQP.BasicProperties(), Long.toString(iter).getBytes());
      futures.add(pubFuture);
    }
    return CompositeFuture.all(futures).mapEmpty();
  }
  
}
