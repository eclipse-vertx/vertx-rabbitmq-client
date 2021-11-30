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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is intended to be the one Publisher to rule them all.
 * The spead of the Future Publisher with the option of republishing when connections are re-established (which other tests have shown does slow it down slightly).
 * @author jtalbut
 */
public class RabbitMQFuturePublisherImpl2 implements RabbitMQFuturePublisher {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQFuturePublisherImpl2.class);
  
  private final Vertx vertx;
  private final RabbitMQChannel channel;
  private final String exchange;
  private final Context context;
  private final boolean resendOnReconnect;

  private String lastChannelId = null;

  private final Deque<TaggedPromise> promises = new ArrayDeque<>();
    
  /**
   * POD for holding a delivery tag and the promise to be completed when that delivery tag is confirmed.
   */
  private static class TaggedPromise {
    final String channelId;
    final long deliveryTag;
    final Promise<Void> promise;

    TaggedPromise(String channelId, long deliveryTag, Promise<Void> promise) {
      this.channelId = channelId;
      this.deliveryTag = deliveryTag;
      this.promise = promise;
    }    

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 17 * hash + Objects.hashCode(this.channelId);
      hash = 17 * hash + (int) (this.deliveryTag ^ (this.deliveryTag >>> 32));
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
      if (!Objects.equals(this.channelId, other.channelId)) {
        return false;
      }
      return true;
    }
  }
  
  
  /**
   * POD extending the TaggedPromise to hold enough data to resend a message.
   * Note that, as with the TaggedPromise, equality is governed entirely by the channelId and deliveryTag.
   */
  private static class MessageDetails extends TaggedPromise {

    final String routingKey;
    final BasicProperties properties;
    final byte[] message;

    MessageDetails(String channelId, long deliveryTag, Promise<Void> promise, String routingKey, BasicProperties properties, byte[] message) {
      super(channelId, deliveryTag, promise);
      this.routingKey = routingKey;
      this.properties = properties;
      this.message = message;
    }
  }
  
  public RabbitMQFuturePublisherImpl2(Vertx vertx, RabbitMQChannel channel, String exchange, RabbitMQPublisherOptions options, boolean resendOnReconnect) {
    this.vertx = vertx;
    this.channel = channel;
    this.exchange = exchange;
    this.resendOnReconnect = resendOnReconnect;
    this.context = vertx.getOrCreateContext();
    this.channel.addChannelEstablishedCallback(p -> {
      addConfirmListener(options.getMaxInternalQueueSize())
              .onComplete(ar -> {                
                if (ar.succeeded()) {
                  if (lastChannelId == null) {
                    lastChannelId = channel.getChannelId();
                    p.complete();
                  } else if (!lastChannelId.equals(channel.getChannelId())) {
                    if (resendOnReconnect) {
                      List<TaggedPromise> messagesToResend = copyPromises();
                      doResendAsync(p, messagesToResend.iterator());
                    } else {
                      lastChannelId = channel.getChannelId();
                      List<TaggedPromise> failedPromises = copyPromises();
                      failedPromises.forEach(tp -> tp.promise.fail("Channel reconnected"));
                      p.complete();
                    }
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
  
  private void doResendAsync(Promise<Void> promise, Iterator<TaggedPromise> iter) {
    if (iter.hasNext()) {
      MessageDetails md = (MessageDetails) iter.next();
      Promise<Void> publishPromise = md.promise;
      channel.basicPublish(exchange, md.routingKey, false, md.properties, md.message, deliveryTag -> {
        synchronized(promises) {
          promises.addLast(new MessageDetails(md.channelId, md.deliveryTag, publishPromise, md.routingKey, md.properties, md.message));
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
    List<TaggedPromise> messagesToResend = copyPromises();
    for (TaggedPromise tp : messagesToResend) {
      MessageDetails md = (MessageDetails) tp;
      long deliveryTag = rawChannel.getNextPublishSeqNo();
      channel.basicPublish(exchange, md.routingKey, true, md.properties, md.message);
      MessageDetails md2 = new MessageDetails(md.channelId, deliveryTag, md.promise, md.routingKey, md.properties, md.message);
      synchronized(promises) {
        promises.addLast(md2);
      }
    }
  }

  private List<TaggedPromise> copyPromises() {
    List<TaggedPromise> failedPromises;
    synchronized(promises) {
      failedPromises = new ArrayList<>(promises);
      promises.clear();
    }
    return failedPromises;
  }
  
  protected final Future<Void> addConfirmListener(int maxQueueSize) {
    return channel.addConfirmListener(maxQueueSize)
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
    List<TaggedPromise> toComplete = new ArrayList<>();
    synchronized(promises) {
      if (promises.isEmpty()) {
        log.error("Confirmation received whilst there are no pending promises!");
        return ;
      }
      TaggedPromise head = promises.getFirst();
      if (rawConfirmation.isMultiple() || (head.deliveryTag == rawConfirmation.getDeliveryTag())) {
        while(!promises.isEmpty() && promises.getFirst().deliveryTag  <= rawConfirmation.getDeliveryTag()) {
          TaggedPromise tp = promises.removeFirst();
          toComplete.add(tp);
        }
      } else {
        log.warn("Searching for promise for {} where leading promise has {}", rawConfirmation.getDeliveryTag(), promises.getFirst().deliveryTag);
        for (TaggedPromise tp : promises) {
          if (tp.deliveryTag == rawConfirmation.getDeliveryTag()) {
            toComplete.add(tp);
            promises.remove(tp);
            break ;
          }
        }
      }
    }
    for (TaggedPromise tp : toComplete) {
      completePromise(tp.promise, rawConfirmation);
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
        if (resendOnReconnect) {
          promises.addLast(new MessageDetails(channel.getChannelId(), deliveryTag, promise, routingKey, properties, body));
        } else {
          promises.addLast(new TaggedPromise(channel.getChannelId(), deliveryTag, promise));
        }
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
    }
    return Future.succeededFuture();
  }
  
}
