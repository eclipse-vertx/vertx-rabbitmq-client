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
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import io.vertx.rabbitmq.impl.RabbitMQRepublishingPublisherImpl2;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class RepublishingPublisher2 implements RabbitMQPublisherStresser {

  private static final Logger log = LoggerFactory.getLogger(RepublishingPublisher2.class);
  
  private final Vertx vertx;
  private final RabbitMQChannel channel;
  private String exchange;
  private RabbitMQRepublishingPublisherImpl2 publisher;

  public RepublishingPublisher2(Vertx vertx, RabbitMQConnection connection) {
    this.vertx = vertx;
    this.channel = connection.createChannel();
  }
  
  @Override
  public String getName() {
    return "Reliable publisher";
  }

  @Override
  public Future<Void> init(String exchange) {
    this.exchange = exchange;
    channel.addChannelEstablishedCallback(promise -> {
      channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null)
              .compose(v -> channel.confirmSelect())
              .onComplete(promise)
              ;
    });
    publisher = new RabbitMQRepublishingPublisherImpl2(vertx, channel, exchange, new RabbitMQPublisherOptions().setMaxInternalQueueSize(1000000));
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> shutdown() {
    return channel.close();
  }
  
  @Override
  public Future<Void> runTest(long iterations) {
    List<Future> confirmations = new ArrayList<>();
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
    return CompositeFuture
            .all(confirmations)
            .compose(cf -> {
              return Future.<Void>succeededFuture();
            });
  }
  
}