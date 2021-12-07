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
package io.vertx.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.Map;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQChannel {
  
  /**
   * Establish a Channel to the RabbitMQ server.
   * All operations on the channel are lazy and will establish the connection/channel only when needed.
   * This operation exists to permit clients to establish a connection/channel as part of their initialization.
   * 
   * Note that, as with all networking operations, it is entirely possible for this method to succeed and then the connection to become disconnected before the next call.
   * 
   * @return A Future that will be complete when the channel is established.
   */
  Future<Void> connect();
  
  /**
   * Set a callback to be called whenever this channel is established.
   * This callback must be idempotent - it will be called each time a connection is established, which may be multiple times against the same instance.
   * Callbacks will be added to a list and called in the order they were added, the only way to remove callbacks is to create a new channel.
   *
   * These callbacks should be used to establish any Rabbit MQ server objects that are required - exchanges, queues, bindings, etc.
   * Each callback will receive a Promise<Void> that it must complete in order to pass control to the next callback (or back to the RabbitMQClient).
   * If the callback fails the promise the RabbitMQClient will be unable to make a connection (it will attempt to connect again according to its retry configuration).
   * If the promise is not completed or failed by a callback the RabbitMQClient will not start (it will hang indefinitely).
   *
   * Other methods on the client may be used in the callback -
   * it is specifically expected that RabbitMQ objects will be declared, but the publish and consume methods must not be used.
   *
   * The connection established callbacks are particularly important with the RabbitMQPublisher and RabbitMQConsumer when they are used with
   * servers that may failover to another instance of the server that does not have the same exchanges/queues configured on it.
   * In this situation these callbacks are the only opportunity to create exchanges, queues and bindings before the client will attempt to use them when it
   * re-establishes connection.
   * If your failover cluster is guaranteed to have the appropriate objects already configured then it is not necessary to use the callbacks (though should be harmless to do so).
   *
   * @param channelEstablishedCallback  callback to be called whenever a new channel is established.
   */
  @GenIgnore
  void addChannelEstablishedCallback(Handler<Promise<Void>> channelEstablishedCallback);
  
  /**
   * Add a callback that will be called whenever the channel completes its own internal recovery process.
   * This callback must be idempotent - it will be called each time a connection is established, which may be multiple times against the same instance.
   * Callbacks will be added to a list and called in the order they were added, the only way to remove callbacks is to create a new channel.
   * 
   * This callback is only useful if RabbitMQOptions.automaticRecoveryEnabled is true.
   * 
   * Callbacks can be used for any kind of resetting that clients need to perform after the automatic recovery is complete.
   * 
   * Callbacks will be called on a RabbitMQ thread, after topology recovery, and will block the completion of the recovery.
   * Because these callbacks have no interaction with Vertx this is one of the few methods that exposes the raw channel.
   * 
   * @param channelRecoveryCallback 
   */
  void addChannelRecoveryCallback(Handler<Channel> channelRecoveryCallback);
  
  void addChannelShutdownHandler(Handler<ShutdownSignalException> handler);
  
  /**
   * Creates a RabbitMQPublisher on this channel that reliably sends messages.
   * @param exchange The exchange that messages are to be sent to.
   * @param options Options for configuring the publisher.
   * @return a RabbitMQPublisher on this channel that reliably sends messages.
   */
  RabbitMQPublisher<Object> createPublisher(String exchange, RabbitMQPublisherOptions options);
      
  /**
   * Creates a RabbitMQPublisher on this channel that reliably sends messages.
   * @param <T> The type of data that will be passed in to the Publisher.
   * @param codec The codec that will be used to encode the messages passed in to the Publisher.
   * @param exchange The exchange that messages are to be sent to.
   * @param options Options for configuring the publisher.
   * @return a RabbitMQPublisher on this channel that reliably sends messages.
   */
  <T> RabbitMQPublisher<T> createPublisher(RabbitMQMessageCodec<T> codec, String exchange, RabbitMQPublisherOptions options);

  /**
   * Create a RabbitMQConsumer on this channel that reliably receives messages.
   * @param queue The queue that messages are being pushed from.
   * @param options Options for configuring the consumer.
   * @return a RabbitMQConsumer on this channel that can reliably receives messages.
   * After being constructed and configured the RabbitMQConsumer should be passed to the basicConsume method.
   */
  RabbitMQConsumer createConsumer(String queue, RabbitMQConsumerOptions options);
  
  Future<Void> addConfirmHandler(Handler<RabbitMQConfirmation> confirmListener);
  
  /**
   * Returns an identifier of the underlying channel.
   * This is made up of the connection instance and the channel number.
   * @return an identifier of the underlying channel.
   */
  String getChannelId();
  
  Future<Void> abort(int closeCode, String closeMessage);
  
  Future<Void> addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback);
  
  Future<Void> basicAck(String channelId, long deliveryTag, boolean multiple);
  
  Future<Void> basicCancel(String consumerTag);
  
    /**
     * Start a consumer. 
     * 
     * @param queue the name of the queue
     * @param autoAck true if the server should consider messages acknowledged once delivered; false if the server should expect explicit acknowledgements.
     * Auto acknowledgements should only be used when "at-most-once" semantics are required, it cannot guarantee delivery.
     * @param consumerTag a client-generated consumer tag to establish context
     * It is strongly recommended that this be set to a globally unique value as it is needed for tracking of acks.
     * Setting it to a user-understandable value will help when looking at queue consumers in the RabbitMQ management UI.
     * @param noLocal True if the server should not deliver to this consumer
     * messages published on this channel's connection. Note that the RabbitMQ server does not support this flag.
     * Set to false, unless you are using something other than RabbitMQ.
     * @param exclusive true if this is an exclusive consumer.
     * See <a href="https://www.rabbitmq.com/consumers.html#exclusivity">https://www.rabbitmq.com/consumers.html#exclusivity</a>.
     * It is recommended that this be set to false
     * , be sure you understand the implications and have read 
     * <a href="https://www.rabbitmq.com/consumers.html#single-active-consumer">https://www.rabbitmq.com/consumers.html#single-active-consumer</a> before setting to true.
     * @param arguments a set of arguments for the consume
     * Set to null unless there is a good reason not to.
     * @param consumer an interface to the consumer object
     * @return A Future containing either the consumerTag associated with the new consumer or a failure.
     * @see com.rabbitmq.client.Channel.basicConsume
     * @see com.rabbitmq.client.AMQP.Basic.Consume
     * @see com.rabbitmq.client.AMQP.Basic.ConsumeOk
     */
  Future<String> basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer consumer);
  
  Future<Void> basicQos(int prefetchSize, int prefetchCount, boolean global);
  
  Future<Void> exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, Map<String,Object> arguments);
  
  Future<Void> exchangeBind(String destination, String source, String routingKey, Map<String,Object> arguments);
  
  Future<Void> queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String,Object> arguments);
  
  Future<Void> queueDeclarePassive(String queue);
          
  Future<Void> queueBind(String queue, String exchange, String routingKey, Map<String,Object> arguments);
  
    /**
     * Publish a message.
     *
     * The Future returned from this method will be completed when a message has been sent.
     * If options.waitForConfirm is set to true the Future will only be completed after the broker confirms that it has received the message for processing.
     * This will block all other calls to basicPublish on this channel.
     * If options.waitForConfirm is set to false the Future will be completed when the message has been sent, which does not necessarily mean that it has even reached the broker.
     * Clients should choose one of:
     * <ul>
     * <li>Accept that messages may be lost.
     * <li>Set waitForConfirm to true and accept a hit to the performance of all publishing on this channel.
     * <li>Use asynchronous confirmations (via addConfirmListener).
     * <li>Use the RabbitMQPublisher that provides an implementation of an asynchronous confirmation handler.
     * </ul>
     * 
     * The message body type must be one of:
     * <ul>
     * <li>byte[] 
     *   The most efficient option, any other type must be converted to this.
     * <li>Buffer 
     *   The data will be extracted using getBytes(). 
     *   It is not possible to specify a subset of bytes with this method, use Buffer.slice if a subset is required.
     * <li>Any other type for which a MessageCodec has been registered.
     *   The MessageCodec can either be identified by name (options.setCodec) or registered as a defaultCodec which will be identified by class.
     * </ul>
     * 
     * The options.deliveryTagHandler (if not null) is called before com.rabbitmq.client.Channel.basicPublish is called.
     * It is possible (and common) for a message confirmation to be received <i>before</i> com.rabbitmq.client.Channel.basicPublish returns
     * - any housekeeping to be done with the deliveryTag must be carried out in this handler.
     * 
     * @see com.rabbitmq.client.Channel.basicPublish
     * @see <a href="https://www.rabbitmq.com/alarms.html">Resource-driven alarms</a>
     * @param options options relating to this library's handling of the message
     * @param exchange the exchange to publish the message to
     * @param routingKey the routing key
     * @param mandatory true if the 'mandatory' flag is to be set
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @return A Future that will be completed when the message has been sent.
     */
  Future<Void> basicPublish(RabbitMQPublishOptions options, String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, Object body);
  
  Future<Void> confirmSelect();

  Future<Void> waitForConfirms(long timeout);

  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage);
    
  /**
   * Register a message codec.
   * 
   * You can register a message codec if you want to send any non standard message to the broker. E.g. you might want to send POJOs directly to the broker.
   * 
   * The MessageCodecs used by the RabbitMQ client are compatible with those used by the Vert.x EventBus
   * , but there are some differences that mean the default implementations are not the same implementations for both:
   * 
   * <ul>
   * <li>The EventBus codecs are natively working with a Buffer that may contain more bytes than the message being processed
   * RabbitMQ messages are natively considered to be byte arrays, so the Buffer seen by a codec is guaranteed to only be used for that purpose.
   * <li>EventBus messages are only ever seen by Vert.x clients.
   * RabbitMQ messages are written by other processes that could have nothing to do with Vert.x.
   * </ul>
   * 
   * The result of these differences is that the RabbitMQ JSON codecs cannot prefix the JSON data with a length value (as the Event Bus Json MessageCodecs do), because that is not
   * useful and is not the expected behaviour when handling JSON data in a RabbitMQ message.
   * 
   * Most of the message codecs for base types are the same (i.e. this library uses the EventBus MessageCodecs for most base types)
   * , however the logic of the RabbitMQ Boolean MessageCodec is the inverse of the Event Bus Boolean Message Codec - the Rabbit MQ codec uses 1 for true and 0 for false.
   * RabbitMQ messages tend not to consist of just a single base value, but they can (as long as the recipient expects big-endian values).
   * 
   * @param <T> The type that the codec can encode and decode.
   * @param codec the message codec to register
   * @return a reference to this, so the API can be used fluently
   */
  <T> RabbitMQChannel registerCodec(RabbitMQMessageCodec<T> codec);
  
  <T> RabbitMQChannel unregisterCodec(String name);
  
  <T> RabbitMQChannel registerDefaultCodec(Class<T> clazz, RabbitMQMessageCodec<T> codec);
  
  <T> RabbitMQChannel unregisterDefaultCodec(Class<T> clazz);
    
}
