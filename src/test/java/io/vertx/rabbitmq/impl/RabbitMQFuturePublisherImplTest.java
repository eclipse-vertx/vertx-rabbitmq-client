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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rabbitmq.RabbitMQBrokerProvider;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQMessage;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import io.vertx.rabbitmq.impl.codecs.RabbitMQLongMessageCodec;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQFuturePublisherImplTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQFuturePublisherImplTest.class);

  /**
   * This test verifies that the RabbitMQ Java client reconnection logic works as long as the vertx reconnect attempts is set to zero.
   *
   * The change that makes this work is in the basicConsumer, where the shutdown handler is only set if retries > 0. 
   * Without that change the vertx client shutdown handler is called, 
   * interrupting the java client reconnection logic, even though the vertx reconnection won't work because retries is zero.
   *
   */
  private final String TEST_EXCHANGE = this.getClass().getName() + "Exchange";
  private final String TEST_QUEUE = this.getClass().getName() + "Queue";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = false;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE = false;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE = false;

  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();
  
  private RabbitMQPublisher<Long> publisher;
  private RabbitMQConsumer consumer;
  
  public RabbitMQFuturePublisherImplTest() throws IOException {
    logger.info("Constructing");
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  @BeforeClass
  public static void startup() {
    CONTAINER.start();
  }
  
  @AfterClass
  public static void shutdown() {
    CONTAINER.stop();
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(CONTAINER.getMappedPort(5672));
    options.setConnectionTimeout(500);
    options.setNetworkRecoveryInterval(500);
    options.setRequestedHeartbeat(60);
    options.setConnectionName(this.getClass().getSimpleName());
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

  @Test(timeout = 5 * 60 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    // Have to react to allMessagesSent completing in case it completes after the last message is received.
    allMessagesSent.future().onSuccess(count -> {
      synchronized(receivedMessages) {
        if (receivedMessages.size() == count) {
          allMessagesReceived.tryComplete();
        }
      }
    });
    
    createConsumer()
            .compose(v -> createPublisher())
            .compose(v -> sendMessages())
            .compose(v -> allMessagesSent.future())
            .compose(v -> allMessagesReceived.future())
            .compose(v -> publisher.cancel())
            .compose(v -> consumer.cancel())
            .compose(v -> connection.close())
            .onComplete(ctx.asyncAssertSuccess())
            ;
  }
  
  private Future<Void> createPublisher() {
    return connection.createChannelBuilder()
            .withChannelOpenHandler(chann -> {
              chann.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null);
            })
            .createPublisher(TEST_EXCHANGE, new RabbitMQLongMessageCodec(), new RabbitMQPublisherOptions())
            .onSuccess(pub -> publisher = pub)
            .mapEmpty()
            ;
  }

  
  private Future<Void> sendMessages() {
    AtomicLong counter = new AtomicLong();
    AtomicLong postCount = new AtomicLong(20);
    AtomicLong timerId = new AtomicLong();
    
    /*
    Send a message every second, with the message being a strictly increasing integer.
    */
    timerId.set(vertx.setPeriodic(100, v -> {
      long value = counter.incrementAndGet();
      logger.info("Publishing message {}", value);
      publisher.publish("", new AMQP.BasicProperties(), value)
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  logger.info("Published message {}", value);
                } else {
                  logger.warn("Failed to publish message {}: ", value, ar.cause());
                }
              });
      if (postCount.decrementAndGet() == 0) {
        vertx.cancelTimer(timerId.get());
        logger.info("All messages sent: {}", counter.get());
        allMessagesSent.complete(counter.get());
      }
    }));
    return Future.succeededFuture();
  }
  
  private Future<Void> messageHandler(RabbitMQConsumer consumer, RabbitMQMessage<Long> message) {
    Long index = message.body();
    synchronized(receivedMessages) {
      receivedMessages.add(index);
      logger.info("Received message: {} (have {})", index, receivedMessages.size());
      Future<Long> allMessagesSentFuture = allMessagesSent.future();
      if (allMessagesSentFuture.isComplete() && (receivedMessages.size() == allMessagesSentFuture.result())) {
        logger.info("All messages sents: {}", receivedMessages.size());
        allMessagesReceived.tryComplete();
      }
    }
    return message.basicAck();
  }
  
  private Future<Void> createConsumer() {
    return connection.createChannelBuilder()
            .withChannelOpenHandler(rawChannel -> {
              rawChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null);
              rawChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null);
              rawChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null);
            })
            .createConsumer(new RabbitMQLongMessageCodec(), TEST_QUEUE, null, new RabbitMQConsumerOptions(), this::messageHandler)            
            .onSuccess(con -> consumer = con)
            .mapEmpty()
            ;
  }
  
  
}
