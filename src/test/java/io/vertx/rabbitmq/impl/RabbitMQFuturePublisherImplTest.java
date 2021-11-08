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
package io.vertx.rabbitmq.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rabbitmq.RabbitMQBrokerProvider;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQClientReconnectTest;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQFuturePublisher;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQFuturePublisherImplTest {
  
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
  private static final String TEST_EXCHANGE = "RabbitMQClientBuiltinReconnectExchange";
  private static final String TEST_QUEUE = "RabbitMQClientBuiltinReconnectQueue";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = false;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE = false;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE = false;

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQClientReconnectTest.class);

  private final Network network;
  private final GenericContainer networkedRabbitmq;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();
  
  private RabbitMQChannel pubChannel;
  private RabbitMQFuturePublisher publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer consumer;
  
  public RabbitMQFuturePublisherImplTest() throws IOException {
    LOGGER.info("Constructing");
    this.network = RabbitMQBrokerProvider.getNetwork();
    this.networkedRabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(networkedRabbitmq.getMappedPort(5672));
    options.setConnectionTimeout(1000);
    options.setNetworkRecoveryInterval(1000);
    options.setRequestedHeartbeat(60);
    options.setConnectionName(this.getClass().getSimpleName());
    return options;
  }
  
  @Before
  public void setup() throws Exception {
    this.connection = RabbitMQClient.create(vertx, getRabbitMQOptions());
  }

  @Test(timeout = 1 * 60 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    Async async = ctx.async();
    
    // Have to react to allMessagesSent completing in case it completes after the last message is received.
    allMessagesSent.future().onSuccess(count -> {
      synchronized(receivedMessages) {
        if (receivedMessages.size() == count) {
          allMessagesReceived.tryComplete();
        }
      }
    });
    
    createAndStartConsumer(vertx, ctx)
            .compose(v -> createAndStartProducer(vertx))
            .compose(v -> allMessagesSent.future())
            .compose(v -> allMessagesSent.future())
            .compose(v -> allMessagesReceived.future())
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

  private Future<Void> createAndStartProducer(Vertx vertx) {
    pubChannel = connection.createChannel();
   
    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  logger.info("Exchange declared");
                  p.complete();
                } else {
                  logger.warn("Exchange not declared: ", ar.cause());
                  p.fail(ar.cause());
                }
              });
    });
    publisher = pubChannel.createFuturePublisher(TEST_EXCHANGE, new RabbitMQPublisherOptions());
    AtomicLong counter = new AtomicLong();
    AtomicLong postCount = new AtomicLong(20);
    AtomicLong timerId = new AtomicLong();
    
    /*
    Send a message every second, with the message being a strictly increasing integer.
    */
    timerId.set(vertx.setPeriodic(100, v -> {
      long value = counter.incrementAndGet();
      logger.info("Publishing message {}", value);
      publisher.publish("", new AMQP.BasicProperties(), Buffer.buffer(Long.toString(value)))
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  logger.info("Published message {}", value);
                } else {
                  logger.warn("Failed to publish message {}: ", value, ar.cause());
                }
              });
      if (postCount.decrementAndGet() == 0) {
        vertx.cancelTimer(timerId.get());
        allMessagesSent.complete(counter.get());
      }
    }));
    return Future.succeededFuture();
  }

  private Future<Void> createAndStartConsumer(Vertx vertx, TestContext ctx) {
    
    conChannel = connection.createChannel();
    conChannel.addChannelEstablishedCallback(p -> {
      conChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    
    consumer = conChannel.createConsumer(TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      Long index = Long.parseLong(message.body().toString(StandardCharsets.UTF_8));
      synchronized(receivedMessages) {
        receivedMessages.add(index);
        logger.info("Received message: {} (have {})", index, receivedMessages.size());
        Future<Long> allMessagesSentFuture = allMessagesSent.future();
        if (allMessagesSentFuture.isComplete() && (receivedMessages.size() == allMessagesSentFuture.result())) {
          allMessagesReceived.tryComplete();
        }
      }
      conChannel.basicAck(message.consumerTag(), message.envelope().getDeliveryTag(), false);
    });
    return consumer.consume(false, null)
            .onComplete(ar -> { 
              if (ar.failed()) {
                ctx.fail(ar.cause());
              } else {
                logger.info("Consumer started: {}", ar );
              } })
            .mapEmpty()
            ;
  }
  
}
