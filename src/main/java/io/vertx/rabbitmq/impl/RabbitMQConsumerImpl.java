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
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.streams.impl.InboundBuffer;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQChannelBuilder;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQMessage;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 *
 * @author jtalbut
 */
public class RabbitMQConsumerImpl<T> implements RabbitMQConsumer<T> {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumerImpl.class);

  private final Vertx vertx;
  private final Context vertxContext;

  private RabbitMQChannel channel;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;
  private final Supplier<String> queueNameSupplier;  
  private volatile String consumerTag;
  private final boolean keepMostRecent;
  private final InboundBuffer<RabbitMQMessage<T>> pending;
  private final int maxQueueSize;
  private volatile boolean cancelled;
  private final long reconnectInterval;
  private final boolean exclusive;
  private final AtomicLong consumeCount = new AtomicLong();
  private final Map<String, Object> arguments;
  private final ConsumerBridge bridge;
  private final RabbitMQMessageCodec<T> messageCodec;

  public static <T> Future<RabbitMQConsumer<T>> create(
            RabbitMQChannelBuilder channelBuilder
          , RabbitMQMessageCodec<T> messageCodec
          , Supplier<String> queueNameSupplier
          , RabbitMQConsumerOptions options
  ) {    
    RabbitMQConsumerImpl<T> consumer = new RabbitMQConsumerImpl<>(channelBuilder, messageCodec, queueNameSupplier, options);
    return consumer.start(channelBuilder);    
  }    
    
  public RabbitMQConsumerImpl(
          RabbitMQChannelBuilder channelBuilder
          , RabbitMQMessageCodec<T> messageCodec
          , Supplier<String> queueNameSupplier
          , RabbitMQConsumerOptions options
  ) {
    this.vertx = channelBuilder.getConnection().getVertx();
    this.vertxContext = vertx.getOrCreateContext();
    this.keepMostRecent = options.isKeepMostRecent();
    this.maxQueueSize = options.getMaxInternalQueueSize();
    this.pending = new InboundBuffer<>(vertxContext, maxQueueSize);
    pending.resume();
    this.queueNameSupplier = queueNameSupplier;
    this.reconnectInterval = options.getReconnectInterval();
    this.exclusive = options.isExclusive();
    this.arguments = options.getArguments();
    this.bridge = new ConsumerBridge();
    this.messageCodec = messageCodec;
  }
  
  public Future<RabbitMQConsumer<T>> start(RabbitMQChannelBuilder channelBuilder) {
    
    return channelBuilder.openChannel()
            .compose(chann -> {
              this.channel = chann;
              Promise<String> promise = Promise.promise();
              consume(promise);
              return promise.future();
            }).map(this);
  }  

  @Override
  public RabbitMQChannel getChannel() {
    return channel;
  }
  
  private class ConsumerBridge implements Consumer {
    
    @Override
    public void handleConsumeOk(String tag) {
      log.info("handleConsumeOk " + tag);
      consumerTag = tag;
    }

    @Override
    public void handleCancelOk(String tag) {
      log.info("handleCancelOk " +tag);
    }

    @Override
    public void handleCancel(String tag) throws IOException {
      log.info("handleCancel " +tag);
    }

    @Override
    public void handleShutdownSignal(String tag, ShutdownSignalException sig) {    
      log.info("handleShutdownSignal " + tag);
      if ((reconnectInterval > 0) && !cancelled) {
        long count = consumeCount.incrementAndGet();
        log.debug("consume count: " + count);        
        consume(Promise.promise());
      }
    }

    @Override
    public void handleRecoverOk(String tag) {
      log.info("handleRecoverOk " +tag);
    }

    @Override
    public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
      log.info("Got message: " + new String(body));
      RabbitMQMessage<T> msg;
      T value = messageCodec.decodeFromBytes(body);
      msg = new RabbitMQMessageImpl<>(value, tag, envelope, properties, null);
      vertxContext.runOnContext(v -> handleMessage(msg));
    }    
  }

  private Future<String> consume(Promise<String> promise) {
    String queueName = queueNameSupplier.get();
    channel.basicConsume(queueName, false, channel.getChannelId(), false, exclusive, arguments, bridge)
            .onSuccess(consumerTag -> {
              promise.complete(consumerTag);
            })
            .onFailure(ex -> {              
              if (reconnectInterval > 0 && ! cancelled) {
                log.debug("Failed to consume " + queueName + " (" + ex.getClass() + ", \"" + ex.getMessage() + "\"), will try again after " + reconnectInterval + "ms");
                vertx.setTimer(reconnectInterval, (id) -> {
                  consume(promise);
                });
              } else {
                log.debug("Failed to consume: ", ex);
                promise.fail(ex);
              }
            });
    return promise.future();
  }

  @Override
  public RabbitMQConsumer<T> exceptionHandler(Handler<Throwable> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  @Override
  public RabbitMQConsumer<T> handler(Handler<RabbitMQMessage<T>> handler) {
    if (handler != null) {
      pending.handler(msg -> {
        try {
          handler.handle(msg);
        } catch (Exception e) {
          handleException(e);
        }
      });
    } else {
      pending.handler(null);
    }
    return this;
  }

  @Override
  public RabbitMQConsumer<T> pause() {
    pending.pause();
    return this;
  }

  @Override
  public RabbitMQConsumer<T> resume() {
    pending.resume();
    return this;
  }

  @Override
  public RabbitMQConsumer<T> fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public RabbitMQConsumer<T> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public String consumerTag() {
    return consumerTag;
  }

  @Override
  public Future<Void> cancel() {
    Promise<Void> promise = Promise.promise();
    cancel(promise);
    return promise.future();
  }

  @Override
  public void cancel(Handler<AsyncResult<Void>> cancelResult) {
    log.debug("Cancelling " + consumerTag);
    cancelled = true;
    channel.onChannel(chann -> {
      chann.basicCancel(consumerTag);
      return null;
    })
            .compose(v -> channel.close())
            .onComplete(ar -> {
              if (cancelResult != null) {
                cancelResult.handle(ar);
              }
              handleEnd();
            });
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public boolean isPaused() {
    return false;
  }

  /**
   * Push message to stream.
   * <p>
   * Should be called from a vertx thread.
   *
   * @param message received message to deliver
   */
  private void handleMessage(RabbitMQMessage<T> message) {

    if (pending.size() >= maxQueueSize) {
      if (keepMostRecent) {
        pending.read();
      } else {
        log.debug("Discard a received message since stream is paused and buffer flag is false");
        return;
      }
    }
    pending.write(message);
  }

  /**
   * Trigger exception handler with given exception
   */
  private void handleException(Throwable exception) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(exception);
    }
  }

  /**
   * Trigger end of stream handler
   */
  void handleEnd() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

}
