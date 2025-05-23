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
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class RabbitMQClientBuiltinRecoveryTest {

  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientBuiltinRecoveryTest.class);

  /**
   * This test verifies that the RabbitMQ Java client reconnection logic works as long as the vertx reconnect attempts is set to zero.
   *
   * This test is almost identical to the RabbitMQClientReconnectTest.
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
  private Proxy proxy;

  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<String> receivedMessages = new HashSet<>();

  private final Promise<Void> firstMessagesReceived = Promise.promise();
  private final AtomicBoolean hasShutdown = new AtomicBoolean(false);
  private final Promise<Void> messageSentAfterShutdown = Promise.promise();
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();

  private RabbitMQPublisher<byte[]> publisher;
  private RabbitMQConsumer consumer;

  public RabbitMQClientBuiltinRecoveryTest() throws IOException {
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
    // Enable Java RabbitMQ client library reconnections
    options.setAutomaticRecoveryEnabled(true);
    // Disable vertx RabbitMQClient reconnections
    options.setReconnectAttempts(0);
    return options;
  }

  @Before
  public void setup(TestContext testContext) throws Exception {
    this.proxy = new Proxy(vertx, CONTAINER.getMappedPort(5672));
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
            .compose(v -> firstMessagesReceived.future())
            .compose(v -> breakConnection())
            .compose(v -> messageSentAfterShutdown.future())
            .compose(v -> reestablishConnection())
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
            .createPublisher(TEST_EXCHANGE, RabbitMQChannelBuilder.BYTE_ARRAY_MESSAGE_CODEC, new RabbitMQPublisherOptions().setResendOnReconnect(true))
            .onSuccess(pub -> publisher = pub)
            .mapEmpty()
            ;
  }

  private Future<Void> sendMessages() {
    AtomicLong counter = new AtomicLong();
    AtomicLong postShutdownCount = new AtomicLong(20);
    AtomicLong timerId = new AtomicLong();

    /*
    Send a message every second, with the message being a strictly increasing integer.
    After sending the first message when 'hasShutdown' is set complete the messageSentAfterShutdown to notify the main test.
    Then continue to send a further postShutdownCount messages, before cancelling the periodic timer and completing allMessagesSent with the total count of messages sent.
    */
    timerId.set(vertx.setPeriodic(200, v -> {
      String value = "MSG " + counter.incrementAndGet();
      logger.info("Publishing message {}", value);
      publisher.publish("", new BasicProperties(), value.getBytes(StandardCharsets.UTF_8))
              .onFailure(ex -> {
                logger.warn("Failed to send message {}: ", value, ex);
              });
      if (hasShutdown.get()) {
        messageSentAfterShutdown.tryComplete();
        if (postShutdownCount.decrementAndGet() == 0) {
          vertx.cancelTimer(timerId.get());
          allMessagesSent.complete(counter.get());
        }
      }
    }));
    return Future.succeededFuture();
  }

  private Future<Void> messageHandler(RabbitMQConsumer consumer, RabbitMQMessage<byte[]> message) {
    String body = new String(message.body(), StandardCharsets.UTF_8);
    synchronized(receivedMessages) {
      receivedMessages.add(body);
      if (receivedMessages.size() > 5) {
        firstMessagesReceived.tryComplete();
      }
      logger.info("Received message: {} (have {})", body, receivedMessages.size());
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
            .withChannelShutdownHandler(sse -> this.hasShutdown.set(true))
            .createConsumer(
                    RabbitMQChannelBuilder.BYTE_ARRAY_MESSAGE_CODEC
                    , TEST_QUEUE
                    , null
                    , new RabbitMQConsumerOptions().setReconnectInterval(0)
                    , this::messageHandler
            )
            .onSuccess(con -> consumer = con)
            .mapEmpty();
  }

  private Future<Void> breakConnection() {
    return vertx.executeBlocking(() -> {
      logger.info("Blocking proxy");
      proxy.stopProxy();
      logger.info("Blocked proxy");
      return null;
    });
  }

  private Future<Void> reestablishConnection() {
    return vertx.executeBlocking(() -> {
      logger.info("Unblocking proxy");
      try {
        proxy.startProxy();
      } catch(Exception ex) {
        logger.error("Failed to restart proxy");
      }
      logger.info("Unblocked proxy");
      return null;
    });
  }

}
