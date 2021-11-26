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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class RabbitMQFuturePublisherImpl implements RabbitMQFuturePublisher {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQFuturePublisherImpl.class);

  private final Vertx vertx;
  private final RabbitMQChannel channel;
  private final String exchange;
  private final Context context;
  private final RabbitMQPublisherOptions options;

  private String lastChannelId = null;
  //private static IndexedQueue<TaggedPromise> promises = new IndexedQueue<>();
  private static Deque<TaggedPromise> promises = new ArrayDeque<>();
    
  private static class TaggedPromise {
    final long deliveryTag;
    final Promise<Void> promise;

    TaggedPromise(long deliveryTag, Promise<Void> promise) {
      this.deliveryTag = deliveryTag;
      this.promise = promise;
    }    

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 71 * hash + (int) (this.deliveryTag ^ (this.deliveryTag >>> 32));
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final TaggedPromise other = (TaggedPromise) obj;
      if (this.deliveryTag != other.deliveryTag) {
        return false;
      }
      return true;
    }
        
  }
  
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
                    List<TaggedPromise> failedPromises = copyPromises();
                    failedPromises.forEach(tp -> tp.promise.fail("Channel reconnected"));
                  }
                  p.complete();
                } else {
                  p.fail(ar.cause());
                }
      });
    });
    this.channel.addChannelRecoveryCallback(c -> {
      context.runOnContext(v -> {
        List<TaggedPromise> failedFutures = copyPromises();
        promises.forEach(tp -> tp.promise.fail("Channel reconnected"));
      });
    });
  }

  private List<TaggedPromise> copyPromises() {
    List<TaggedPromise> failedPromises;
    synchronized(promises) {
      failedPromises = new ArrayList<>(promises.size());
      for(TaggedPromise promise : promises) {
        failedPromises.add(promise);
      }
      promises.clear();
    }
    return failedPromises;
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
      if (promises.isEmpty()) {
        log.error("Confirmation received whilst there are no pending promises!");
        return ;
      }
      TaggedPromise head = promises.getFirst();
      if (rawConfirmation.isMultiple() || (head.deliveryTag == rawConfirmation.getDeliveryTag())) {
        while(!promises.isEmpty() && promises.getFirst().deliveryTag  <= rawConfirmation.getDeliveryTag()) {
          TaggedPromise tp = promises.removeFirst();
          completePromise(tp.promise, rawConfirmation);
        }
      } else {
        log.warn("Searching for promise for {} where leading promise has :(", rawConfirmation.getDeliveryTag(), promises.getFirst().deliveryTag);
        for (TaggedPromise tp : promises) {
          if (tp.deliveryTag == rawConfirmation.getDeliveryTag()) {
            completePromise(tp.promise, rawConfirmation);
            promises.remove(tp);
            break ;
          }
        }
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
      synchronized(promises) {
        promises.addLast(new TaggedPromise(deliveryTag, promise));
      }
    });
    return promise.future();
  }
  
  
}