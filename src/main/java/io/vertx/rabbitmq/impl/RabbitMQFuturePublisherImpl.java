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

import com.rabbitmq.client.AMQP;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.rabbitmq.RabbitMQFuturePublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class RabbitMQFuturePublisherImpl implements RabbitMQFuturePublisher {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQPublisherImpl.class);

  private final Vertx vertx;
  private final RabbitMQChannel channel;
  private final String exchange;
  private final Context context;
  private final RabbitMQPublisherOptions options;

  private String lastChannelId = null;
  private long head = 0;
  private static IndexedQueue<Promise<Void>> promises = new IndexedQueue<>();
  
  public RabbitMQFuturePublisherImpl(Vertx vertx, RabbitMQChannel channel, String exchange, RabbitMQPublisherOptions options) {
    this.vertx = vertx;
    this.channel = channel;
    this.exchange = exchange;
    this.options = options;
    this.context = vertx.getOrCreateContext();
    this.channel.addChannelEstablishedCallback(p -> {
      addConfirmListener()
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  if (lastChannelId == null) {
                    lastChannelId = channel.getChannelId();
                  } else if (!lastChannelId.equals(channel.getChannelId())) {
                    context.runOnContext(v -> {
                      List<Promise> failedFutures = copyPromises();
                      failedFutures.forEach(promise -> promise.fail("Channel reconnected"));
                    });
                  }
                  p.complete();
                } else {
                  p.fail(ar.cause());
                }
      });
    });
    this.channel.addChannelRecoveryCallback(c -> {
      context.runOnContext(v -> {
        List<Promise> failedFutures = copyPromises();
        promises.forEach(p -> p.fail("Channel reconnected"));
      });
    });
  }

  private List<Promise> copyPromises() {
    List<Promise> failedFutures;
    synchronized(promises) {
      failedFutures = new ArrayList<>(promises.size());
      for(Promise promise : promises) {
        failedFutures.add(promise);
      }
      promises.clear();
    }
    return failedFutures;
  }
  
  protected final Future<Void> addConfirmListener() {
    return channel.addConfirmListener(options.getMaxInternalQueueSize())
            .onComplete(ar -> {
              if (ar.succeeded()) {
                ar.result().handler(confirmation -> {
                  handleConfirmation(confirmation);
                });
              } else {
                log.error("Failed to add confirmListener: ", ar.cause());
              }
            })
            .mapEmpty();
  }
  
  private void handleConfirmation(RabbitMQConfirmation rawConfirmation) {
    synchronized(promises) {
      if (rawConfirmation.isMultiple() || rawConfirmation.getDeliveryTag() == head) {
        while(head <= rawConfirmation.getDeliveryTag()) {
          ++head;
          Promise<Void> promise = promises.removeFirst();
          completePromise(promise, rawConfirmation);
        }
      } else {
        long index = rawConfirmation.getDeliveryTag() - head;
        Promise<Void> promise = promises.get((int) index);
        promises.set((int) index, null);
        completePromise(promise, rawConfirmation);
      }
    }
  }

  private void completePromise(Promise<Void> promise, RabbitMQConfirmation rawConfirmation) {
    if (promise != null) {
      if (rawConfirmation.isSucceeded()) {
        promise.complete();
      } else {
        promise.fail("Negative confirmation received");
      }
    }
  }

  @Override
  public Future<Void> publish(String routingKey, AMQP.BasicProperties properties, Buffer body) {
    return publish(routingKey, properties, body.getBytes());
  }

  @Override
  public Future<Void> publish(String routingKey, AMQP.BasicProperties properties, byte[] body) {
    Promise<Void> promise = Promise.promise();
    channel.basicPublish(exchange, routingKey, false, properties, body, deliveryTag -> {
      log.info("Message {} has delivery tag {}", new String(body), deliveryTag);
      synchronized(promises) {
        promises.set((int) (deliveryTag - head), promise);
      }
    });
    return promise.future();
  }
  
  
}