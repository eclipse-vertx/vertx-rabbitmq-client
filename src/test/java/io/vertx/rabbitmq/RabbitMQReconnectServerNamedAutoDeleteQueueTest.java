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
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rabbitmq.impl.codecs.RabbitMQLongMessageCodec;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
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
public class RabbitMQReconnectServerNamedAutoDeleteQueueTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientReconnectTest.class);

  /**
   * This test verifies that the RabbitMQ Java client reconnection logic works as long as the vertx reconnect attempts is set to zero.
   *
   * The change that makes this work is in the basicConsumer, where the shutdown handler is only set if retries > 0. 
   * Without that change the vertx client shutdown handler is called, 
   * interrupting the java client reconnection logic, even though the vertx reconnection won't work because retries is zero.
   *
   */
  private final String TEST_EXCHANGE = this.getClass().getName() + "Exchange";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = false;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;

  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();
  private Proxy proxy;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Void> firstMessagesReceived = Promise.promise();
  private final AtomicBoolean hasShutdown = new AtomicBoolean(false);
  private final Promise<Void> messageSentAfterShutdown = Promise.promise();
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> messagesReceivedAfterReconnect = Promise.promise();
  
  private RabbitMQPublisher<Long> publisher;
  private RabbitMQConsumer consumer;
  private final AtomicReference<String> queueName = new AtomicReference<>();
  
  public RabbitMQReconnectServerNamedAutoDeleteQueueTest() throws IOException {
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

  @Test(timeout = 1 * 60 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    Async async = ctx.async();
    
    createConsumer()
            .compose(v -> createPublisher())
            .compose(v -> sendMessages())
            .compose(v -> firstMessagesReceived.future())
            .compose(v -> breakConnection())
            .compose(v -> messageSentAfterShutdown.future())
            .compose(v -> reestablishConnection())
            .compose(v -> allMessagesSent.future())
            .compose(v -> messagesReceivedAfterReconnect.future())
            .compose(v -> publisher.cancel())
            .compose(v -> consumer.cancel())
            .compose(v -> connection.close())
            .onComplete(ar -> {
              logger.debug("All done");
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
            .createPublisher(TEST_EXCHANGE, new RabbitMQLongMessageCodec(), new RabbitMQPublisherOptions())
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
      long value = counter.incrementAndGet();
      logger.info("Publishing message {}", value);
      publisher.publish("", new BasicProperties(), value);
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
  
  
  private Future<Void> messageHandler(RabbitMQConsumer consumer, RabbitMQMessage<Long> message) {
    logger.debug("Got message {} from {}", message.body(), queueName);
    if (messageSentAfterShutdown.future().succeeded()) {
      logger.debug("Got message after reconnect");
      messagesReceivedAfterReconnect.complete();
    }
    firstMessagesReceived.tryComplete();
    return message.basicAck();
  }
  
  private Future<Void> createConsumer() {
    return connection.createChannelBuilder()
            .withChannelOpenHandler(rawChannel -> {
              rawChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null);
              DeclareOk dok = rawChannel.queueDeclare("", false, true, true, null);
              queueName.set(dok.getQueue());
              rawChannel.queueBind(dok.getQueue(), TEST_EXCHANGE, "", null);
            })
            .withChannelShutdownHandler(sse -> {
              hasShutdown.set(true);
            })
            .createConsumer(new RabbitMQLongMessageCodec(), null, () -> queueName.get(), new RabbitMQConsumerOptions(), this::messageHandler)            
            .onSuccess(con -> consumer = con)
            .mapEmpty()
            ;
  }
  

  private Future<Void> breakConnection() {
    return vertx.executeBlocking(promise -> {
      logger.info("Blocking proxy");
      proxy.stopProxy();
      logger.info("Blocked proxy");
      promise.complete();      
    });
  }
  
  private Future<Void> reestablishConnection() {
    return vertx.executeBlocking(promise -> {
      logger.info("Unblocking proxy");
      try {
        proxy.startProxy();
      } catch(Exception ex) {
        logger.error("Failed to restart proxy");
      }
      logger.info("Unblocked proxy");
      promise.complete();      
    });
  }
    
}
