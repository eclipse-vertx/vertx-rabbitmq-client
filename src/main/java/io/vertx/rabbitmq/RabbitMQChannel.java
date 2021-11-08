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
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.streams.ReadStream;
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
   * 
   * @param channelRecoveryCallback 
   */
  void addChannelRecoveryCallback(Handler<RabbitMQChannel> channelRecoveryCallback);
  
  void addChannelShutdownHandler(Handler<ShutdownSignalException> handler);
  
  /**
   * Creates a RabbitMQPublisher on this channel that reliably sends messages.
   * @param exchange The exchange that messages are to be sent to.
   * @param options Options for configuring the publisher.
   * @return a RabbitMQPublisher on this channel that reliably sends messages.
   */
  RabbitMQRepublishingPublisher createPublisher(String exchange, RabbitMQPublisherOptions options);
      
  /**
   * Creates a RabbitMQPublisher on this channel that reliably sends messages.
   * @param exchange The exchange that messages are to be sent to.
   * @param options Options for configuring the publisher.
   * @return a RabbitMQPublisher on this channel that reliably sends messages.
   */
  RabbitMQFuturePublisher createFuturePublisher(String exchange, RabbitMQPublisherOptions options);
      
  /**
   * Create a RabbitMQConsumer on this channel that reliably receives messages.
   * @param queue The queue that messages are being pushed from.
   * @param options Options for configuring the consumer.
   * @return a RabbitMQConsumer on this channel that can reliably receives messages.
   * After being constructed and configured the RabbitMQConsumer should be passed to the basicConsume method.
   */
  RabbitMQConsumer createConsumer(String queue, RabbitMQConsumerOptions options);
  
  Future<ReadStream<RabbitMQConfirmation>> addConfirmListener(int maxQueueSize);
  
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
  
  Future<Void> basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body);
  
  Future<Void> basicPublish(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body, Handler<Long> deliveryTagHandler);

  Future<Void> basicPublishWithConfirm(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body);
  
  Future<Void> basicPublishWithConfirm(String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, byte[] body, Handler<Long> deliveryTagHandler);

  Future<Void> confirmSelect();

  Future<Void> waitForConfirms(long timeout);

  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage);
    
}
