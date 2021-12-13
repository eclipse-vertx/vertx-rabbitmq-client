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
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.rabbitmq.RabbitMQPublisher;
import java.io.IOException;
import java.util.Collection;

/**
 * This is intended to be the one Publisher to rule them all.
 * The spead of the Future Publisher with the option of republishing when connections are re-established (which other tests have shown does slow it down slightly).
 * @author jtalbut
 */
public class RabbitMQPublisherImpl<T> implements RabbitMQPublisher<T> {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQPublisherImpl.class);
  
  private final Vertx vertx;
  private final RabbitMQChannelImpl channel;
  private final String exchange;
  private final boolean resendOnReconnect;
  private final RabbitMQMessageCodec<T> messageCodec;

  private String lastChannelId = null;

  private final Deque<MessageDetails> promises = new ArrayDeque<>();
  private final Deque<MessageDetails> resend = new ArrayDeque<>();
      
  /**
   * POD holding the details of the promise to be completed and enough data to resend a message if required.
   * Note that equality is governed entirely by the channelId and deliveryTag.
   */
  private class MessageDetails {

    final String channelId;
    final long deliveryTag;
    final Promise<Void> promise;

    final String routingKey;
    final BasicProperties properties;
    final byte[] message;

    MessageDetails(String channelId, long deliveryTag, Promise<Void> promise, String routingKey, BasicProperties properties, byte[] message) {
      this.channelId = channelId;
      this.deliveryTag = deliveryTag;
      this.promise = promise;
      this.routingKey = routingKey;
      this.properties = properties;
      this.message = message;
    }

    public MessageDetails(String channelId, long deliveryTag, Promise<Void> promise) {
      this.channelId = channelId;
      this.deliveryTag = deliveryTag;
      this.promise = promise;
      this.routingKey = null;
      this.properties = null;
      this.message = null;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 41 * hash + Objects.hashCode(this.channelId);
      hash = 41 * hash + (int) (this.deliveryTag ^ (this.deliveryTag >>> 32));
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
      if (!Objects.equals(this.channelId, other.channelId)) {
        return false;
      }
      return true;
    }
  }
  
  public RabbitMQPublisherImpl(Vertx vertx, RabbitMQChannelImpl channel, RabbitMQMessageCodec<T> messageCodec, String exchange, RabbitMQPublisherOptions options) {
    this.vertx = vertx;
    this.channel = channel;
    this.exchange = exchange;
    this.resendOnReconnect = options.isResendOnReconnect();
    this.messageCodec = messageCodec;
    this.channel.addChannelEstablishedCallback(p -> {
      addConfirmListener()
              .onComplete(ar -> {                
                if (ar.succeeded()) {
                  if (lastChannelId == null) {
                    lastChannelId = channel.getChannelId();
                    p.complete();
                  } else if (!lastChannelId.equals(channel.getChannelId())) {
                    if (resendOnReconnect) {
                      copyPromises(resend);
                      doResendAsync(p);
                    } else {
                      lastChannelId = channel.getChannelId();
                      List<MessageDetails> failedPromises = new ArrayList<>();
                      copyPromises(failedPromises);
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
  
  private Future<Void> doResendAsync(Promise<Void> promise) {
    MessageDetails md;
    synchronized(promises) {
      md = resend.pollFirst();
    }
    if (md == null) {
      promise.complete();
    } else {
      Promise<Void> publishPromise = md.promise;
      channel.basicPublish(new RabbitMQPublishOptions().setDeliveryTagHandler(deliveryTag -> {
        synchronized(promises) {
          promises.addLast(new MessageDetails(md.channelId, md.deliveryTag, publishPromise, md.routingKey, md.properties, md.message));
        }
      }), exchange, md.routingKey, false, md.properties, md.message).onFailure(ex -> {
        publishPromise.fail(ex);
      }).onComplete(ar -> {
        doResendAsync(promise);
      });
    }
    return promise.future();
  }
  
  // This is called on a RabbitMQ thread and does not involve Vertx
  // The result is rather unpleasant, but is a necessary thing when faced with Java client recoveries.
  private void channelRecoveryCallback(Channel rawChannel) {
    boolean done = false;
    copyPromises(resend);    
    synchronized(promises) {
      log.debug("Connection recovered, resending {} messages", resend.size());
      for (MessageDetails md : resend) {
        long deliveryTag = rawChannel.getNextPublishSeqNo();
        try {
          rawChannel.basicPublish(exchange, md.routingKey, md.properties, md.message);
          MessageDetails md2 = new MessageDetails(md.channelId, deliveryTag, md.promise, md.routingKey, md.properties, md.message);
          promises.addLast(md2);
        } catch(IOException ex) {
          resend.addFirst(md);
        }
      }
    }
  }

  private void copyPromises(Collection<MessageDetails> target) {
    synchronized(promises) {
      for (MessageDetails md : promises) {
        target.add(md);
      }
      promises.clear();
    }
  }
  
  protected final Future<Void> addConfirmListener() {
    return channel.addConfirmHandler(confirmation -> handleConfirmation(confirmation));
  }
  
  private void handleConfirmation(RabbitMQConfirmation rawConfirmation) {
    List<MessageDetails> toComplete = new ArrayList<>();
    synchronized(promises) {
      if (promises.isEmpty()) {
        log.error("Confirmation received whilst there are no pending promises!");
        return ;
      }
      MessageDetails head = promises.getFirst();
      if (rawConfirmation.isMultiple() || (head.deliveryTag == rawConfirmation.getDeliveryTag())) {
        while(!promises.isEmpty() && promises.getFirst().deliveryTag  <= rawConfirmation.getDeliveryTag()) {
          MessageDetails tp = promises.removeFirst();
          toComplete.add(tp);
        }
      } else {
        log.warn("Searching for promise for {} where leading promise has {}", rawConfirmation.getDeliveryTag(), promises.getFirst().deliveryTag);
        for (MessageDetails tp : promises) {
          if (tp.deliveryTag == rawConfirmation.getDeliveryTag()) {
            toComplete.add(tp);
            promises.remove(tp);
            break ;
          }
        }
      }
    }
    for (MessageDetails tp : toComplete) {
      completePromise(tp.promise, rawConfirmation);
    }
  }

  private void completePromise(Promise<Void> promise, RabbitMQConfirmation rawConfirmation) {
    if (promise != null) {
      if (rawConfirmation.isSucceeded()) {
        promise.tryComplete();
      } else {
        promise.tryFail("Negative confirmation received");
      }
    }
  }

  @Override
  public Future<Void> publish(String routingKey, AMQP.BasicProperties properties, T passedBody) {
    Promise<Void> promise = Promise.promise();
    
    RabbitMQMessageCodec codec = (messageCodec == null) ? channel.getCodecManager().lookupCodec(passedBody, null) : messageCodec;
    if (!Objects.equals(codec.getContentEncoding(), properties.getContentEncoding()) 
            || !Objects.equals(codec.getContentType(), properties.getContentType())) {
      properties = RabbitMQChannelImpl.setTypeAndEncoding(properties, codec.getContentType(), codec.getContentEncoding());
    }
    AMQP.BasicProperties finalProps = properties;    
    
    byte[] preppedBody = codec.encodeToBytes(passedBody);
    
    channel.basicPublish(new RabbitMQPublishOptions()
            .setDeliveryTagHandler(deliveryTag -> {
      synchronized(promises) {
        if (resendOnReconnect) {
          promises.addLast(new MessageDetails(channel.getChannelId(), deliveryTag, promise, routingKey, finalProps, preppedBody));
        } else {
          promises.addLast(new MessageDetails(channel.getChannelId(), deliveryTag, promise));
        }
      }
    }), exchange, routingKey, false, properties, preppedBody).onFailure(ex -> {
      if (resendOnReconnect && ex instanceof AlreadyClosedException) {
        synchronized(promises) {
          resend.addLast(new MessageDetails(channel.getChannelId(), -1, promise, routingKey, finalProps, preppedBody));
        }
      } else {
        promise.fail(ex);
      }
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
