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
package io.vertx.rabbitmq.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class CreateLockTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(CreateLockTest.class);
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();

  private static class TestClass {
    static AtomicInteger constructCount = new AtomicInteger();

    public TestClass() {
      constructCount.incrementAndGet();
    }
    
    public void delay() {
      try {
        Thread.sleep(100);
      } catch(InterruptedException ex) {
      }
    }
    
    public boolean test() {
      return true;
    }
    
  }
  
  @Test
  public void testCreateOnce(TestContext ctx) {
    
    Async async = ctx.async();
    
    CreateLock<TestClass> lock = new CreateLock<>(TestClass::test);    
    
    List<Future> futures = new ArrayList<>();
    for (int i = 0; i < 10; ++i) {
      futures.add(testRunContext.vertx().<String>executeBlocking(promise -> {
        lock.create(p -> {
          TestClass result = new TestClass();
          result.delay();
          p.complete(result);
        }, tc -> {
          return Future.succeededFuture("Boo");
        }).onComplete(promise);
      }));
    }
    CompositeFuture cf = CompositeFuture.all(futures);
    cf.onComplete(ar -> {
      if (ar.succeeded()) {
        ctx.assertEquals(1, TestClass.constructCount.get());
        async.complete();
      } else {
        ctx.fail(ar.cause());
      }
    });
    
  }

  @Test
  public void testUnset() {
  }

  @Test
  public void testGet() {
  }
  
}
