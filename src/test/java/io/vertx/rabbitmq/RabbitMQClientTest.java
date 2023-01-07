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
import org.junit.AfterClass;
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
  
  private static final GenericContainer container = RabbitMQBrokerProvider.getRabbitMqContainer();
  
  @AfterClass
  public static void shutdown() {
    container.stop();
  }
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();

  @Test  
  public void testCreateWithWorkingServer(TestContext context) {
    RabbitMQOptions config = config();
    
    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();

    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].openChannel();
            })
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Completing test");
                connection[0].close().onComplete(ar2 -> {
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
      testContext.assertEquals("text/plain", properties.getContentType());
      testContext.assertEquals(null, properties.getContentEncoding());
      testContext.assertEquals("Hello", new String(body, StandardCharsets.UTF_8));
      promise.complete();
    }

  }
  
  @Test  
  public void testSendMessageWithWorkingServer(TestContext context) {
    logger.debug("testSendMessageWithWorkingServer");
    RabbitMQOptions config = config();

    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQChannel conChan[] = new RabbitMQChannel[1];
    RabbitMQChannel pubChan[] = new RabbitMQChannel[1];
    
    String exchange = "testSendMessageWithWorkingServer";
    String queue = "testSendMessageWithWorkingServerQueue";
    
    Promise<Void> donePromise = Promise.promise();

    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].openChannel();
            })
            .compose(chan -> {
              conChan[0] = chan;
              return connection[0].openChannel();
            })
            .compose(chan -> {
              pubChan[0] = chan;
              return conChan[0].exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null);
            })
            .compose(v -> conChan[0].queueDeclare(queue, true, false, true, null))
            .compose(v -> conChan[0].queueBind(queue, exchange, "", null))
            .compose(v -> conChan[0].basicConsume(queue, true, getClass().getSimpleName(), false, false, null, new TestConsumer(conChan[0], context, donePromise)))
            .compose(v -> pubChan[0].exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null))
            .compose(v -> pubChan[0].confirmSelect())
            .compose(v -> pubChan[0].basicPublish(new RabbitMQPublishOptions(), exchange, "", true, new BasicProperties(), "Hello"))
            .compose(v -> pubChan[0].waitForConfirms(1000))
            .compose(v -> donePromise.future())
            .onComplete(ar -> {
              if (ar.succeeded()) {
                connection[0].close().onComplete(ar2 -> {
                  logger.info("Failing testSendMessageWithWorkingServer: ", ar.cause());
                  async.complete();
                });
              } else {
                logger.info("Failing testSendMessageWithWorkingServer: ", ar.cause());
                context.fail(ar.cause());
              }
            })
            ;
        
    logger.info("Ending testSendMessageWithWorkingServer");
  }
  
  private int findOpenPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
  
  @Test
  public void testCreateWithServerThatArrivesLate(TestContext context) throws IOException {    
    logger.debug("testCreateWithServerThatArrivesLate");
    RabbitMQOptions config = new RabbitMQOptions();
    config.setReconnectInterval(1000);
    config.setInitialConnectAttempts(50);

    // The FixedHostPortGenericContainer is deprecated because it can lead to port conflicts.
    // Unfortunately this test needs to be able to start the container on a port that is known before the container starts.
    int port = findOpenPort();    
    GenericContainer container = new FixedHostPortGenericContainer(RabbitMQBrokerProvider.IMAGE_NAME)
            .withFixedExposedPort(port, 5672)
            ;
    logger.info("Exposed port: {}", port);
    config.setUri("amqp://" + container.getHost() + ":" + port);

    RabbitMQConnectionImpl[] connection = new RabbitMQConnectionImpl[1];
    long start = System.currentTimeMillis();
    Async async = context.async();

    testRunContext.vertx().setTimer(1000, time -> {
      container.start();
      logger.debug("Newly exposed port: {}", container.getMappedPort(5672));
    });
    
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = (RabbitMQConnectionImpl) conn;
              return connection[0].openChannel();
            })
            .onComplete(ar -> {
              if (ar.succeeded()) {
                long end = System.currentTimeMillis();
                logger.info("Completing testCreateWithServerThatArrivesLate with reconnect count = {} (expected 1 < {} < {})"
                        , connection[0].getReconnectCount()
                        , connection[0].getReconnectCount()
                        , (2000 + end - start) / config.getReconnectInterval()
                );
                context.assertTrue(connection[0].getReconnectCount() > 1);
                // Set an upper bound on the reconnect attempts to ensure that it's delaying between attempts.
                // Allow an extra couple of seconds to avoid race conditions.
                context.assertTrue(connection[0].getReconnectCount() < (2000 + end - start) / config.getReconnectInterval());
                
                connection[0].close().onComplete(ar2 -> {
                  async.complete();
                  container.stop();
                });
              } else {
                logger.info("Failing testCreateWithServerThatArrivesLate");
                context.fail(ar.cause());
              }
            });
    logger.info("Ending testCreateWithServerThatArrivesLate");    
  }
  
  public RabbitMQOptions config() {
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://" + container.getHost() + ":" + container.getMappedPort(5672));
    config.setConnectionName(this.getClass().getSimpleName());
    return config;
  }

}
