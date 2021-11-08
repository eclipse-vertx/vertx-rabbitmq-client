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
package io.vertx.rabbitmq.performance;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConnection;
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
      channel.basicPublish(exchange, "", true, new AMQP.BasicProperties(), Long.toString(iter).getBytes())
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
