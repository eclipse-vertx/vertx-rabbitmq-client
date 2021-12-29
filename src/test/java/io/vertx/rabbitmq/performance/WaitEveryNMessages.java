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
public class WaitEveryNMessages implements RabbitMQPublisherStresser {

  private final RabbitMQChannel channel;
  private final AtomicLong counter = new AtomicLong();
  private String exchange;
  private long n;

  public WaitEveryNMessages(RabbitMQConnection connection, long n) {
    this.channel = connection.createChannel();
    this.n = n;
  }
  
  @Override
  public String getName() {
    return "Wait after " + n + " messages";
  }

  @Override
  public Future<Void> init(String exchange) {
    this.exchange = exchange;
    return channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null)
            .compose(v -> channel.confirmSelect())
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
    return promise.future()
            .compose(v -> channel.waitForConfirms(10000));
  }
  
  private void runIteration(Promise<Void> promise) {
    long iter = counter.decrementAndGet();
    if (iter <= 0) {
      promise.complete();
    } else {
      channel.basicPublish(new RabbitMQPublishOptions(), exchange, "", true, new AMQP.BasicProperties(), Long.toString(iter).getBytes())
              .compose(v -> {
                if (iter % n == 0) {
                  return channel.waitForConfirms(100000);
                } else {
                  return Future.succeededFuture();
                }
              })
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
