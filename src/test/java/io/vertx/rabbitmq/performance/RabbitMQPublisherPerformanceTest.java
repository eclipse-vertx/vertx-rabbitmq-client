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
package io.vertx.rabbitmq.performance;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.nio.NioParams;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rabbitmq.RabbitMQBrokerProvider;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQPublisherPerformanceTest {

  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQPublisherPerformanceTest.class);

  private static final int WARMUP_ITERATIONS = 10 * 1000;
  private static final int ITERATIONS = 50 * 1000;

  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();

  @BeforeClass
  public static void startup() {
    CONTAINER.start();
  }

  @AfterClass
  public static void shutdown() {
    CONTAINER.stop();
  }

  private Vertx vertx;

  @Rule
  public Timeout timeoutRule = Timeout.seconds(3600);

  @Before
  public void before() {
    vertx = Vertx.vertx();
  }

  @After
  public void after() {
    vertx.close().await();
  }

  private static class Result {
    private final String name;
    private final long durationMs;

    public Result(String name, long durationMs) {
      this.name = name;
      this.durationMs = durationMs;
    }

  }

  private final List<Result> results = new ArrayList();

  public RabbitMQOptions config() {
    ExecutorService execSvc = new ThreadPoolExecutor(8, 16, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger();
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "RabbitMQ ExecutorService " + counter.incrementAndGet());
      }
    });
    ScheduledExecutorService heartbeatSvc = new ScheduledThreadPoolExecutor(4, new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger();
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "RabbitMQ HeartbeatThread " + counter.incrementAndGet());
      }
    });
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5672));
    config.setNioParams(new NioParams().setWriteQueueCapacity(Math.max(ITERATIONS, WARMUP_ITERATIONS)));
    config.setConnectionName(this.getClass().getSimpleName());
    config.setHeartbeatExecutor(heartbeatSvc);
    config.setSharedExecutor(execSvc);
    config.setShutdownExecutor(execSvc);
    config.setThreadFactory(new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger();
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "RabbitMQ " + counter.incrementAndGet());
      }
    });
    return config;
  }

  private static final class NullConsumer implements Consumer {

    @Override
    public void handleConsumeOk(String consumerTag) {
    }

    @Override
    public void handleCancelOk(String consumerTag) {
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
    }

    @Override
    public void handleRecoverOk(String consumerTag) {
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
    }

  }

  RabbitMQConnection connection;
  RabbitMQChannel consumerChannel;
  String consumerTag;

  @Test
  public void testPerformance(TestContext testContext) {
    RabbitMQOptions config = config();

    String exchange = this.getClass().getName() + "Exchange";
    String queue = this.getClass().getName() + "Queue";

    List<RabbitMQPublisherStresser> tests = Arrays.asList(
            new FireAndForget()
            , new WaitOnEachMessage()
            , new WaitEveryNMessages(10)
            , new WaitEveryNMessages(100)
            , new WaitEveryNMessages(1000)
            , new Publisher(true)
            , new Publisher(false)
    );

    connection = RabbitMQClient.connect(vertx, config).await();
    consumerChannel = connection.createChannelBuilder()
      .withChannelOpenHandler(rawChannel -> {
        rawChannel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null);
        rawChannel.queueDeclare(queue, true, false, true, null);
        rawChannel.queueBind(queue, exchange, "", null);
      })
      // Consumer channel
      .openChannel().await();
    consumerTag = consumerChannel.basicConsume(queue, true, getClass().getSimpleName(), false, false, null, new NullConsumer()).await();
    init(config.getUri(), exchange, tests.iterator());
    runTests(tests.iterator());
    logger.info("Cancelling consumer");
    consumerChannel.basicCancel(consumerTag).await();
    logger.info("Clsing connection");
    connection.close().await();
  }

  private void init(String url, String exchange, Iterator<RabbitMQPublisherStresser> testIter) {
    while (testIter.hasNext()) {
      RabbitMQPublisherStresser test = testIter.next();
      test.init(connection, exchange).await();
      init(url, exchange, testIter);
    }
    logger.info("Running performance tests with {} messages", ITERATIONS);
  }

  private void runTests(Iterator<RabbitMQPublisherStresser> testIter) {
    while (testIter.hasNext()) {
      RabbitMQPublisherStresser test = testIter.next();
      runTest(test);
    }
  }

  private void runTest(RabbitMQPublisherStresser test) {
    test.runTest(WARMUP_ITERATIONS).await();
    long start = System.currentTimeMillis();
    test.runTest(ITERATIONS)
      .compose(v2 -> {
        long end = System.currentTimeMillis();
        long duration = end - start;
        results.add(new Result(test.getName(), duration));
        double seconds = duration / 1000.0;
        logger.info("Result: {}\t{}s\t{} M/s", test.getName(), seconds, (int) (ITERATIONS / seconds));
        return test.shutdown();
      }).await();
  }
}
