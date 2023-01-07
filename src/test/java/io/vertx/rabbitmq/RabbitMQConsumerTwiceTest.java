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
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;


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

  private static final GenericContainer container = RabbitMQBrokerProvider.getRabbitMqContainer();
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<byte[]> consumer;
  
  public RabbitMQConsumerTwiceTest() throws IOException {
    logger.info("Constructing");
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(container.getMappedPort(5672));
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
  public void setup(TestContext testContext) throws Exception {
    RabbitMQClient.connect(vertx, getRabbitMQOptions())
            .onSuccess(conn -> {
              this.connection = conn;
            })
            .onComplete(testContext.asyncAssertSuccess());
  }
  
  @AfterClass
  public static void shutdown() {
    container.stop();
  }

  @Test(timeout = 1 * 20 * 1000L)
  public void testCallToConsumerConsume(TestContext testContext) throws Exception {
    
    connection.openChannel()
            .compose(chann -> {
              conChannel = chann;
              return createAndStartConsumer(chann);
            })
            .onFailure(ex -> {
              testContext.fail(ex);
            })
            .compose(v -> {
              return consumer.consume(false, null)
                      .onSuccess(v2 -> {
                        testContext.fail("Should have failed to create second consumer on the same channel");
                      })
                      .recover(ex -> {
                        logger.info("Expected failure: ", ex);
                        return Future.succeededFuture();
                      });
            })
            .compose(v2 -> {
              return consumer.cancel();
            })
            .compose(v2 -> {
              return conChannel.close();
            })
            .compose(v2 -> {
              return connection.close();
            })
            .onComplete(testContext.asyncAssertSuccess());
    
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
