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

import com.rabbitmq.client.AMQP.BasicProperties;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.InboundBuffer;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQConfirmation;
import io.vertx.rabbitmq.RabbitMQPublisherConfirmation;
import io.vertx.rabbitmq.RabbitMQPublisherOptions;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.rabbitmq.RabbitMQRepublishingPublisher;


/**
 *
 * @author jtalbut
 */
public class RabbitMQPublisherImpl implements RabbitMQRepublishingPublisher, ReadStream<RabbitMQPublisherConfirmation> {
  
  private static final Logger log = LoggerFactory.getLogger(RabbitMQPublisherImpl.class);
  
  private final Vertx vertx;
  private final RabbitMQChannel channel;
  private final String exchange;
  private final InboundBuffer<RabbitMQPublisherConfirmation> confirmations;
  private final Context context;
  private final RabbitMQPublisherOptions options;

  private final Deque<MessageDetails> pendingAcks = new ArrayDeque<>();
  private final InboundBuffer<MessageDetails> sendQueue;
  private String lastChannelId = null;
  private volatile boolean stopped = false;
  private final boolean shouldReconnect;

  /**
   * POD for holding message details pending acknowledgement.
   * @param <I> The type of the message IDs.
   */
  static class MessageDetails {

    private final String exchange;
    private final String routingKey;
    private final BasicProperties properties;
    private final byte[] message;
    private final Handler<AsyncResult<Void>> publishHandler;
    private String channelId;
    private long deliveryTag;

    MessageDetails(String exchange, String routingKey, BasicProperties properties, byte[] message, Handler<AsyncResult<Void>> publishHandler) {
      this.exchange = exchange;
      this.routingKey = routingKey;
      this.properties = properties;
      this.message = message;
      this.publishHandler = publishHandler;
    }

    public void setDeliveryTag(String channelId, long deliveryTag) {
      this.channelId = channelId;
      this.deliveryTag = deliveryTag;
    }

  }
  
  public RabbitMQPublisherImpl(Vertx vertx, RabbitMQChannel channel, String exchange, RabbitMQPublisherOptions options, boolean shouldReconnect) {
    this.vertx = vertx;
    this.channel = channel;
    this.exchange = exchange;
    this.context = vertx.getOrCreateContext();
    this.confirmations = new InboundBuffer<>(context);
    this.sendQueue = new InboundBuffer<>(context);
    sendQueue.handler(md -> handleMessageSend(md));
    this.options = options;
    this.shouldReconnect = shouldReconnect;
    this.channel.addChannelEstablishedCallback(p -> {
      addConfirmListener()
              .onComplete(ar -> {
                if (ar.succeeded()) {
                  if (lastChannelId == null) {
                    lastChannelId = channel.getChannelId();
                  } else if (!lastChannelId.equals(channel.getChannelId())) {
                    sendQueue.pause();
                    log.info("Moving {} from pending to send queue", pendingAcks.stream().map(md -> new String(md.message)).collect(Collectors.toList()));
                    sendQueue.write(pendingAcks);
                    pendingAcks.clear();
                    sendQueue.resume();
                    lastChannelId = channel.getChannelId();
                  }
                  p.complete();
                } else {
                  p.fail(ar.cause());
                }
      });
    });
    this.channel.addChannelRecoveryCallback(p -> {
      context.runOnContext(v -> {
        sendQueue.pause();
        log.info("Moving {} from pending to send queue", pendingAcks.stream().map(md -> new String(md.message)).collect(Collectors.toList()));
        sendQueue.write(pendingAcks);
        pendingAcks.clear();
        sendQueue.resume();
      });
    });
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> resultHandler) {
    stopped = true;
    sendQueue.pause();
    if (sendQueue.isEmpty()) {
      resultHandler.handle(Future.succeededFuture());
    } else {
      sendQueue.emptyHandler(v -> {
        resultHandler.handle(Future.succeededFuture());
      });
    }
    sendQueue.resume();
  }

  @Override
  public Future<Void> stop() {
    Promise<Void> promise = Promise.promise();
    stop(promise);
    return promise.future();
  }

  @Override
  public void close() throws Exception {
    stop().onFailure(ex -> {
      log.error("Failed to stop correctly on close: ", ex);
    });
  }
  
  @Override
  public void restart() {
    stopped = false;
    sendQueue.pause();
    sendQueue.emptyHandler(null);
    sendQueue.resume();
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

  @Override
  public ReadStream<RabbitMQPublisherConfirmation> getConfirmationStream() {
    return this;
  }

  @Override
  public int queueSize() {
    return sendQueue.size();
  }

  private void handleMessageSend(MessageDetails md) {
    sendQueue.pause();
    synchronized(pendingAcks) {
      pendingAcks.add(md);
    }
    doSend(md);
  }

  private boolean wasIoRelated(Throwable ex) {
    while(ex != null) {
      if (ex instanceof IOException) {
        return true;
      }
      ex = ex.getCause();
    }
    return false;
  }
  
  private void doSend(MessageDetails md) {
    try {
      channel.basicPublish(md.exchange, md.routingKey, false, md.properties, md.message, dt -> { md.setDeliveryTag(lastChannelId, dt); })
              .onComplete(ar -> {
                sendQueue.resume();
                try {
                  if (ar.succeeded()) {
                    if (md.publishHandler != null) {
                      try {
                        md.publishHandler.handle(ar);
                      } catch(Throwable ex) {
                        log.warn("Failed to handle publish result", ex);
                      }
                    }
                    sendQueue.resume();
                  } else {
                    if (wasIoRelated(ar.cause()) && shouldReconnect) {
                      channel.connect()
                              .onSuccess(v -> doSend(md))
                              ;
                    } else {
                      doSend(md);
                    }
                  }
                } finally {
                }
              });
    } catch(Throwable ex) {
      if (wasIoRelated(ex)&& shouldReconnect) {
        channel.connect()
                .onSuccess(v -> doSend(md))
                ;
      } else {
        vertx.setTimer(options.getReconnectAttempts(), l -> handleMessageSend(md));
      }
    }
  }

  private void handleConfirmation(RabbitMQConfirmation rawConfirmation) {
    synchronized(pendingAcks) {
      if (rawConfirmation.isMultiple()) {
        for (Iterator<MessageDetails> iter = pendingAcks.iterator(); iter.hasNext(); ) {
          MessageDetails md = iter.next();
          if (md.deliveryTag <= rawConfirmation.getDeliveryTag()) {
            String messageId = md.properties == null ?  null : md.properties.getMessageId();
            confirmations.write(new RabbitMQPublisherConfirmation(messageId, rawConfirmation.isSucceeded()));
            iter.remove();
          } else {
            break ;
          }
        }
      } else {
        for (Iterator<MessageDetails> iter = pendingAcks.iterator(); iter.hasNext(); ) {
          MessageDetails md = iter.next();
          if (md.deliveryTag == rawConfirmation.getDeliveryTag()) {
            String messageId = md.properties == null ?  null : md.properties.getMessageId();
            confirmations.write(new RabbitMQPublisherConfirmation(messageId, rawConfirmation.isSucceeded()));
            iter.remove();
            break ;
          }
        }
      }
    }
  }

  @Override
  public void publish(String routingKey, BasicProperties properties, byte[] body) {
    if (!stopped) {
      context.runOnContext(e -> {
        sendQueue.write(new MessageDetails(exchange, routingKey, properties, body, null));
      });
    }
  }

  @Override
  public void publish(String routingKey, BasicProperties properties, Buffer body) {
    publish(routingKey, properties, body.getBytes());
  }
  
  @Override
  public RabbitMQPublisherImpl exceptionHandler(Handler<Throwable> hndlr) {
    confirmations.exceptionHandler(hndlr);
    return this;
  }

  @Override
  public RabbitMQPublisherImpl handler(Handler<RabbitMQPublisherConfirmation> hndlr) {
    confirmations.handler(hndlr);
    return this;
  }

  @Override
  public RabbitMQPublisherImpl pause() {
    confirmations.pause();
    return this;
  }

  @Override
  public RabbitMQPublisherImpl resume() {
    confirmations.resume();
    return this;
  }

  @Override
  public RabbitMQPublisherImpl fetch(long l) {
    confirmations.fetch(l);
    return this;
  }

  @Override
  public RabbitMQPublisherImpl endHandler(Handler<Void> hndlr) {
    return this;
  }

}
