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
import com.rabbitmq.client.Envelope;
import io.vertx.core.Promise;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rabbitmq.impl.RabbitMQConnectionImpl;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;



/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQClientTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientTest.class);
  
  @ClassRule
  public static final GenericContainer rabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();

  @Test  
  public void testCreateWithWorkingServer(TestContext context) {
    RabbitMQOptions config = config();
    RabbitMQConnection connection = RabbitMQClient.create(testRunContext.vertx(), config);

    RabbitMQChannel channel = connection.createChannel();
    Async async = context.async();
    channel.connect()
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Completing test");
                connection.close().onComplete(ar2 -> {
                  async.complete();                     
                });
              } else {
                logger.info("Failing test");
                context.fail(ar.cause());
              }
            });
    logger.info("Ending test");
  }
  
  private static final class TestConsumer extends DefaultConsumer {
    
    private final TestContext testContext;
    private final Promise promise;

    public TestConsumer(RabbitMQChannel channel, TestContext testContext, Promise promise) {
      super(channel);
      this.testContext = testContext;
      this.promise = promise;
    }
    
    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
      logger.info("Message received");
      testContext.assertEquals("Hello", new String(body, StandardCharsets.UTF_8));
      promise.complete();
    }

  }
  
  @Test  
  public void testSendMessageWithWorkingServer(TestContext context) {
    RabbitMQOptions config = config();
    RabbitMQConnection connection = RabbitMQClient.create(testRunContext.vertx(), config);

    Async async = context.async();
    
    String exchange = "testSendMessageWithWorkingServer";
    String queue = "testSendMessageWithWorkingServerQueue";
    
    RabbitMQChannel conChan = connection.createChannel();
    RabbitMQChannel pubChan = connection.createChannel();
    Promise<Void> donePromise = Promise.promise();
    conChan.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null)
            .compose(v -> conChan.queueDeclare(queue, true, false, true, null))
            .compose(v -> conChan.queueBind(queue, exchange, "", null))
            .compose(v -> conChan.basicConsume(queue, true, getClass().getSimpleName(), false, false, null, new TestConsumer(conChan, context, donePromise)))
            .compose(v -> pubChan.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null))
            .compose(v -> pubChan.confirmSelect())
            .compose(v -> pubChan.basicPublish(exchange, "", true, new BasicProperties(), "Hello".getBytes(StandardCharsets.UTF_8)))
            .compose(v -> pubChan.waitForConfirms(1000))
            .compose(v -> donePromise.future())
            .onComplete(ar -> {
              if (ar.succeeded()) {
                connection.close().onComplete(ar2 -> {
                  async.complete();
                });
              } else {
                logger.info("Failing test: ", ar.cause());
                context.fail(ar.cause());
              }
            })
            ;
        
    logger.info("Ending test");
  }
  
  private int findOpenPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
  
  @Test
  public void testCreateWithServerThatArrivesLate(TestContext context) throws IOException {    
    RabbitMQOptions config = new RabbitMQOptions();
    config.setReconnectInterval(500);
    config.setInitialConnectAttempts(50);
    RabbitMQConnectionImpl connection = (RabbitMQConnectionImpl) RabbitMQClient.create(testRunContext.vertx(), config);

    int port = findOpenPort();    
    GenericContainer container = new FixedHostPortGenericContainer(RabbitMQBrokerProvider.IMAGE_NAME)
            .withFixedExposedPort(port, 5672)
            ;
    config.setUri("amqp://" + container.getContainerIpAddress() + ":" + port);

    testRunContext.vertx().setTimer(1000, time -> {
      container.start();
    });
    RabbitMQChannel channel = connection.createChannel();
    long start = System.currentTimeMillis();
    Async async = context.async();
    channel.connect()
            .onComplete(ar -> {
              if (ar.succeeded()) {
                long end = System.currentTimeMillis();
                logger.info("Completing test with reconnect count = {} (expected 1 < {} < {})"
                        , connection.getReconnectCount()
                        , connection.getReconnectCount()
                        , (2000 + end - start) / config.getReconnectInterval()
                );
                context.assertTrue(connection.getReconnectCount() > 1);
                // Set an upper bound on the reconnect attempts to ensure that it's delaying between attempts.
                // Allow an extra couple of seconds to avoid race conditions.
                context.assertTrue(connection.getReconnectCount() < (2000 + end - start) / config.getReconnectInterval());
                
                connection.close().onComplete(ar2 -> {
                  async.complete();
                  container.stop();
                });
              } else {
                logger.info("Failing test");
                context.fail(ar.cause());
              }
            });
    logger.info("Ending test");    
  }
  
  public RabbitMQOptions config() {
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://" + rabbitmq.getContainerIpAddress() + ":" + rabbitmq.getMappedPort(5672));
    config.setConnectionName(this.getClass().getSimpleName());
    return config;
  }

}
