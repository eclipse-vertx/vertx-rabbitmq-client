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
package io.vertx.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.impl.RabbitMQChannelImpl;
import io.vertx.rabbitmq.impl.RabbitMQCodecManager;
import io.vertx.rabbitmq.impl.RabbitMQConfirmListenerImpl;
import io.vertx.rabbitmq.impl.RabbitMQConnectionImpl;
import io.vertx.rabbitmq.impl.RabbitMQConsumerImpl;
import io.vertx.rabbitmq.impl.RabbitMQPublisherImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 *
 * @author jtalbut
 */
public class RabbitMQChannelBuilder {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQChannelBuilder.class);

  public static final RabbitMQMessageCodec<byte[]> BYTE_ARRAY_MESSAGE_CODEC = RabbitMQCodecManager.BYTE_ARRAY_MESSAGE_CODEC;
  public static final RabbitMQMessageCodec<Buffer> BUFFER_MESSAGE_CODEC = RabbitMQCodecManager.BUFFER_MESSAGE_CODEC;
  public static final RabbitMQMessageCodec<Object> NULL_MESSAGE_CODEC = RabbitMQCodecManager.NULL_MESSAGE_CODEC;
  public static final RabbitMQMessageCodec<String> STRING_MESSAGE_CODEC = RabbitMQCodecManager.STRING_MESSAGE_CODEC;
  public static final RabbitMQMessageCodec<JsonObject> JSON_OBJECT_MESSAGE_CODEC = RabbitMQCodecManager.JSON_OBJECT_MESSAGE_CODEC;
  public static final RabbitMQMessageCodec<JsonArray> JSON_ARRAY_MESSAGE_CODEC = RabbitMQCodecManager.JSON_ARRAY_MESSAGE_CODEC;

  private final RabbitMQConnectionImpl connection;
  private final List<ChannelHandler> channelOpenHandlers = new ArrayList<>();
  private final RabbitMQCodecManager codecManager = new RabbitMQCodecManager();
  private final List<Handler<Channel>> channelRecoveryCallbacks = new ArrayList<>();
  private final List<Handler<ShutdownSignalException>> shutdownHandlers = new ArrayList<>();


  /**
   * Constructor.
   *
   * @param connection The connection to open the channel on.
   */
  public RabbitMQChannelBuilder(RabbitMQConnectionImpl connection) {
    this.connection = connection;
  }

  /**
   * Add a ChannelOpenHandler to the builder.
   * <p>
   * The ChannelOpenHandler will be called, on a blocking thread, each time the channel is established.
   * <p>
   * If the built in reconnect mechanism is disabled (by setting {@link RabbitMQOptions#setReconnectAttempts(int)} to zero)
   * then the ChannelOpenHandler will be called just once in the lifetime of a RabbitMQChannel.
   * If the reconnect mechanism is enabled the ChannelOpenHandler will be called each time the channel connects to the broker.
   * <p>
   * The ChannelOpenHandler will always be called on a blocking thread and the Channel that is passed in will be open and ready
   * for use.
   * <p>
   * If there has been a network failure that causes the requests on the Channel to fail the RabbitMQChannel will detect this
   * and (if reconnects are enabled) will reconnect and call the ChannelOpenHandler again.
   * It is not necessary for the ChannelOpenHandler to take specific error recovery steps in this case.
   * <p>
   * If the ChannelOpenHandler fails for some other reason it will block the use of the channel.
   * Two tests are required:
   * <ul>
   * <li>Calling abort in the middle of a ChannelOpenHandler should attempt reconnect.
   * <li>Raising some other exception in the middle of the ChannelOpenHandler should have a way to communicate that.
   * </ul>
   * <p>
   * A RabbitMQChannel can only have one ChannelOpenHandler.
   * <p>
   * @param channelOpenHandler The handler to be called (on a blocking thread) each time the channel is opened.
   * @return       this, so that the builder may be used fluently.
   */
  public RabbitMQChannelBuilder withChannelOpenHandler(ChannelHandler channelOpenHandler) {
    this.channelOpenHandlers.add(channelOpenHandler);
    return this;
  }

  /**
   * Register a {@link RabbitMQMessageCodec}.
   * <p>
   * The codec registered will be used when explicitly referenced by name.
   * <p>
   * @param <T>    The type of object processed by the codec.
   * @param codec  The codec
   * @return       this, so that the builder may be used fluently.
   */
  public <T> RabbitMQChannelBuilder withNamedCodec(RabbitMQMessageCodec<T> codec) {
    codecManager.registerCodec(codec);
    return this;
  }

  /**
   * Register a {@link RabbitMQMessageCodec}.
   * <p>
   * The codec registered will be used when publishing a message of the given class (if no other codec is specified).
   * <p>
   * Default codecs cannot be used for consumers.
   * <p>
   * @param <T>    The type of object processed by the codec.
   * @param clazz  The class that the codec processes.
   * @param codec  The codec
   * @return       this, so that the builder may be used fluently.
   */
  public <T> RabbitMQChannelBuilder withTypedCodec(Class<T> clazz, RabbitMQMessageCodec<T> codec) {
    codecManager.registerDefaultCodec(clazz, codec);
    return this;
  }

  /**
   * Add a callback that will be called whenever the channel completes its own internal recovery process.
   * This callback must be idempotent - it will be called each time a connection is established, which may be multiple times against the same instance.
   * Callbacks will be added to a list and called in the order they were added, the only way to remove callbacks is to create a new channel.
   *
   * This callback is only useful if RabbitMQOptions.automaticRecoveryEnabled is true.
   *
   * Callbacks can be used for any kind of resetting that clients need to perform after the automatic recovery is complete.
   * Typically this callback is not required because the recovery mechanism (as opposed to the reconnect mechanism) automatically
   * tracks objects created on the channel.
   *
   * Callbacks will be called on a RabbitMQ thread, after topology recovery, and will block the completion of the recovery.
   * These callbacks have no interaction with Vertx and expose the raw channel.
   *
   * @param channelRecoveryCallback
   * @return       this, so that the builder may be used fluently.
   */
  public RabbitMQChannelBuilder withChannelRecoveryCallback(Handler<Channel> channelRecoveryCallback) {
    channelRecoveryCallbacks.add(channelRecoveryCallback);
    return this;
  }

  /**
   * Add a callback that will be called whenever the channel shuts down.
   *
   * Callbacks will be called on a RabbitMQ thread, and will block the completion of the shutdown (though the network connection is not usable at this time).
   *
   * @param handler
   * @return       this, so that the builder may be used fluently.
   */
  public RabbitMQChannelBuilder withChannelShutdownHandler(Handler<ShutdownSignalException> handler) {
    shutdownHandlers.add(handler);
    return this;
  }

  /**
   * Add a callback that will be called when confirmation messages are received for published messages.
   *
   * The confirm handler will be called on a vertx thread (in the context of the publisher).
   *
   * This method works by adding a channelOpenHandler that ensures that the confirm handler is added to the channel
   * in the event of a reconnection.
   *
   * The openChannelHandlers are called in the order in which they are registered.
   *
   * @param handler The confirmation handler.
   * @return       this, so that the builder may be used fluently.
   * @see <a href="https://www.rabbitmq.com/confirms.html">Consumer Acknowledgements and Publisher Confirms</a>
   */
  public RabbitMQChannelBuilder withConfirmHandler(Handler<RabbitMQConfirmation> handler) {
    channelOpenHandlers.add(chann -> {
      RabbitMQConfirmListenerImpl listener = new RabbitMQConfirmListenerImpl(chann.getChannelNumber(), connection.getVertx().getOrCreateContext(), handler);
      chann.addConfirmListener(listener);
      chann.confirmSelect();
    });
    return this;
  }

/*
  public RabbitMQChannelBuilder withReturnedMessageHandler(Handle<Return> returnHandler) {
    channelOpenHandlers.add(chann -> {
      chann.addReturnListener(ret -> returnHandler.recycle(ret));
    });
    return this;
  }
*/

  /**
   * Set the QOS criteria to use on the channel.
   *
   * The default values are both 0, which implies unlimited.
   *
   * This method works by adding a channelOpenHandler that ensures that the QOS settings are added to the channel
   * in the event of a reconnection.
   *
   * It is usually a good idea to set a prefetch count to prevent a single consumer attempting to download all available messages
   * on a queue.
   * Be aware that if the client clients fails to ack (or nack) messages that have been fetched it will deadlock once it has fetched
   * its limit.
   *
   * @param prefetchSize The maximum size, in octets, of the messages that may be prefetched by the client.
   * Must not be negative.
   * @param prefetchCount The maximum number of message that may be prefetched by the client.
   * Must not be negative, and must be less that 65536.
   * @return       this, so that the builder may be used fluently.
   * @see <a href="https://www.rabbitmq.com/confirms.html">Consumer Acknowledgements and Publisher Confirms</a>
   */
  public RabbitMQChannelBuilder withQos(int prefetchSize, int prefetchCount) {
    if (prefetchCount < 0 || prefetchCount > 65535) {
      throw new IllegalArgumentException("Invalid prefetchCount");
    }
    if (prefetchSize < 0) {
      throw new IllegalArgumentException("Invalid prefetchSize");
    }
    channelOpenHandlers.add(chann -> {
      chann.basicQos(prefetchSize, prefetchCount, true);
    });
    return this;
  }

  /**
   * Open a new channel from this builder.
   *
   * @return  A Future that will be completed with the channel when it is open.
   */
  public Future<RabbitMQChannel> openChannel() {
    RabbitMQChannelImpl channel = new RabbitMQChannelImpl(this);
    return channel.connect();
  }

   /**
   * Creates a RabbitMQPublisher (using a new channel on this connection) that reliably sends messages.
   * @param <T> The type of data that will be passed in to the Publisher.
   @ @param codec The default {@link RabbitMQMessageCodec} to use for all published messages.
   * Set to null to use codecs registered on the channel builder.
   * Set to an instance of the RabbitMQByteArrayMessageCodec to avoid doing any mapping of published messages.
   * @param exchange The exchange that messages are to be sent to.
   * @param options Options for configuring the publisher.
   * @return a RabbitMQPublisher on this channel that reliably sends messages.
   */
  public <T> Future<RabbitMQPublisher<T>> createPublisher(String exchange, @Nullable RabbitMQMessageCodec<T> codec, RabbitMQPublisherOptions options) {
    return RabbitMQPublisherImpl.create(this, codec, exchange, options);
  }

  /**
   * Create a RabbitMQConsumer (using a new channel on this connection) that reliably receives messages.
   * @param queue The queue that messages are being pushed from.
   * @param options Options for configuring the consumer.
   * @return a RabbitMQConsumer on this channel that can reliably receives messages.
   * After being constructed and configured the RabbitMQConsumer should be passed to the basicConsume method.
   */
//  public Future<RabbitMQConsumer> createConsumer(String queue, RabbitMQConsumerOptions options) {
//    return RabbitMQStreamConsumer1Impl.create(this, RabbitMQCodecManager.BYTE_ARRAY_MESSAGE_CODEC, null, options);
//  }

  /**
   * Create a RabbitMQConsumer (using a new channel on this connection) that reliably receives messages.
   * @param <T> The type of data that will be received by the Consumer.
   * @param codec The codec that will be used to decode the messages received by the Consumer.
   * @param queue The queue that messages are being pushed from.
   * @param queueNameSuppler Functional supplier of the queue name, if this is non-null the queue parameter will be ignored.
   * @param options Options for configuring the consumer.
   * @param handler The handler that will be called for each message received.
   * @return a RabbitMQConsumer on this channel that can reliably receives messages.
   * After being constructed and configured the RabbitMQConsumer should be passed to the basicConsume method.
   */
  public <T> Future<RabbitMQConsumer> createConsumer(
            RabbitMQMessageCodec<T> codec
          , String queue
          , Supplier<String> queueNameSuppler
          , RabbitMQConsumerOptions options
          , BiFunction<RabbitMQConsumer, RabbitMQMessage<T>, Future<Void>> handler
  ) {
    if (queueNameSuppler == null) {
      queueNameSuppler = () -> queue;
    }
    return RabbitMQConsumerImpl.create(this, codec, queueNameSuppler, options, handler);
  }

  public RabbitMQConnectionImpl getConnection() {
    return connection;
  }

  public RabbitMQCodecManager getCodecManager() {
    return codecManager;
  }

  public List<Handler<Channel>> getChannelRecoveryCallbacks() {
    return channelRecoveryCallbacks;
  }

  public List<Handler<ShutdownSignalException>> getShutdownHandlers() {
    return shutdownHandlers;
  }

  public ChannelFunction<Void> getChannelOpenHandler() {
    return chan -> {
      for (ChannelHandler handler : channelOpenHandlers) {
        try {
          handler.handle(chan);
        } catch (Throwable ex) {
          log.error("ChannelOpenHAndler failed: ", ex);
        }
      }
      return null;
    };
  }


}
