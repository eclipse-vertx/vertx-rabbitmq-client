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
import io.vertx.rabbitmq.RabbitMQClientTest;
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
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQClientTest.class);
  
  private static final long WARMUP_ITERATIONS = 10 * 1000;
  private static final long ITERATIONS = 50 * 1000;
  
  @ClassRule
  public static final GenericContainer rabbitmq = RabbitMQBrokerProvider.getRabbitMqContainer();
  
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
    config.setUri("amqp://" + rabbitmq.getContainerIpAddress() + ":" + rabbitmq.getMappedPort(5672));
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
  public void testPerformance(TestContext context) {
    RabbitMQOptions config = config();
    RabbitMQConnection connection = RabbitMQClient.create(testRunContext.vertx(), config);

    RabbitMQChannel channel = connection.createChannel();
    Async async = context.async();
    
    String exchange = this.getClass().getName() + "Exchange";
    String queue = this.getClass().getName() + "Queue";
    
    List<RabbitMQPublisherStresser> tests = Arrays.asList(
            new FireAndForget(connection)
            , new WaitOnEachMessage(connection)
            , new WaitEveryNMessages(connection, 10)
            , new WaitEveryNMessages(connection, 100)
            , new WaitEveryNMessages(connection, 1000)
            , new FuturePublisherWithRetries(testRunContext.vertx(), connection)
            , new FuturePublisherWithoutRetries(testRunContext.vertx(), connection)
            , new FuturePublisher(connection)
            , new RepublishingPublisher(connection)
            , new RepublishingPublisher2(testRunContext.vertx(), connection)
    );

    channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true, false, null)
            .compose(v -> channel.queueDeclare(queue, true, false, true, null))
            .compose(v -> channel.queueBind(queue, exchange, "", null))
            .compose(v -> channel.basicConsume(queue, true, getClass().getSimpleName(), false, false, null, new NullConsumer()))
            .compose(v -> init(config.getUri(), exchange, tests.iterator()))
            .compose(v -> runTests(tests.iterator()))
            .compose(v -> connection.close())
            .onSuccess(v -> async.complete())
            .onFailure(ex -> {
              logger.error("Failed: ", ex);
              context.fail(ex);
            })
            ;
    
  }
  
  private Future<Void> init(String url, String exchange, Iterator<RabbitMQPublisherStresser> testIter) {
    if (testIter.hasNext()) {
      RabbitMQPublisherStresser test = testIter.next();
      return test.init(exchange)
              .compose(v -> init(url, exchange, testIter))
              ;
    } else {
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
                long start = System.currentTimeMillis();
                return test.runTest(ITERATIONS)
                        .compose(v2 -> {
                          long end = System.currentTimeMillis();
                          long duration = end - start;
                          results.add(new Result(test.getName(), duration));
                          double seconds = duration / 1000.0;
                          logger.info("Result: {}\t{}s\t{} M/s", test.getName(), seconds, (int) (ITERATIONS / seconds));
                          return test.shutdown();
                        });
              })
              .onComplete(promise);
    });
   
  }
  
}
