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
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
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
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
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
  
  private static final long WARMUP_ITERATIONS = 10 * 1000;
  private static final long ITERATIONS = 50 * 1000;
  
  private static final GenericContainer container = RabbitMQBrokerProvider.getRabbitMqContainer();
  
  @AfterClass
  public static void shutdown() {
    container.stop();
  }
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();
  
  @Rule
  public Timeout timeoutRule = Timeout.seconds(3600);
  
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
    config.setUri("amqp://" + container.getHost() + ":" + container.getMappedPort(5672));
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

  @Test
  public void testPerformance(TestContext testContext) {
    RabbitMQOptions config = config();
    
    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].openChannel();
            })
            .onSuccess(channel -> {
              Async async = testContext.async();

              String exchange = this.getClass().getName() + "Exchange";
              String queue = this.getClass().getName() + "Queue";

              List<RabbitMQPublisherStresser> tests = Arrays.asList(
                      new FireAndForget(connection[0])
                      , new WaitOnEachMessage(connection[0])
                      , new WaitEveryNMessages(connection[0], 10)
                      , new WaitEveryNMessages(connection[0], 100)
                      , new WaitEveryNMessages(connection[0], 1000)
                      , new Publisher(testRunContext.vertx(), connection[0], true)
                      , new Publisher(testRunContext.vertx(), connection[0], false)
              );

              channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null)
                      .compose(v -> channel.queueDeclare(queue, true, false, true, null))
                      .compose(v -> channel.queueBind(queue, exchange, "", null))
                      .compose(v -> channel.basicConsume(queue, true, getClass().getSimpleName(), false, false, null, new NullConsumer()))
                      .compose(v -> init(config.getUri(), exchange, tests.iterator()))
                      .compose(v -> runTests(tests.iterator()))
                      .compose(v -> connection[0].close())
                      .onSuccess(v -> async.complete())
                      .onFailure(ex -> {
                        logger.error("Failed: ", ex);
                        testContext.fail(ex);
                      })
                      ;
            })
            .onFailure(testContext::fail)
            ;
    
  }
  
  private Future<Void> init(String url, String exchange, Iterator<RabbitMQPublisherStresser> testIter) {
    if (testIter.hasNext()) {
      RabbitMQPublisherStresser test = testIter.next();
      return test.init(exchange)
              .compose(v -> init(url, exchange, testIter))
              ;
    } else {
      logger.info("Running performance tests with {} messages", ITERATIONS);
      return Future.succeededFuture();
    }
  }
  
  private Future<Void> runTests(Iterator<RabbitMQPublisherStresser> testIter) {
    if (testIter.hasNext()) {
      RabbitMQPublisherStresser test = testIter.next();
      
      return runTest(test)
              .compose(v -> runTests(testIter))
              ;
    } else {
      return Future.succeededFuture();
    }
  }
  
  private Future<Void> runTest(RabbitMQPublisherStresser test) {
    return testRunContext.vertx().executeBlocking(promise -> {
      test.runTest(WARMUP_ITERATIONS)
              .compose(v -> {
                return testRunContext.vertx().<Void>executeBlocking(promise2 -> {
                  long start = System.currentTimeMillis();
                  test.runTest(ITERATIONS)
                          .compose(v2 -> {
                            long end = System.currentTimeMillis();
                            long duration = end - start;
                            results.add(new Result(test.getName(), duration));
                            double seconds = duration / 1000.0;
                            logger.info("Result: {}\t{}s\t{} M/s", test.getName(), seconds, (int) (ITERATIONS / seconds));
                            return test.shutdown();
                          })
                          .onComplete(promise2);
                }).onComplete(promise);
              });
    });
   
  }
  
}
