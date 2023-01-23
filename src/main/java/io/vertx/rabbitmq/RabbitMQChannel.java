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
package io.vertx.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import io.vertx.core.Future;
import java.util.Map;

/**
 * A RabbitMQ Channel.
 * <p>
 * A channel is used for issuing commands to the RabbitMQ broker.
 * Channels exist on top of a Connection and multiple Channels can be used for a single Connection.
 * <p>
 * The {@link RabbitMQConsumer} and {@link RabbitMQPublisher} classes manage their own channels and it is not necessary
 * to explicitly use the RabbitMQChannel if using those classes via the factory methods in {@link RabbitMQConnection}.
 * <p>
 * In most situations the methods here ensure that the connection and channel are open (performing recovery if appropriate) and then
 * delegate to the appropriate methods on the {@link com.rabbitmq.client.Channel} class, calling them on a worker thread.
 * The one exception is {@code basicPublish} which will, if conditions are right, delegate directly to 
 * {@code Channel.basicPublish}, which is non-blocking because the connection is always using NIO.
 * <p>
 * The {@code Channel} class has many overloads to enable callers to use simpler methods.
 * This class prefers to only provide the fullest method.
 * <p>
 * @author jtalbut
 * @see <a href="https://www.rabbitmq.com/channels.html">Channels</a>
 * @see com.rabbitmq.client.Channel
 */
public interface RabbitMQChannel {
  
  /**
   * Returns an identifier of the underlying channel.
   * <p>
   * This is the channelNumber of the current instance of the underlying channel.
   * <p>
   * The reconnect mechanism can result in the underlying channel being replaced, which will invalidate
   * any delivery tags from the previous channel.
   * Use the channel number to scope any delivery tags that are stored.
   * <p>
   * @return an identifier of the underlying channel.
   */
  int getChannelNumber();
  
  /**
   * Returns a unique identifier for the underlying channel with a greater scope than the channel number.
   * <p>
   * Unlike the channel number the channel ID should be suitable for identifying the channel on the broker.
   * <p>
   * The channel ID is made up of the connection name, the connection instance number and the channel number.
   * <p>
   * @return a unique identifier for the underlying channel with a greater scope than the channel number.
   */
  String getChannelId();
    
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
     * @see <a href="https://www.rabbitmq.com/publishers.html#protocols">Protocol Differences</a> for details of the mandatory flag
     * @param options options relating to this library's handling of the message
     * @param exchange the exchange to publish the message to
     * @param routingKey the routing key
     * @param mandatory true if the 'mandatory' flag is to be set
     * If the mandatory flag is set to true the broker will return the message to the publisher if it would be discarded (because there is no queue bound to the exchange).
     * The message is returned via a registered "returned message handler" ({@link RabbitMQChannelBuilder#withReturnedMessageHandler(io.netty.util.Recycler.Handle)).
     * @param props other properties for the message - routing headers etc
     * @param body the message body
     * @return A Future that will be completed when the message has been sent.
     */
  Future<Void> basicPublish(RabbitMQPublishOptions options, String exchange, String routingKey, boolean mandatory, AMQP.BasicProperties props, Object body);
  
  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage);
  
  Future<Void> basicAck(long channelNumber, long deliveryTag, boolean multiple);
  
  Future<Void> basicNack(long channelNumber, long deliveryTag, boolean multiple);
  
  <T> Future<T> onChannel(ChannelFunction<T> handler);  
  
  RabbitMQManagementChannel getManagementChannel();
    
}
