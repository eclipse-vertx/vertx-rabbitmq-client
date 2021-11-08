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
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.streams.impl.InboundBuffer;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQMessage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author jtalbut
 */
public class RabbitMQConsumerImpl implements RabbitMQConsumer {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumerImpl.class);

  private final RabbitMQChannelImpl channel;
  private final Context vertxContext;

  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;
  private String queueName;  
  private volatile String consumerTag;
  private final boolean keepMostRecent;
  private final InboundBuffer<RabbitMQMessage> pending;
  private final int maxQueueSize;
  private volatile boolean cancelled;
  private final boolean shouldReconnect;
  private boolean exclusive;
  private AtomicLong consumeCount = new AtomicLong();
  private Map<String, Object> arguments;

  public RabbitMQConsumerImpl(Context vertxContext, RabbitMQChannelImpl channel, String queueName, RabbitMQConsumerOptions options, boolean shouldReconnect) {
    this.channel = channel;

    this.vertxContext = vertxContext;
    this.keepMostRecent = options.isKeepMostRecent();
    this.maxQueueSize = options.getMaxInternalQueueSize();
    this.pending = new InboundBuffer<>(vertxContext, maxQueueSize);
    pending.resume();
    this.queueName = queueName;    
    this.shouldReconnect = shouldReconnect;
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
      log.info("handleShutdownSignal " +tag);
      if (shouldReconnect && !cancelled) {
        long count = consumeCount.incrementAndGet();
        channel.basicConsume(queueName, false, channel.getChannelId(), false, exclusive, arguments, new ConsumerBridge());
      }
    }

    @Override
    public void handleRecoverOk(String tag) {
      log.info("handleRecoverOk " +tag);
    }

    @Override
    public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
      log.info("Got message: " + new String(body));
      RabbitMQMessage msg = new RabbitMQMessageImpl(body, tag, envelope, properties, null);
      vertxContext.runOnContext(v -> handleMessage(msg));
    }    
  }

  @Override
  public Future<String> consume(boolean exclusive, Map<String, Object> arguments) {
    if (!consumeCount.compareAndSet(0, 1)) {
      throw new IllegalStateException("consume has already been called");
    }
    this.exclusive = exclusive;
    this.arguments = arguments;
    return channel.connect()
            .compose(v -> {
              return channel.basicConsume(queueName, false, channel.getChannelId(), false, exclusive, arguments, new ConsumerBridge());
            })
            ;
  }
  
  @Override
  public String queueName() {
    return queueName;
  }

  @Override
  public RabbitMQConsumer setQueueName(String name) {
    this.queueName = name;
    return this;
  }

  @Override
  public RabbitMQConsumer exceptionHandler(Handler<Throwable> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  @Override
  public RabbitMQConsumer handler(Handler<RabbitMQMessage> handler) {
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
  public RabbitMQConsumer pause() {
    pending.pause();
    return this;
  }

  @Override
  public RabbitMQConsumer resume() {
    pending.resume();
    return this;
  }

  @Override
  public RabbitMQConsumer fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public RabbitMQConsumer endHandler(Handler<Void> endHandler) {
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
    channel.basicCancel(consumerTag)
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
  private void handleMessage(RabbitMQMessage message) {

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
