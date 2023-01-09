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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.rabbitmq.AsyncHandler;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import io.vertx.rabbitmq.RabbitMQPublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * This is intended to be the one Publisher to rule them all.
 * The spead of the Future Publisher with the option of republishing when connections are re-established (which other tests have shown does slow it down slightly).
 * @author jtalbut
 */
public class RabbitMQPublisherImpl<T> implements RabbitMQPublisher<T> {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQPublisherImpl.class);
  
  private final Vertx vertx;
  private final RabbitMQConnection connection;
  private final String exchange;
  private final boolean resendOnReconnect;
  private final RabbitMQMessageCodec<T> messageCodec;

  private RabbitMQChannel channel;
  private RabbitMQCodecManager codecManager;
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
  
  public static <T> Future<RabbitMQPublisher<T>> create(
          Vertx vertx
          , RabbitMQConnection connection
          , AsyncHandler<RabbitMQChannel> channelOpenedHandler
          , RabbitMQMessageCodec<T> messageCodec
          , String exchange
          , RabbitMQPublisherOptions options) {
    
    RabbitMQPublisherImpl<T> publisher = new RabbitMQPublisherImpl<>(vertx, connection, messageCodec, exchange, options);
    return publisher.start(channelOpenedHandler);    
  }    

  private Future<Void> performResends(RabbitMQChannel newChannel) {
    if (lastChannelId == null) {
      lastChannelId = newChannel.getChannelId();
      return Future.succeededFuture();
    } else if (!lastChannelId.equals(newChannel.getChannelId())) {
      if (resendOnReconnect) {
        copyPromises(resend);
        Promise p = Promise.promise();
        doResendAsync(p);
        return p.future();
      } else {
        lastChannelId = newChannel.getChannelId();
        List<MessageDetails> failedPromises = new ArrayList<>();
        copyPromises(failedPromises);
        failedPromises.forEach(tp -> tp.promise.fail("Channel reconnected"));
        return Future.succeededFuture();
      }
    } else {
      return Future.succeededFuture();
    }
  }
  
  public RabbitMQPublisherImpl(Vertx vertx, RabbitMQConnection connection, RabbitMQMessageCodec<T> messageCodec, String exchange, RabbitMQPublisherOptions options) {
    this.vertx = vertx;
    this.connection = connection;
    this.exchange = exchange;
    this.resendOnReconnect = options.isResendOnReconnect();
    this.messageCodec = messageCodec;
  }
  
  public Future<RabbitMQPublisher<T>> start(AsyncHandler<RabbitMQChannel> channelOpenedHandler) {
    return connection.openChannel(chann -> {
      return channelOpenedHandler.handle(chann)
              .compose(v -> {
                return chann.addConfirmHandler(confirmation -> handleConfirmation(confirmation));
              }).compose(v -> {
                return performResends(chann);
              });
    }).compose(chann -> {
      this.channel = chann;
      codecManager = ((RabbitMQChannelImpl) channel).getCodecManager();
      channel.addChannelRecoveryCallback(this::channelRecoveryCallback);
      return Future.succeededFuture();
    }).map(this);
  }

  @Override
  public RabbitMQChannel getChannel() {
    return channel;
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
    copyPromises(resend);    
    synchronized(promises) {
      log.debug("Connection recovered, resending " + resend.size() + " messages");
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
  
  private void handleConfirmation(RabbitMQConfirmation rawConfirmation) {
    log.debug("handleConfirmation(" + rawConfirmation.getChannelId() + ":" + rawConfirmation.getDeliveryTag() + ")");
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
        log.warn("Searching for promise for " + rawConfirmation.getDeliveryTag() + " where leading promise has " + promises.getFirst().deliveryTag);
        for (MessageDetails tp : promises) {
          if (tp.deliveryTag == rawConfirmation.getDeliveryTag()) {
            toComplete.add(tp);
            promises.remove(tp);
            break ;
          }
        }
      }
      log.info("Found " + toComplete.size() + " promises to complete, out of " + promises.size());
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
    
    RabbitMQMessageCodec codec = (messageCodec == null) ? codecManager.lookupCodec(passedBody, null) : messageCodec;
    if ((codec.getContentEncoding() != null && !Objects.equals(codec.getContentEncoding(), properties.getContentEncoding()))
            || (codec.getContentType() != null && !Objects.equals(codec.getContentType(), properties.getContentType()))) {
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
  public Future<Void> pause() {
    synchronized(promises) {
      promises.clear();
    }
    return Future.succeededFuture();
  }
  
  @Override
  public Future<Void> cancel() {
    synchronized(promises) {
      promises.clear();
    }    
    return channel.close();
  }
  
}
