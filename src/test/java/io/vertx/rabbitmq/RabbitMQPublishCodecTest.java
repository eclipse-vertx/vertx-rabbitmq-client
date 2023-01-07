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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;


@RunWith(VertxUnitRunner.class)
public class RabbitMQPublishCodecTest {

  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQPublishCodecTest.class);

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

  private final GenericContainer networkedRabbitmq;

  private final Vertx vertx;
  private RabbitMQConnection connection;

  private volatile Promise<byte[]> lastMessage;

  private RabbitMQChannel pubChannel;
  private RabbitMQPublisher<Object> publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<byte[]> consumer;

  public RabbitMQPublishCodecTest() throws IOException {
    logger.info("Constructing");
    this.networkedRabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
    this.vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(6));
  }

  private RabbitMQOptions getRabbitMQOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost("localhost");
    options.setPort(networkedRabbitmq.getMappedPort(5672));
    options.setConnectionTimeout(500);
    options.setNetworkRecoveryInterval(500);
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

  public static byte[] longToBytes(long x) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(x);
    return buffer.array();
  }
  
  public static byte[] intToBytes(int x) {
    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
    buffer.putInt(x);
    return buffer.array();
  }
  
  public static byte[] shortToBytes(short x) {
    ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
    buffer.putShort(x);
    return buffer.array();
  }
  
  public static byte[] doubleToBytes(double x) {
    ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
    buffer.putDouble(x);
    return buffer.array();
  }
  
  public static byte[] floatToBytes(float x) {
    ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
    buffer.putFloat(x);
    return buffer.array();
  }
  
  public static byte[] charToBytes(char x) {
    ByteBuffer buffer = ByteBuffer.allocate(Character.BYTES);
    buffer.putChar(x);
    return buffer.array();
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

  private Future<Void> testTransfer(String name, Object send, byte[] required) {
    logger.debug("Testing transfer of {}", send);
    lastMessage = Promise.promise();
    return publisher.publish("", new AMQP.BasicProperties(), send)
            .compose(v -> {
              logger.debug("Published: {}", send);
              return lastMessage.future();
            })
            .compose(bytes -> {
              logger.debug("Got message: {}", bytes);
              if (Arrays.equals(required, bytes)) {
                return Future.succeededFuture();
              } else {
                logger.info("{}: {} != {}", name, bytes, required);
                return Future.failedFuture(name +": " + bytesToHex(bytes) + " != " + bytesToHex(required));
              }
            });
  }
  
  @Test(timeout = 20 * 60 * 1000L)
  public void testRecoverConnectionOutage(TestContext ctx) throws Exception {
    Async async = ctx.async();

    connection.openChannel()
            .compose(this::createAndStartConsumer)
            .compose(v -> connection.openChannel())
            .compose(chan -> {
              createPublisher(chan);
              return testTransfer("String", "Hello", "Hello".getBytes(StandardCharsets.UTF_8));
            })
            .compose(v -> testTransfer("Null", null, new byte[0]))
            .compose(v -> {
              Buffer buf = Buffer.buffer("This is my buffer");              
              return testTransfer("Buffer", buf, buf.getBytes());
            })
            .compose(v -> {
              JsonObject jo = new JsonObject().put("key", 1234.5678);
              return testTransfer("JsonObject", jo, jo.toString().getBytes(StandardCharsets.UTF_8));
            })
            .compose(v -> {
              JsonArray ja = new JsonArray().add(123).add("Bob");
              return testTransfer("JsonArray", ja, ja.toString().getBytes(StandardCharsets.UTF_8));
            })
            .compose(v -> {
              JsonArray ja = new JsonArray();
              ja.add(123);
              ja.add("Bob");
              return testTransfer("JsonArray", ja, ja.toString().getBytes(StandardCharsets.UTF_8));
            })
            .onFailure(ex -> {
              logger.error("Failed: ", ex);
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
    publisher = pubChannel.createPublisher(TEST_EXCHANGE, new RabbitMQPublisherOptions());
  }

  private Future<Void> createAndStartConsumer(RabbitMQChannel channel) {
    conChannel = channel;
    conChannel.addChannelEstablishedCallback(p -> {
      conChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> conChannel.queueDeclare(TEST_QUEUE, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> conChannel.queueBind(TEST_QUEUE, TEST_EXCHANGE, "", null))
              .onComplete(p);
    });
    consumer = conChannel.createConsumer(TEST_QUEUE, new RabbitMQConsumerOptions().setAutoAck(true));
    consumer.handler(message -> {
      logger.debug("Got message: {} ({})", message, lastMessage);
      try {
        lastMessage.complete(message.body());
      } catch(Throwable ex) {
        logger.error("Failed: ", ex);
      }
    });
    return consumer.consume(true, null).mapEmpty();
  }
}
