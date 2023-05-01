/*
 * Copyright 2023 Eclipse.
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
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQChannelBuilder;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQMessage;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 *
 * @author jtalbut
 */
public class RabbitMQConsumerImpl<T> implements RabbitMQConsumer, Consumer {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumerImpl.class);
  
  private final RabbitMQConnectionImpl connection;
  private final Handler<RabbitMQConsumer> consumerOkHandler;
  private final Handler<RabbitMQConsumer> cancelOkHandler;
  private final Handler<RabbitMQConsumer> cancelHandler;
  private final Handler<RabbitMQConsumer> shutdownSignalHandler;
  private final Handler<RabbitMQConsumer> recoverOkHandler;
  private final long reconnectIntervalMs;
  private final boolean autoAck;
  private final boolean exclusive;
  private final Map<String, Object> arguments;
  private final Supplier<String> queueNameSupplier;
  
  private final RabbitMQMessageCodec<T> messageCodec;

  private final BiFunction<RabbitMQConsumer, RabbitMQMessage<T>, Future<Void>> handler;
  
  private RabbitMQChannel channel;
  private String consumerTag;
  private boolean cancelled;
  private final Context vertxContext;
  private final AtomicLong consumeCount = new AtomicLong();

  public static <U> Future<RabbitMQConsumer> create(
          RabbitMQChannelBuilder channelBuilder
          , RabbitMQMessageCodec<U> messageCodec
          , Supplier<String> queueNameSupplier
          , RabbitMQConsumerOptions options
          , BiFunction<RabbitMQConsumer, RabbitMQMessage<U>, Future<Void>> handler
  ) {
    RabbitMQConsumerImpl<U> consumer = new RabbitMQConsumerImpl<>(
            channelBuilder
            , messageCodec
            , queueNameSupplier
            , options
            , handler
    );
    return consumer.start(channelBuilder);        
  }

  public RabbitMQConsumerImpl(RabbitMQChannelBuilder channelBuilder
          , RabbitMQMessageCodec<T> messageCodec
          , Supplier<String> queueNameSupplier
          , RabbitMQConsumerOptions options
          , BiFunction<RabbitMQConsumer, RabbitMQMessage<T>, Future<Void>> handler
  ) {
    this.connection = channelBuilder.getConnection();
    this.consumerOkHandler = options.getConsumerOkHandler();
    this.cancelOkHandler = options.getCancelOkHandler();
    this.cancelHandler = options.getCancelHandler();
    this.shutdownSignalHandler = options.getShutdownSignalHandler();
    this.recoverOkHandler = options.getRecoverOkHandler();
    this.reconnectIntervalMs = options.getReconnectInterval();
    this.autoAck = options.isAutoAck();
    this.exclusive = options.isExclusive();
    this.consumerTag = options.getConsumerTag();
    this.queueNameSupplier = queueNameSupplier;
    this.arguments = options.getArguments().isEmpty() ? Collections.emptyMap() : new HashMap<>(options.getArguments());
    this.messageCodec = messageCodec;
    this.handler = handler;
    this.vertxContext = connection.getVertx().getOrCreateContext();
  }
  
  public Future<RabbitMQConsumer> start(RabbitMQChannelBuilder channelBuilder) {
    return channelBuilder
            .openChannel()
            .compose(chann -> {
              this.channel = chann;
              Promise<Void> promise = Promise.promise();
              return consume(promise);
            })
            .map(this);
  }
  
  @Override
  public RabbitMQChannel getChannel() {
    return channel;
  }

  @Override
  public String getConsumerTag() {
    return consumerTag;
  }

  @Override
  public Future<Void> cancel() {
    this.cancelled = true;
    return channel.basicCancel(consumerTag);
  }
  
  @Override
  public void handleConsumeOk(String consumerTag) {
    this.consumerTag = consumerTag;
    if (consumerOkHandler != null) {
      consumerOkHandler.handle(this);
    }
  }

  @Override
  public void handleCancelOk(String consumerTag) {
    if (cancelOkHandler != null) {
      cancelOkHandler.handle(this);
    }
  }

  @Override
  public void handleCancel(String consumerTag) throws IOException {
    if (cancelHandler != null) {
      cancelHandler.handle(this);
    }
  }

  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
    if ((reconnectIntervalMs > 0) && !cancelled) {
      consume(Promise.promise());
    }
    if (shutdownSignalHandler != null) {
      shutdownSignalHandler.handle(this);
    }
  }

  @Override
  public void handleRecoverOk(String consumerTag) {
    if (recoverOkHandler != null) {
      recoverOkHandler.handle(this);
    }
  }

  @Override
  public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
    CompletableFuture<Void> cf = new CompletableFuture<>();
    
    T value = messageCodec.decodeFromBytes(body);
    RabbitMQMessage<T> msg = new RabbitMQMessageImpl<>(channel, channel.getChannelNumber(), value, consumerTag, envelope, properties);
    
    vertxContext.runOnContext(v -> {
      handler.apply(this, msg)
              .andThen(ar -> cf.complete(null));
    });
    
    try {
      cf.get();
    } catch (Throwable ex) {      
    }
    
  }    
  
  private Future<Void> consume(Promise<Void> promise) {
    String queueName = queueNameSupplier.get();
    channel.basicConsume(queueName, false, channel.getChannelId(), false, exclusive, arguments, this)
            .onSuccess(tag -> promise.complete())
            .onFailure(ex -> {
              if (reconnectIntervalMs > 0 && ! cancelled && ! connection.isClosed()) {
                log.debug("Failed to consume " + queueName + " (" + ex.getClass() + ", \"" + ex.getMessage() + "\"), will try again after " + reconnectIntervalMs + "ms");
                connection.getVertx().setTimer(reconnectIntervalMs, (id) -> {
                  consume(promise);
                });
              } else {
                log.debug("Failed to consume: ", ex);
                promise.fail(ex);
              }
            });
    return promise.future();
  }
}
