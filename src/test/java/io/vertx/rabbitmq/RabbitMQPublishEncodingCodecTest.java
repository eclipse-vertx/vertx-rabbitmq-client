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

import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;

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

  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();

  private final Vertx vertx;
  private RabbitMQConnection connection;

  private Promise<String> lastMessage;

  private RabbitMQPublisher<String> publisher;
  private RabbitMQConsumer consumer;

  public RabbitMQPublishEncodingCodecTest() throws IOException {
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

    options.setHost(CONTAINER.getHost());
    options.setPort(CONTAINER.getMappedPort(5672));
    options.setConnectionTimeout(500);
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
            })
            ;
  }
  
  @Test
  public void testCustomClassCodec() {
    CustomStringCodec codec = new CustomStringCodec();
    String transformed = codec.decodeFromBytes(codec.encodeToBytes("This is a boring string"));
    assertEquals("This is a boring string", transformed);
  }  

  @Test(timeout = 1 * 60 * 1000L)
  public void testPublishMessageWithNamedCodec(TestContext ctx) throws Exception {
    createConsumer()
            .compose(v -> createPublisher())
            .compose(v -> {
              return testTransfer("deflated-utf16", "Another boring string", "Another boring string");
            })
            .compose(v -> consumer.cancel())
            .compose(v -> publisher.cancel())
            .compose(v -> connection.close())
            .onComplete(ctx.asyncAssertSuccess())
            ;

  }  

  private Future<Void> createPublisher() {
    return connection.createChannelBuilder()
            .withChannelOpenHandler(chann -> {
              chann.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null);
            })
            .createPublisher(TEST_EXCHANGE, new CustomStringCodec(), new RabbitMQPublisherOptions())
            .onSuccess(pub -> publisher = pub)
            .mapEmpty()
            ;
  }
  
  private Future<Void> messageHandler(RabbitMQConsumer consumer, RabbitMQMessage<String> message) {
    lastMessage.complete(message.body());
    return message.basicAck();
  }
  
  private Future<Void> createConsumer() {
    return connection.createChannelBuilder()
            .withChannelOpenHandler(rawChannel -> {
              rawChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null);
              rawChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null);
              rawChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null);
            })
            .createConsumer(new CustomStringCodec(), TEST_QUEUE, null, new RabbitMQConsumerOptions(), this::messageHandler)
            .onSuccess(con -> consumer = con)
            .mapEmpty()
            ;
  }
  
}
