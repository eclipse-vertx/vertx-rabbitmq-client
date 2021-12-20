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
import org.testcontainers.containers.Network;

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

  private final Network network;
  private final GenericContainer networkedRabbitmq;

  private final Vertx vertx;
  private RabbitMQConnection connection;

  private Promise<String> lastMessage;

  private RabbitMQChannel pubChannel;
  private RabbitMQPublisher<String> publisher;
  private RabbitMQChannel conChannel;
  private RabbitMQConsumer<String> consumer;

  public RabbitMQPublishEncodingCodecTest() throws IOException {
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

  @Test(timeout = 5 * 60 * 1000L)
  public void testPublishMessageWithNamedCodec(TestContext ctx) throws Exception {
    Async async = ctx.async();

    createPublisher();
    createConsumer();
    testTransfer("deflated-utf16", "Another boring string", "Another boring string")
            .onFailure(ex -> {
              ctx.fail(ex);
            })
            .onSuccess(v -> {
              async.complete();
            });

  }

  private void createPublisher() {
    pubChannel = connection.createChannel();

    pubChannel.addChannelEstablishedCallback(p -> {
      pubChannel.exchangeDeclare(TEST_EXCHANGE, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .onComplete(p);
    });
    publisher = pubChannel.createPublisher(new CustomStringCodec(), TEST_EXCHANGE, new RabbitMQPublisherOptions());
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
    consumer = conChannel.createConsumer(new CustomStringCodec(), TEST_QUEUE, new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      lastMessage.complete(message.body());
    });
    consumer.consume(true, null);
  }
}
