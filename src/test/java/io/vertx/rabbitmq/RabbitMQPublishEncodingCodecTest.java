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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class RabbitMQPublishEncodingCodecTest {

  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQPublishEncodingCodecTest.class);

  /**
   * This test verifies that all the codecs provided by default by CodecManager work correctly.
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

  private final GenericContainer container;

  private final Vertx vertx;
  private RabbitMQConnection connection;

  private Promise<String> lastMessage;

  private RabbitMQChannel pubChannel;
  private RabbitMQPublisher<String> publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<String> consumer;

  public RabbitMQPublishEncodingCodecTest() throws IOException {
    logger.info("Constructing");
    this.container = RabbitMQBrokerProvider.getRabbitMqContainer();
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

  private Future<Void> testTransfer(String name, String send, String required) {
    lastMessage = Promise.promise();
    return publisher.publish("", new AMQP.BasicProperties(), send)
            .compose(v -> {
              return lastMessage.future();
            })
            .compose(received -> {
              if (required.equals(received)) {
                return Future.succeededFuture();
              } else {
                logger.debug("{}: {} != {}", name, received, required);
                return Future.failedFuture(name +": " + received + " != " + required);
              }
            });
  }
  
  @Test
  public void testCustomClassCodec() {
    CustomStringCodec codec = new CustomStringCodec();
    String transformed = codec.decodeFromBytes(codec.encodeToBytes("This is a boring string"));
    assertEquals("This is a boring string", transformed);
  }  

  @Test(timeout = 1 * 60 * 1000L)
  public void testPublishMessageWithNamedCodec(TestContext ctx) throws Exception {
    Async async = ctx.async();

    connection.openChannel().compose(this::createAndStartConsumer).onFailure(ctx::fail)
            .compose(v -> connection.openChannel())
            .compose(chan -> {
              createPublisher(chan);
              return testTransfer("deflated-utf16", "Another boring string", "Another boring string");
            })
            .onFailure(ex -> {
              ctx.fail(ex);
            })
            .onSuccess(v -> {
              async.complete();
            });

  }

  private void createPublisher(RabbitMQChannel channel) {
    pubChannel = channel;

    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(p);
    });
    publisher = pubChannel.createPublisher(new CustomStringCodec(), TEST_EXCHANGE, new RabbitMQPublisherOptions());
  }

  private Future<Void> createAndStartConsumer(RabbitMQChannel channel) {
    conChannel = channel;
    conChannel.registerCodec(new CustomClassCodec());    
    conChannel.addChannelEstablishedCallback(p -> {
      conChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    consumer = conChannel.createConsumer(new CustomStringCodec(), TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      lastMessage.complete(message.body());
    });
    return consumer.consume(true, null).mapEmpty();
  }
}
