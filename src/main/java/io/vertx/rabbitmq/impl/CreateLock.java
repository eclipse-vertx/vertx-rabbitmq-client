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
package io.vertx.rabbitmq.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import java.util.function.Function;

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
  private final Handler<T> postCreateHandler;

  public CreateLock(ValueTest<T> test, Handler<T> postCreateHandler) {
    this.test = test == null ? v -> true : test;
    this.postCreateHandler = postCreateHandler;
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
          if (postCreateHandler != null) {
            createFuture = createFuture.compose(t -> {
              try {
                postCreateHandler.handle(t);
                return Future.succeededFuture(t);
              } catch (Throwable ex) {
                logger.warn("postCreateHandler failed: ", ex);
                return Future.failedFuture(ex);
              }
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
