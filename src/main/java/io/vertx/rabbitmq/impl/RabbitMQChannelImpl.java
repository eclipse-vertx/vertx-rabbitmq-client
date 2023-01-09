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
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.rabbitmq.AsyncHandler;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import io.vertx.rabbitmq.RabbitMQPublisher;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import io.vertx.rabbitmq.impl.codecs.RabbitMQByteArrayMessageCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author jtalbut
 */
public class RabbitMQChannelImpl implements RabbitMQChannel, ShutdownListener {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQChannelImpl.class);
  
  private final Vertx vertx;
  private final RabbitMQConnectionImpl connection;
  private final Context context;
  private final RabbitMQCodecManager codecManager;
  
  private volatile String channelId;
  
  private final List<Handler<Channel>> channelRecoveryCallbacks = new ArrayList<>();
  private final List<Handler<ShutdownSignalException>> shutdownHandlers = new ArrayList<>();
  private final Object publishLock = new Object();
  private long knownConnectionInstance;
  private final int retries;
  private volatile boolean closed;
  private volatile boolean confirmSelected;
  private final CreateLock<Channel> createLock;
  


  public RabbitMQChannelImpl(Vertx vertx, RabbitMQConnectionImpl connection, RabbitMQOptions options, AsyncHandler channelOpenedHandler) {
    this.vertx = vertx;
    this.connection = connection;
    this.retries = options.getReconnectAttempts();
    this.context = vertx.getOrCreateContext();
    this.codecManager = new RabbitMQCodecManager();    
    this.createLock = new CreateLock<>(
            c -> c.isOpen()
            , channelOpenedHandler == null ? null : c -> channelOpenedHandler.handle(this).map(v -> c)
    );
  }

  public RabbitMQCodecManager getCodecManager() {
    return codecManager;
  }
  
  @Override
  public void addChannelRecoveryCallback(Handler<Channel> channelRecoveryCallback) {
    synchronized(channelRecoveryCallbacks) {
      channelRecoveryCallbacks.add(channelRecoveryCallback);
    }
  }  

  @Override
  public void addChannelShutdownHandler(Handler<ShutdownSignalException> handler) {
    synchronized(shutdownHandlers) {
      shutdownHandlers.add(handler);
    }
  }

  @Override
  public Future<Void> addConfirmHandler(Handler<RabbitMQConfirmation> confirmHandler) {
    return onChannel(channel -> {

      RabbitMQConfirmListenerImpl listener = new RabbitMQConfirmListenerImpl(this, vertx.getOrCreateContext(), confirmHandler);
      channel.addConfirmListener(listener);
      channel.confirmSelect();

      return null;
    });
  }
    
  @Override
  public Future<Void> confirmSelect() {

    confirmSelected = true;
    return onChannel(channel -> {

      channel.confirmSelect();

      return null;
    });
  }

  @Override
  public Future<Void> waitForConfirms(long timeout) {

    return onChannel(channel -> {
      channel.waitForConfirmsOrDie(timeout);
      return null;
    });
  }

  @Override
  public String getChannelId() {
    return channelId;
  }
  
  public Future<RabbitMQChannel> connect() {
    return onChannel(channel -> this);
  }
  
  private void connect(Promise<Channel> promise) {    
    connection.openChannel(this.knownConnectionInstance)
            .onComplete(ar -> {
                    if (ar.failed()) {
                      promise.fail(ar.cause());
                    } else {
                      this.knownConnectionInstance = connection.getConnectionInstance();
                      Channel channel = ar.result();
                      this.channelId = connection.getConnectionName() + ":" + Long.toString(knownConnectionInstance) + ":" + channel.getChannelNumber();
                      channel.addShutdownListener(this);
                      
                      if (channel instanceof Recoverable) {
                        Recoverable recoverable = (Recoverable) channel;
                        recoverable.addRecoveryListener(new RecoveryListener() {
                          @Override
                          public void handleRecovery(Recoverable recoverable) {
                            log.info("Channel " + recoverable + " recovered");
                            List<Handler<Channel>> callbacks;
                            synchronized(channelRecoveryCallbacks) {
                              callbacks = new ArrayList<>(channelRecoveryCallbacks);
                            } 
                            for (Handler<Channel> handler : callbacks) {
                              handler.handle(channel);
                            }
                          }

                          @Override
                          public void handleRecoveryStarted(Recoverable recoverable) {
                          }
                        });
                      }
                      promise.complete(channel);
                    }
            });
  }

  @Override
  public void shutdownCompleted(ShutdownSignalException cause) {
    if (retries > 0) {
      this.knownConnectionInstance = -1;
    }
    log.trace("Channel " + this + " Shutdown: " + cause.getMessage());
    for (Handler<ShutdownSignalException> handler : shutdownHandlers) {
      handler.handle(cause);
    }
  }
    
  @FunctionalInterface
  private interface ChannelHandler<T> {
    T handle(Channel channel) throws Exception;
  }
  
  private <T> Future<T> onChannel(ChannelHandler<T> handler) {
    if (closed) {
      return Future.failedFuture("Channel closed");
    }
    return createLock.create(promise -> {
      connect(promise);
    }, channel -> {
      return context.executeBlocking(promise -> {
        try {
          T t = handler.handle(channel);
          promise.complete(t);
        } catch (Throwable t) {
          promise.fail(t);
        }
      });
    });
  }
  
  @Override
  public Future<Void> abort(int closeCode, String closeMessage) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Future<Void> addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback) {
    
    return onChannel(channel -> {
      return channel.addConfirmListener(ackCallback, nackCallback);
    }).mapEmpty();
    
  }
  
  
  @Override
  public Future<Void> basicAck(String channelId, long deliveryTag, boolean multiple) {
    
    if (channelId == null) {
      return Future.failedFuture(new IllegalArgumentException("channelId may not be null"));
    }
    return onChannel(channel -> {
      if (channelId.equals(this.channelId)) {
        channel.basicAck(deliveryTag, multiple);
      }
      return null;
    });
    
  }

  @Override
  public Future<Void> basicCancel(String consumerTag) {
    
    return onChannel(channel -> {
      channel.basicCancel(consumerTag);
      return null;
    });
    
  }
  
  @Override
  public Future<String> basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer consumer) {
    
    return onChannel(channel -> {
      return channel.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, consumer);
    });
    
  }

  public static AMQP.BasicProperties setTypeAndEncoding(AMQP.BasicProperties props, String type, String encoding) {
    return new AMQP.BasicProperties.Builder()
            .appId(props.getAppId())
            .clusterId(props.getClusterId())
            .contentEncoding(encoding == null ? props.getContentEncoding() : encoding)
            .contentType(type == null ? props.getContentType() : type)
            .correlationId(props.getCorrelationId())
            .deliveryMode(props.getDeliveryMode())
            .expiration(props.getExpiration())
            .headers(props.getHeaders())
            .messageId(props.getMessageId())
            .priority(props.getPriority())
            .replyTo(props.getReplyTo())
            .type(props.getType())
            .timestamp(props.getTimestamp())
            .userId(props.getUserId())
            .build();
  }
  
  @Override
  public Future<Void> basicPublish(RabbitMQPublishOptions options, String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, Object body) {
    /**
     * This is an optimisation that avoids considering basicPublish to be a blocking operation as it translates directly to an NIO call.
     * This is only valid if:
     * 1. The RabbitMQClient is using NIO.
     * 2. Synchronous confirms are not enabled.
     * the first of which is mandated by this client and the second by checking confirmSelected.
     * 
     * Synchronizing is necessary because this introduces a race condition in the generation of the delivery tag.
     */
    String codecName = options == null ? null : options.getCodec();
    RabbitMQMessageCodec codec = codecManager.lookupCodec(body, codecName);
    if ((codec.getContentEncoding() != null && !Objects.equals(codec.getContentEncoding(), props.getContentEncoding()))
            || (codec.getContentType() != null && !Objects.equals(codec.getContentType(), props.getContentType()))) {
      props = setTypeAndEncoding(props, codec.getContentType(), codec.getContentEncoding());
    }
    try {
      Channel channel = createLock.get();
      if (!confirmSelected && channel != null && channel.isOpen()) {
        synchronized(publishLock) {
          if (options != null && options.getDeliveryTagHandler() != null) {
            long deliveryTag = channel.getNextPublishSeqNo();
            options.getDeliveryTagHandler().handle(deliveryTag);
          }
          channel.basicPublish(exchange, routingKey, mandatory, props, codec.encodeToBytes(body));
          return Future.succeededFuture();
        }
      }
    } catch(IOException ex) {
      log.warn("Synchronous send of basicPublish(" + exchange + ", " + routingKey + ", " + mandatory + ": ", ex);
    }
    
    boolean waitForConfirms = options != null && options.isWaitForConfirm();
    if (waitForConfirms) {
      if (!confirmSelected) {
        return Future.failedFuture(new IllegalStateException("Must call confirmSelect before basicPublishWithConfirm"));
      }
    }    
      
    AMQP.BasicProperties finalProps = props;
    return onChannel(channel -> {
      synchronized(publishLock) {
        if (options != null && options.getDeliveryTagHandler() != null) {
          long deliveryTag = channel.getNextPublishSeqNo();
          options.getDeliveryTagHandler().handle(deliveryTag);
        }
        channel.basicPublish(exchange, routingKey, mandatory, finalProps, codec.encodeToBytes(body));
        if (waitForConfirms) {
          channel.waitForConfirms();
        }
      }
      return null;
    }).mapEmpty();
    
  }

  @Override
  public Future<Void> basicQos(int prefetchSize, int prefetchCount, boolean global) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Future<Void> exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, Map<String, Object> arguments) {
    
    return onChannel(channel -> {
      return channel.exchangeDeclare(exchange, type, durable, autoDelete, arguments);
    }).mapEmpty();
    
  }

  @Override
  public Future<Void> exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Future<String> queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
    
    return onChannel(channel -> {
      return channel.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
    }).map(dok -> dok.getQueue());
    
  }

  @Override
  public Future<Void> queueDeclarePassive(String queue) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Future<Void> queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) {
    
    return onChannel(channel -> {
      return channel.queueBind(queue, exchange, routingKey, arguments);
    }).mapEmpty();
    
  }

  @Override
  public Future<Void> close() {
    return close(AMQP.REPLY_SUCCESS, "OK");
  }
    
  @Override
  public Future<Void> close(int closeCode, String closeMessage) {
    Channel chann = createLock.get();
    closed = true;
    if (chann == null || !chann.isOpen()) {
      return Future.succeededFuture();
    }
    return vertx.executeBlocking(promise -> {
      try {
        chann.close(closeCode, closeMessage);
        promise.complete();
      } catch (Throwable t) {
        promise.fail(t);
      }
    });
  }

  @Override
  public <T> RabbitMQChannel registerCodec(RabbitMQMessageCodec<T> codec) {
    codecManager.registerCodec(codec);
    return this;
  }

  @Override
  public <T> RabbitMQChannel unregisterCodec(String name) {
    codecManager.unregisterCodec(name);
    return this;
  }

  @Override
  public <T> RabbitMQChannel registerDefaultCodec(Class<T> clazz, RabbitMQMessageCodec<T> codec) {
    codecManager.registerDefaultCodec(clazz, codec);
    return this;
  }

  @Override
  public <T> RabbitMQChannel unregisterDefaultCodec(Class<T> clazz) {
    codecManager.unregisterDefaultCodec(clazz);
    return this;
  }
  
  
  
  
}
