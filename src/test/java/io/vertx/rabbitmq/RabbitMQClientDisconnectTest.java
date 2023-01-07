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

  private static final GenericContainer container = RabbitMQBrokerProvider.getRabbitMqContainer();
  private Proxy proxy;
  private TestContext testContext;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();
  
  private RabbitMQChannel pubChannel;
  private RabbitMQPublisher<String> publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<String> consumer;
  
  public RabbitMQClientDisconnectTest() throws IOException {
    logger.info("Constructing");
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  @AfterClass
  public static void shutdown() {
    container.stop();
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
    options.setReconnectAttempts(Integer.MAX_VALUE);
    return options;
  }
  
  @Before
  public void setup(TestContext testContext) throws Exception {
    this.proxy = new Proxy(vertx, this.container.getMappedPort(5672));
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
    this.testContext = ctx;
    
    connection.openChannel().onSuccess(this::createAndStartProducer).onFailure(ctx::fail);
    connection.openChannel().onSuccess(this::createAndStartConsumer).onFailure(ctx::fail);
    
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
            .compose(v -> publisher.stop())
            .compose(v -> pubChannel.close())
            .compose(v -> consumer.cancel())
            .compose(v -> conChannel.close())
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

  private void createAndStartProducer(RabbitMQChannel channel) {
    pubChannel = channel;
   
    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(p);
    });
    publisher = pubChannel.createPublisher(new RabbitMQStringMessageCodec(), TEST_EXCHANGE, new RabbitMQPublisherOptions());
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

  private void createAndStartConsumer(RabbitMQChannel channel) {
    conChannel = channel;
   
    conChannel.addChannelEstablishedCallback(p -> {
      conChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    
    consumer = conChannel.createConsumer(new RabbitMQStringMessageCodec(), TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      Long index = Long.parseLong(message.body());
      synchronized(receivedMessages) {
        receivedMessages.add(index);
        logger.info("Received message: {} (have {})", index, receivedMessages.size());
        Future<Long> allMessagesSentFuture = allMessagesSent.future();
        if (allMessagesSentFuture.isComplete() && (receivedMessages.size() == allMessagesSentFuture.result())) {
          allMessagesReceived.tryComplete();
        }
      }
    });
    consumer.consume(false, null)
            .onComplete(ar -> { 
              if (ar.failed()) {
                testContext.fail(ar.cause());
              } else {
                logger.info("Consumer started: {}", ar );
              } })
            ;
  }
  
}
