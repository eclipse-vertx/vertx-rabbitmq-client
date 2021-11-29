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
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jtalbut
 */
public class RabbitMQRepublishingPublisherImpl2 implements RabbitMQFuturePublisher {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQRepublishingPublisherImpl2.class);
  
  private final Vertx vertx;
  private final RabbitMQChannel channel;
  private final String exchange;
  private final Context context;
  private final RabbitMQPublisherOptions options;

  private String lastChannelId = null;

  private final Deque<MessageDetails> promises = new ArrayDeque<>();
  private final Deque<MessageDetails> resendQueue = new ArrayDeque<>();
    
  /**
   * POD for holding message details pending acknowledgement.
   * @param <I> The type of the message IDs.
   */
  static class MessageDetails {

    private final Promise<Void> promise;
    private final String exchange;
    private final String routingKey;
    private final BasicProperties properties;
    private final byte[] message;
    private final Handler<AsyncResult<Void>> publishHandler;
    private final String channelId;
    private final long deliveryTag;

    MessageDetails(Promise<Void> promise, String exchange, String routingKey, BasicProperties properties, byte[] message, String channelId, long deliveryTag, Handler<AsyncResult<Void>> publishHandler) {
      this.promise = promise;
      this.exchange = exchange;
      this.routingKey = routingKey;
      this.properties = properties;
      this.message = message;
      this.publishHandler = publishHandler;
      this.channelId = channelId;
      this.deliveryTag = deliveryTag;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 67 * hash + (int) (this.deliveryTag ^ (this.deliveryTag >>> 32));
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
      final MessageDetails other = (MessageDetails) obj;
      if (this.deliveryTag != other.deliveryTag) {
        return false;
      }
      return true;
    }
    
    
  }
  
  public RabbitMQRepublishingPublisherImpl2(Vertx vertx, RabbitMQChannel channel, String exchange, RabbitMQPublisherOptions options) {
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
                    p.complete();
                  } else if (!lastChannelId.equals(channel.getChannelId())) {
                    List<MessageDetails> messagesToResend = copyPromises();
                    resendQueue.addAll(messagesToResend);
                    doResendAsync(p, messagesToResend.iterator());
                  } else {
                    p.complete();
                  }
                } else {
                  p.fail(ar.cause());
                }
      });
    });
    this.channel.addChannelRecoveryCallback(this::channelRecoveryCallback);
  }
  
  private void doResendAsync(Promise<Void> promise, Iterator<MessageDetails> iter) {
    if (iter.hasNext()) {
      MessageDetails md = iter.next();
      Promise<Void> publishPromise = md.promise;
      channel.basicPublish(exchange, md.routingKey, false, md.properties, md.message, deliveryTag -> {
        synchronized(promises) {
          promises.addLast(new MessageDetails(publishPromise, exchange, md.routingKey, md.properties, md.message, md.channelId, md.deliveryTag, null));
        }
      }).onFailure(ex -> {
        publishPromise.fail(ex);
      }).onComplete(ar -> {
        doResendAsync(promise, iter);
      });
    } else {
      promise.complete();
    }
  }
  
  // This is called on a RabbitMQ thread and does not involve Vertx
  // The result is rather unpleasant, but is a necessary thing when faced with Java client recoveries.
  private void channelRecoveryCallback(Channel rawChannel) {
    boolean done = false;
    while (!done) {
      MessageDetails md;
      synchronized(promises) {
        md = this.resendQueue.pollFirst();
      }
      if (md == null) {
        done = true;
      } else {
        long deliveryTag = rawChannel.getNextPublishSeqNo();
        channel.basicPublish(exchange, md.routingKey, true, md.properties, md.message);
        MessageDetails md2 = new MessageDetails(md.promise, exchange, md.routingKey, md.properties, md.message, md.channelId, md.deliveryTag, null);
        synchronized(promises) {
          promises.addLast(md2);
        }
      }
    }
  }

  private List<MessageDetails> copyPromises() {
    List<MessageDetails> failedPromises;
    synchronized(promises) {
      failedPromises = new ArrayList<>(promises);
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
      MessageDetails head = promises.getFirst();
      if (rawConfirmation.isMultiple() || (head.deliveryTag == rawConfirmation.getDeliveryTag())) {
        while(!promises.isEmpty() && promises.getFirst().deliveryTag  <= rawConfirmation.getDeliveryTag()) {
          MessageDetails tp = promises.removeFirst();
          completePromise(tp.promise, rawConfirmation);
        }
      } else {
        log.warn("Searching for promise for {} where leading promise has {}", rawConfirmation.getDeliveryTag(), promises.getFirst().deliveryTag);
        for (MessageDetails tp : promises) {
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
        promises.addLast(new MessageDetails(promise, exchange, routingKey, properties, body, channel.getChannelId(), deliveryTag, promise));
      }
    }).onFailure(ex -> {
      promise.fail(ex);
    });
    return promise.future();
  }
  
  @Override
  public Future<Void> stop() {
    synchronized(promises) {
      promises.clear();
      resendQueue.clear();
    }
    return Future.succeededFuture();
  }
  
}
