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
package io.vertx.rabbitmq;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;


@RunWith(VertxUnitRunner.class)
public class RabbitMQClientBuiltinRecoveryTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientReconnectTest.class);

  /**
   * This test verifies that the RabbitMQ Java client reconnection logic works as long as the vertx reconnect attempts is set to zero.
   *
   * This test is almost identical to the RabbitMQClientReconnectTest.
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
  private Proxy proxy;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Void> firstMessagesReceived = Promise.promise();
  private final AtomicBoolean hasShutdown = new AtomicBoolean(false);
  private final Promise<Void> messageSentAfterShutdown = Promise.promise();
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();
  
  private RabbitMQChannel pubChannel;
  private RabbitMQRepublishingPublisher publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer consumer;
  
  public RabbitMQClientBuiltinRecoveryTest() throws IOException {
    LOGGER.info("Constructing");
    this.network = RabbitMQBrokerProvider.getNetwork();
    this.networkedRabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(proxy.getProxyPort());
    options.setConnectionTimeout(1000);
    options.setNetworkRecoveryInterval(1000);
    options.setRequestedHeartbeat(1);
    options.setConnectionName(this.getClass().getSimpleName());
    // Enable Java RabbitMQ client library reconnections
    options.setAutomaticRecoveryEnabled(true);
    // Disable vertx RabbitMQClient reconnections
    options.setReconnectAttempts(0);    
    return options;
  }
  
  @Before
  public void setup() throws Exception {
    this.proxy = new Proxy(vertx, this.networkedRabbitmq.getMappedPort(5672));
    this.proxy.startProxy();
    this.connection = RabbitMQClient.create(vertx, getRabbitMQOptions());
  }

  @After
  public void shutdown() {
    this.proxy.stopProxy();
  }

  @Test(timeout = 1 * 60 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    Async async = ctx.async();
    
    createAndStartConsumer(vertx, ctx);
    createAndStartProducer(vertx);
    
    // Have to react to allMessagesSent completing in case it completes after the last message is received.
    allMessagesSent.future().onSuccess(count -> {
      synchronized(receivedMessages) {
        if (receivedMessages.size() == count) {
          allMessagesReceived.tryComplete();
        }
      }
    });
    
    firstMessagesReceived.future()
            .compose(v -> breakConnection())
            .compose(v -> messageSentAfterShutdown.future())
            .compose(v -> reestablishConnection())
            .compose(v -> allMessagesSent.future())
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

  private void createAndStartProducer(Vertx vertx) {
    pubChannel = connection.createChannel();
   
    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(p);
    });
    publisher = pubChannel.createPublisher(TEST_EXCHANGE, new RabbitMQPublisherOptions());
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
      publisher.publish("", new BasicProperties(), Buffer.buffer(Long.toString(value)));
      if (hasShutdown.get()) {
        messageSentAfterShutdown.tryComplete();
        if (postShutdownCount.decrementAndGet() == 0) {
          vertx.cancelTimer(timerId.get());
          allMessagesSent.complete(counter.get());
        }
      }
    }));
  }

  private void createAndStartConsumer(Vertx vertx, TestContext ctx) {
    
    conChannel = connection.createChannel();   
    conChannel.addChannelEstablishedCallback(p -> {
      conChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    conChannel.addChannelShutdownHandler(sse -> {
      hasShutdown.set(true);
    });
    
    consumer = conChannel.createConsumer(TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      Long index = Long.parseLong(message.body().toString(StandardCharsets.UTF_8));
      synchronized(receivedMessages) {
        receivedMessages.add(index);
        if (receivedMessages.size() > 5) {
          firstMessagesReceived.tryComplete();
        }
        logger.info("Received message: {} (have {})", index, receivedMessages.size());
        Future<Long> allMessagesSentFuture = allMessagesSent.future();
        if (allMessagesSentFuture.isComplete() && (receivedMessages.size() == allMessagesSentFuture.result())) {
          allMessagesReceived.tryComplete();
        }
      }
      conChannel.basicAck(message.consumerTag(), message.envelope().getDeliveryTag(), false);
    });
    consumer.consume(false, null)
            .onComplete(ar -> { 
              if (ar.failed()) {
                ctx.fail(ar.cause());
              } else {
                logger.info("Consumer started: {}", ar );
              } })
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
