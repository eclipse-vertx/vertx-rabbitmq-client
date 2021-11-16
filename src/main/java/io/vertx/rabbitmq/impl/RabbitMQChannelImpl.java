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
import io.vertx.core.streams.ReadStream;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQFuturePublisher;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.rabbitmq.RabbitMQRepublishingPublisher;

/**
 *
 * @author jtalbut
 */
public class RabbitMQChannelImpl implements RabbitMQChannel, ShutdownListener {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQChannelImpl.class);
  
  private final Vertx vertx;
  private final RabbitMQConnectionImpl connection;
  private final Context context;
  
  private volatile String channelId;
  
  private final List<Handler<RabbitMQChannel>> channelRecoveryCallbacks = new ArrayList<>();
  private final List<Handler<ShutdownSignalException>> shutdownHandlers = new ArrayList<>();
  private final Object publishLock = new Object();
  private long knownConnectionInstance;
  private final int retries;
  private volatile boolean closed;
  private volatile boolean confirmSelected;
  private final CreateLock<Channel> createLock = new CreateLock<>(c -> c.isOpen());


  public RabbitMQChannelImpl(Vertx vertx, RabbitMQConnectionImpl connection, RabbitMQOptions options) {
    this.vertx = vertx;
    this.connection = connection;
    this.retries = options.getReconnectAttempts();
    this.context = vertx.getOrCreateContext();
  }
  
  @Override
  public void addChannelEstablishedCallback(Handler<Promise<Void>> channelEstablishedCallback) {
    createLock.addPostCreateHandler(channelEstablishedCallback);
  }
  
  @Override
  public void addChannelRecoveryCallback(Handler<RabbitMQChannel> channelRecoveryCallback) {
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
  public RabbitMQRepublishingPublisher createPublisher(String exchange, RabbitMQPublisherOptions options) {
    return new RabbitMQPublisherImpl(vertx, this, exchange, options, retries > 0);
  }

  @Override
  public RabbitMQFuturePublisher createFuturePublisher(String exchange, RabbitMQPublisherOptions options) {
    return new RabbitMQFuturePublisherImpl(vertx, this, exchange, options);
  }
  
  @Override
  public RabbitMQConsumer createConsumer(String queue, RabbitMQConsumerOptions options) {
    RabbitMQConsumerImpl consumer = new RabbitMQConsumerImpl(vertx, vertx.getOrCreateContext(), this, queue, options);    
    return consumer;
  }

  @Override
  public Future<ReadStream<RabbitMQConfirmation>> addConfirmListener(int maxQueueSize) {

    return onChannel(channel -> {

      RabbitMQConfirmListenerImpl listener = new RabbitMQConfirmListenerImpl(this, vertx.getOrCreateContext(), maxQueueSize);
      channel.addConfirmListener(listener);
      channel.confirmSelect();

      return listener;
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
  
  @Override
  public Future<Void> connect() {
    return onChannel(channel -> null);
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
                        RabbitMQChannel outerChannel = this;
                        recoverable.addRecoveryListener(new RecoveryListener() {
                          @Override
                          public void handleRecovery(Recoverable recoverable) {
                            log.info("Channel {} recovered", recoverable);
                            List<Handler<RabbitMQChannel>> callbacks;
                            synchronized(channelRecoveryCallbacks) {
                              callbacks = new ArrayList<>(channelRecoveryCallbacks);
                            } 
                            for (Handler<RabbitMQChannel> handler : callbacks) {
                              handler.handle(outerChannel);
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
    log.info("Channel {} Shutdown: {}", this, cause.getMessage());
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
      return context.executeBlocking(future -> {
        try {
          T t = handler.handle(channel);
          future.complete(t);
        } catch (Throwable t) {
          future.fail(t);
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

  @Override
  public Future<Void> basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body) {
    return basicPublish(exchange, routingKey, mandatory, props, body, null);
  }
    
  @Override
  public Future<Void> basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body, Handler<Long> deliveryTagHandler) {
    /**
     * This is an optimisation that avoids considering basicPublish to be a blocking operation as it translates directly to an NIO call.
     * This is only valid if:
     * 1. The RabbitMQClient is using NIO.
     * 2. Synchronous confirms are not enabled.
     * the first of which is mandated by this client and the second by checking confirmSelected.
     * 
     * Synchronizing is necessary because this introduces a race condition in the generation of the delivery tag.
     */
    try {
      Channel channel = createLock.get();
      if (!confirmSelected && channel != null && channel.isOpen()) {
        synchronized(publishLock) {
          if (deliveryTagHandler != null) {
            long deliveryTag = channel.getNextPublishSeqNo();
            deliveryTagHandler.handle(deliveryTag);
          }
          channel.basicPublish(exchange, routingKey, mandatory, props, body);
          return Future.succeededFuture();
        }
      }
    } catch(IOException ex) {
      log.warn("Synchronous send of basicPublish({}, {}, {}, ...) failed: ", exchange, routingKey, mandatory, ex);
    }
    
    return onChannel(channel -> {
      synchronized(publishLock) {
        if (deliveryTagHandler != null) {
          long deliveryTag = channel.getNextPublishSeqNo();
          deliveryTagHandler.handle(deliveryTag);
        }
      }
      channel.basicPublish(exchange, routingKey, mandatory, props, body);
      return null;
    }).mapEmpty();
    
  }

  @Override
  public Future<Void> basicPublishWithConfirm(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body, Handler<Long> deliveryTagHandler) {

    if (!confirmSelected) {
      return Future.failedFuture(new IllegalStateException("Must call confirmSelect before basicPublishWithConfirm"));
    }
    return onChannel(channel -> {
      synchronized(publishLock) {
        if (deliveryTagHandler != null) {
          long deliveryTag = channel.getNextPublishSeqNo();
          deliveryTagHandler.handle(deliveryTag);
        }
      }
      channel.basicPublish(exchange, routingKey, mandatory, props, body);
      channel.waitForConfirms();
      return null;
    }).mapEmpty();
    
  }

  @Override
  public Future<Void> basicPublishWithConfirm(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body) {
    return basicPublishWithConfirm(exchange, routingKey, mandatory, props, body, null);
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
  public Future<Void> queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
    
    return onChannel(channel -> {
      return channel.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
    }).mapEmpty();
    
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
  
  
}
