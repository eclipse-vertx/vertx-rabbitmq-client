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
public class RabbitMQClientDisconnectTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientDisconnectTest.class);

  /**
   * This test just does a clean start/stop of rabbitmq to check that there are no spurious errors logged.
   */
  private static final String TEST_EXCHANGE = "RabbitMQClientDisconnectExchange";
  private static final String TEST_QUEUE = "RabbitMQClientDisconnectQueue";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = false;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = true;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_DURABLE = false;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE = true;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE = true;

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQClientDisconnectTest.class);

  private final Network network;
  private final GenericContainer networkedRabbitmq;
  private Proxy proxy;
  
  private final Vertx vertx;
  private RabbitMQConnection connection;

  private final Set<Long> receivedMessages = new HashSet<>();
  
  private final Promise<Long> allMessagesSent = Promise.promise();
  private final Promise<Long> allMessagesReceived = Promise.promise();
  
  private RabbitMQChannel pubChannel;
  private RabbitMQRepublishingPublisher publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer consumer;
  
  public RabbitMQClientDisconnectTest() throws IOException {
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
    // Disable Java RabbitMQ client library reconnections
    options.setAutomaticRecoveryEnabled(false);
    // Enable vertx RabbitMQClient reconnections
    options.setReconnectAttempts(Integer.MAX_VALUE);
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

  private void createAndStartProducer(Vertx vertx) {
    pubChannel = connection.createChannel();
   
    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(p);
    });
    publisher = pubChannel.createPublisher(TEST_EXCHANGE, new RabbitMQPublisherOptions());
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
      publisher.publish("", new BasicProperties(), Buffer.buffer(Long.toString(value)));
      if (count.decrementAndGet() == 0) {
        vertx.cancelTimer(timerId.get());
        allMessagesSent.complete(counter.get());
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
  
}
