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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rabbitmq.impl.codecs.RabbitMQStringMessageCodec;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;


@RunWith(VertxUnitRunner.class)
public class RabbitMQClientDisconnectTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientDisconnectTest.class);

  /**
   * This test just does a clean start/stop of rabbitmq to check that there are no spurious errors logged.
   */
  private final String TEST_EXCHANGE = this.getClass().getName() + "Exchange";
  private final String TEST_QUEUE = this.getClass().getName() + "Queue";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = false;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = true;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_DURABLE = false;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE = true;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE = true;

  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();
  private Proxy proxy;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();
  
  private RabbitMQPublisher<String> publisher;
  private RabbitMQConsumer consumer;
  
  public RabbitMQClientDisconnectTest() throws IOException {
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
    options.setPort(proxy.getProxyPort());
    options.setConnectionTimeout(500);
    options.setNetworkRecoveryInterval(500);
    options.setRequestedHeartbeat(1);
    options.setConnectionName(this.getClass().getSimpleName());
    // Disable Java RabbitMQ client library reconnections
    options.setAutomaticRecoveryEnabled(false);
    // Enable vertx RabbitMQClient reconnections
    options.setReconnectAttempts(100);
    return options;
  }
  
  @Before
  public void setup(TestContext testContext) throws Exception {
    this.proxy = new Proxy(vertx, this.CONTAINER.getMappedPort(5672));
    this.proxy.startProxy();
    RabbitMQClient.connect(vertx, getRabbitMQOptions())
            .onSuccess(conn -> {
              this.connection = conn;
            })
            .onComplete(testContext.asyncAssertSuccess());
  }

  @After
  public void shutdownProxy() {
    this.proxy.stopProxy();
  }

  @Test(timeout = 1 * 30 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    Async async = ctx.async();
    
    createConsumer()
            .compose(v -> createPublisher())
            .onSuccess(v -> sendMessages())
            .onFailure(ctx::fail);
    
    // Have to react to allMessagesSent completing in case it completes after the last message is received.
    allMessagesSent.future().onSuccess(count -> {
      synchronized(receivedMessages) {
        if (receivedMessages.size() == count) {
          allMessagesReceived.tryComplete();
        }
      }
    });
    
    allMessagesSent.future()
            .compose(v -> allMessagesReceived.future())
            .compose(v -> publisher.cancel())
            .compose(v -> consumer.cancel())
            .compose(v -> connection.close())
            .onComplete(ar -> {
              if (ar.succeeded()) {
                async.complete();
              } else {
                ctx.fail(ar.cause());
              }
            })
            ;
  }

  private Future<Void> createPublisher() {
    return connection.createChannelBuilder()
            .withChannelOpenHandler(chann -> {
              chann.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null);
            })
            .createPublisher(TEST_EXCHANGE, RabbitMQChannelBuilder.STRING_MESSAGE_CODEC, new RabbitMQPublisherOptions().setResendOnReconnect(true))
            .onSuccess(pub -> publisher = pub)
            .mapEmpty()
            ;
  }
  
  private void sendMessages() {
    AtomicLong counter = new AtomicLong();
    AtomicLong count = new AtomicLong(20);
    AtomicLong timerId = new AtomicLong();
    
    /*
    Send a message every second, with the message being a strictly increasing integer.
    After sending the first message when 'hasShutdown' is set complete the messageSentAfterShutdown to notify the main test.
    Then continue to send a further postShutdownCount messages, before cancelling the periodic timer and completing allMessagesSent with the total count of messages sent.
    */
    timerId.set(vertx.setPeriodic(200, v -> {
      long value = counter.incrementAndGet();
      logger.info("Publishing message {}", value);
      publisher.publish("", new BasicProperties(), Long.toString(value));
      if (count.decrementAndGet() == 0) {
        vertx.cancelTimer(timerId.get());
        allMessagesSent.complete(counter.get());
      }
    }));
  }
  
  private Future<Void> messageHandler(RabbitMQConsumer consumer, RabbitMQMessage<String> message) {
    Long index = Long.valueOf(message.body());
    synchronized(receivedMessages) {
      receivedMessages.add(index);
      logger.info("Received message: {} (have {})", index, receivedMessages.size());
      Future<Long> allMessagesSentFuture = allMessagesSent.future();
      if (allMessagesSentFuture.isComplete() && (receivedMessages.size() == allMessagesSentFuture.result())) {
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
            .createConsumer(RabbitMQChannelBuilder.STRING_MESSAGE_CODEC, TEST_QUEUE, null, new RabbitMQConsumerOptions(), this::messageHandler)
            .onSuccess(con -> consumer = con)
            .mapEmpty()
            ;
  }
  
}
