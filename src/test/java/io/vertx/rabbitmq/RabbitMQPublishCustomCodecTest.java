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
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class RabbitMQPublishCustomCodecTest {

  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQPublishCustomCodecTest.class);

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

  private final Network network;
  private final GenericContainer networkedRabbitmq;

  private final Vertx vertx;
  private RabbitMQConnection connection;

  private Promise<CustomClass> lastMessage;

  private RabbitMQChannel pubChannel;
  private RabbitMQPublisher<CustomClass> publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<CustomClass> consumer;

  public RabbitMQPublishCustomCodecTest() throws IOException {
    logger.info("Constructing");
    this.network = RabbitMQBrokerProvider.getNetwork();
    this.networkedRabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(networkedRabbitmq.getMappedPort(5672));
    options.setConnectionTimeout(500);
    options.setNetworkRecoveryInterval(500);
    options.setRequestedHeartbeat(1);
    options.setConnectionName(this.getClass().getSimpleName());
    return options;
  }

  @Before
  public void setup() throws Exception {
    this.connection = RabbitMQClient.create(vertx, getRabbitMQOptions());
  }
  
  private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
  public static String bytesToHex(byte[] bytes) {
      byte[] hexChars = new byte[bytes.length * 2];
      for (int j = 0; j < bytes.length; j++) {
          int v = bytes[j] & 0xFF;
          hexChars[j * 2] = HEX_ARRAY[v >>> 4];
          hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
      }
      return new String(hexChars, StandardCharsets.UTF_8);
  }

  private Future<Void> testTransfer(String name, CustomClass send, CustomClass required) {
    lastMessage = Promise.promise();
    return publisher.publish("", new AMQP.BasicProperties(), send)
            .compose(v -> {
              return lastMessage.future();
            })
            .compose(received -> {
              if (required.toString().equals(received.toString())) {
                return Future.succeededFuture();
              } else {
                logger.debug("{}: {} != {}", name, received, required);
                return Future.failedFuture(name +": " + received + " != " + required);
              }
            });
  }
  
  @Test
  public void testCustomClassCodec() {
    CustomClassCodec codec = new CustomClassCodec();
    CustomClass value = new CustomClass(17L, "Seventeen", 2.345);
    CustomClass transformed = codec.decodeFromBytes(codec.encodeToBytes(value));
    assertEquals(value.getId(), transformed.getId());
    assertEquals(value.getTitle(), transformed.getTitle());
    assertEquals(value.getDuration(), transformed.getDuration(), 0.001);
  }  

  @Test(timeout = 5 * 60 * 1000L)
  public void testPublishMessageWithCodec(TestContext ctx) throws Exception {
    Async async = ctx.async();

    createPublisher();
    createConsumer();
    testTransfer("CustomClass", new CustomClass(19L, "random", 27.435), new CustomClass(19L, "random", 27.435))
            .onFailure(ex -> {
              ctx.fail(ex);
            })
            .onSuccess(v -> {
              async.complete();
            });

  }

  private void createPublisher() {
    pubChannel = connection.createChannel();
    pubChannel.registerCodec(new CustomClassCodec());    

    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(p);
    });
    publisher = pubChannel.createPublisher(new CustomClassCodec(), TEST_EXCHANGE, new RabbitMQPublisherOptions());
  }

  private void createConsumer() {
    conChannel = connection.createChannel();
    conChannel.registerCodec(new CustomClassCodec());    
    conChannel.addChannelEstablishedCallback(p -> {
      conChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    consumer = conChannel.createConsumer(new CustomClassCodec(), TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      lastMessage.complete(message.body());
    });
    consumer.consume(true, null);
  }
}
