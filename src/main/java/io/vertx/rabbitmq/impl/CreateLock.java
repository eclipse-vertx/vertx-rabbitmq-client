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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class CreateLock<T> {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(CreateLock.class);
  
  @FunctionalInterface
  public interface ValueTest<T> {
    /**
     * Test that the value is in a usable state, return false if it is not.
     * @param value The value to be tested, will not be null.
     * @return true if the value is usable, false otherwise.
     */
    boolean handle(T value);
  }
  
  private final ValueTest<T> test;
  private final Object lock = new Object();
  private Future<T> createFuture;
  private boolean creating;
  private T value;
  private final List<Handler<Promise<Void>>> postCreateHandlers = new ArrayList<>();

  public CreateLock(ValueTest<T> test) {
    this.test = test == null ? v -> true : test;
  }
  
  public void addPostCreateHandler(Handler<Promise<Void>> postCreateHandler) {
    synchronized(lock) {
      postCreateHandlers.add(postCreateHandler);
    }
  }
  
  private void postCreateHandler(T val, AsyncResult<Void> prevResult, Iterator<Handler<Promise<Void>>> iter, Promise<T> createPromise) {
    try {
      if (prevResult != null && prevResult.failed()) {
        createPromise.fail(prevResult.cause());
      } else {
        if (iter.hasNext()) {
          Handler<Promise<Void>> next = iter.next();
          Promise<Void> callbackPromise = Promise.promise();
          next.handle(callbackPromise);
          callbackPromise.future().onComplete(result -> postCreateHandler(val, result, iter, createPromise));
        } else {
          createPromise.complete(val);
        }
      }
    } catch (Throwable ex) {
      logger.error("Exception whilst running post create handler: ", ex);
      createPromise.fail(ex);
    }
  }
  
  public <R> Future<R> create(
          Handler<Promise<T>> creator
          , Function<T, Future<R>> handler
  ) {    
    boolean doCreate = false;
    T currentValue = null;
    Promise<R> result = null;
    Promise<T> createPromise = null;
    synchronized(lock) {
      if (value == null || !test.handle(value)) {
        if (createFuture == null) {
          createPromise = Promise.promise();
          createFuture = createPromise.future();          
          createFuture.onComplete(ar -> {
            synchronized(lock) {
              if (ar.failed()) {
                logger.warn("Creation failed: ", ar.cause());
              } else {
                value = ar.result();
              }
              creating = false;
              createFuture = null;
            }
          });
          List<Handler<Promise<Void>>> callbacks;
          synchronized(lock) {
            callbacks = new ArrayList<>(postCreateHandlers);
          } 
          if (!callbacks.isEmpty()) {
            createFuture = createFuture.compose(val -> {
              Promise<T> postCreatePromise = Promise.promise();
              postCreateHandler(val, null, callbacks.iterator(), postCreatePromise);
              return postCreatePromise.future();
            });
          }
        }
        if (!creating) {
          doCreate = true;
          creating = true;
        }
        Promise promise = Promise.promise();
        createFuture.onComplete(ar -> {
          if (ar.succeeded()) {
            handler.apply(ar.result()).onComplete(promise);            
          } else {
            promise.fail(ar.cause());
          }
        });
        result = promise;
      } else {
        currentValue = value;
      }
    }
    if (currentValue != null) {
      return handler.apply(currentValue);
    } else {
      if (doCreate) {
        creator.handle(createPromise);
      }
      return result.future();
    }
  }
  
  public void unset() {
    synchronized(lock) {
      value = null;
    }
  }
  
  public T get() {
    return value;
  }
  
}
