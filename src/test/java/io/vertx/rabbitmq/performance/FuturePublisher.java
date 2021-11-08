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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQFuturePublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import io.vertx.rabbitmq.impl.RabbitMQChannelImpl;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class FuturePublisher implements RabbitMQPublisherStresser {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQChannelImpl.class);
  
  private final RabbitMQChannel channel;
  private String exchange;
  private RabbitMQFuturePublisher publisher;

  public FuturePublisher(RabbitMQConnection connection) {
    this.channel = connection.createChannel();
  }
  
  @Override
  public String getName() {
    return "Future publisher";
  }

  @Override
  public Future<Void> init(String exchange) {
    this.exchange = exchange;
    channel.addChannelEstablishedCallback(promise -> {
      channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null)
              .onComplete(promise)
              ;
    });
    publisher = channel.createFuturePublisher(exchange, new RabbitMQPublisherOptions().setMaxInternalQueueSize(1000000));
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> runTest(long iterations) {
    List<Future> confirmations = new ArrayList<>();
    for (long i = 0; i < iterations; ++i) {
      String idString = Long.toString(i);
      confirmations.add(
              publisher.publish(""
                      , new AMQP.BasicProperties.Builder()
                              .messageId(Long.toString(i))
                              .build()
                      , idString.getBytes()
              )
      );
    }
    return CompositeFuture
            .all(confirmations)
            .compose(cf -> Future.<Void>succeededFuture())
            ;
  }
  
}
