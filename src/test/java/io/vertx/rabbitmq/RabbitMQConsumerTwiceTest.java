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
package io.vertx.rabbitmq;

import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;


@RunWith(VertxUnitRunner.class)
public class RabbitMQConsumerTwiceTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerTwiceTest.class);

  /**
   * This test verifies that the RabbitMQConsumer cannot be used twice on the same channel.
   */
  private final String TEST_EXCHANGE = this.getClass().getName() + "Exchange";
  private final String TEST_QUEUE = this.getClass().getName() + "Queue";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = false;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = true;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_DURABLE = false;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE = true;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE = true;

  private final Network network;
  private final GenericContainer networkedRabbitmq;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<byte[]> consumer;
  
  public RabbitMQConsumerTwiceTest() throws IOException {
    logger.info("Constructing");
    this.network = RabbitMQBrokerProvider.getNetwork();
    this.networkedRabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(networkedRabbitmq.getMappedPort(5672));
    options.setConnectionTimeout(500);
    options.setNetworkRecoveryInterval(500);
    options.setRequestedHeartbeat(1);
    options.setConnectionName(this.getClass().getSimpleName());
    // Disable Java RabbitMQ client library reconnections
    options.setAutomaticRecoveryEnabled(false);
    // Enable vertx RabbitMQClient reconnections
    options.setReconnectAttempts(Integer.MAX_VALUE);
    return options;
  }
  
  @Before
  public void setup() throws Exception {
    this.connection = RabbitMQClient.create(vertx, getRabbitMQOptions());
  }

  @Test(timeout = 1 * 60 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    Async async = ctx.async();
    
    conChannel = connection.createChannel();    
    createAndStartConsumer(conChannel)
            .onComplete(ar -> {
              // First one should have succeeded
              if (ar.failed()) {
                ctx.fail(ar.cause());
              } else {
                ctx.assertNotNull(ar.result());
              }
            })
            .compose(v -> {
              return consumer.consume(false, null)
                      .onComplete(ar -> {
                        // Second one should have failed
                        if (ar.succeeded()) {
                          ctx.fail("Should have failed to create second consumer on the same channel");
                        } else {
                          logger.info("Expected failure: ", ar.cause());
                          conChannel.close()
                                  .compose(v2 -> connection.close())
                                  .onComplete(ar2 -> async.complete())
                                  ;
                        }
                      });
            });
    
  }

  private Future<String> createAndStartConsumer(RabbitMQChannel channel) {   
    channel.addChannelEstablishedCallback(p -> {
      channel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    
    consumer = channel.createConsumer(TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      channel.basicAck(message.consumerTag(), message.envelope().getDeliveryTag(), false);
    });
    return consumer.consume(false, null)
            .onSuccess(tag -> logger.info("Consumer started: {}", tag))
            ;
  }
  
}
