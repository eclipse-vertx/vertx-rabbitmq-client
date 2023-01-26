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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author jtalbut
 */
public class WaitOnEachMessage implements RabbitMQPublisherStresser {
  
  private RabbitMQConnection connection;
  private RabbitMQChannel channel;
  private final AtomicLong counter = new AtomicLong();
  private String exchange;

  public WaitOnEachMessage() {
  }
  
  @Override
  public String getName() {
    return "Wait on each message";
  }

  @Override
  public Future<Void> init(RabbitMQConnection connection, String exchange) {
    this.connection = connection;
    this.exchange = exchange;
    return connection.createChannelBuilder().openChannel()
            .compose(chann -> {
              this.channel = chann;
              return channel.getManagementChannel().exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null);
            })
            .compose(v -> channel.getManagementChannel().confirmSelect())
            ;
  }

  @Override
  public Future<Void> shutdown() {
    return channel.close();
  }
  
  @Override
  public Future<Void> runTest(long iterations) {
    Promise<Void> promise = Promise.promise();
    counter.set(iterations);
    runIteration(promise);
    return promise.future();
  }
  
  private void runIteration(Promise<Void> promise) {
    long iter = counter.decrementAndGet();
    if (iter <= 0) {
      promise.complete();
    } else {
      channel.basicPublish(new RabbitMQPublishOptions().setWaitForConfirm(true), exchange, "", true, new AMQP.BasicProperties(), Long.toString(iter).getBytes())
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  runIteration(promise);
                } else {
                  promise.fail(ar.cause());
                }
              });
    }
  }
  
}
