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
package examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Envelope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.rabbitmq.DefaultConsumer;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import io.vertx.rabbitmq.impl.codecs.RabbitMQLongMessageCodec;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;


/**
 *
 * @author jtalbut
 */
public class RabbitMQExamples {
  
  private static Logger logger = Logger.getLogger("examples.RabbitMQExamples");
  
  private final String EXCHANGE_NAME = this.getClass().getName() + "Exchange";
  private final String QUEUE_NAME = this.getClass().getName() + "Queue";
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE = false;
  private static final BuiltinExchangeType DEFAULT_RABBITMQ_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_DURABLE = true;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE = false;
  private static final boolean DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE = false;
    
  public void createConnectionWithUri() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", false, new AMQP.BasicProperties(), "body");
            });
  }
  
  public void createConnectionWithManualParameters() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setHost("brokerhost");
    config.setPort(5672);
    config.setVirtualHost("vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete((AsyncResult<Void> ar) -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
  
  public void createConnectionWithMultipleHost() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setAddresses(
            Arrays.asList(
                    Address.parseAddress("brokerhost1:5672")
                    , Address.parseAddress("brokerhost2:5672")
            )
    );
    config.setVirtualHost("vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
  
  public void createConnectionAndUseImmediately() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
  
  /**
   * @see RabbitMQSslTest#testCreateWithInsecureServer(TestContext context)
   */
  public void createWithInsecureServer() {    
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://localhost:5671")
            .setConnectionName(ManagementFactory.getRuntimeMXBean().getName())
            .setTrustAll(true)
            ;
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
  
  /**
   * @see RabbitMQSslTest#testCreateWithSpecificCert(TestContext context)
   */
  public void createWithSpecificCert() throws Exception {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://localhost:5671")
            .setConnectionName(ManagementFactory.getRuntimeMXBean().getName())
            .setTlsHostnameVerification(false)
            .setTrustStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath("/etc/ssl-server/localhost-test-rabbit-store") // Full path to trust store file
            )
            ;

    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }

  /**
   * @see RabbitMQSslTest#testCreateWithSslContextFactory(TestContext context)
   */
  public void createWithSslContextFactory() throws Exception {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://localhost:5671")
            .setConnectionName(ManagementFactory.getRuntimeMXBean().getName())
            .setTlsHostnameVerification(false)
            .setSslContextFactory((String name) -> {
              logger.info("Creating SSL Context for " + name);
              SSLContext c = null;
              try {
                char[] trustPassphrase = "password".toCharArray();
                KeyStore tks = KeyStore.getInstance("JKS");
                InputStream tustKeyStoreStream = this.getClass().getResourceAsStream("/ssl-server/localhost-test-rabbit-store");
                tks.load(tustKeyStoreStream, trustPassphrase);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(tks);

                // com.rabbitmq:amqp-client:5.13.1 (at least) hangs when using TLSv1.3 with NIO
                c = SSLContext.getInstance("TLSv1.2");
                c.init(null, tmf.getTrustManagers(), null);
              } catch(IOException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException ex) {
                logger.log(Level.SEVERE, "Failed to prepare SSLContext: ", ex);
              }
              return c;
            })
            ;

    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
      
  /**
   * @see RabbitMQSslTest#testCreateWithPublicCertChain(TestContext context)
   */
  public void createWithPublicCertChain() throws Exception {
    Vertx vertx = Vertx.vertx();
    String url = "amqps://"; // URL of RabbitMQ instance running in the cloud
    
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri(url)
            .setConnectionName(ManagementFactory.getRuntimeMXBean().getName())
            ;
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
  
  public void createWithClientCert() throws Exception {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://localhost:5671")
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            .setTlsHostnameVerification(false)
            .setTrustStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath("/etc/ssl-server/localhost-test-rabbit-store") // Full path to trust store file
            )
            .setKeyStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath("/etc/ssl-server/client/client_certificate.p12") // Full path to key store file
            )
            ;

    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }
  
  public void basicPublish() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> {
              return channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null)
                      .compose(v -> channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "routingKey", false, null, "Body".getBytes(StandardCharsets.UTF_8)))
                      ;
            })
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Message sent");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }  
  
  public void basicPublishNamedCodec(RabbitMQChannel channel) {
    channel.registerCodec(new CustomClassCodec());
    channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.basicPublish(new RabbitMQPublishOptions().setCodec("deflated-utf16"), EXCHANGE_NAME, "routingKey", false, null, "String message"))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Message sent");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }  
  
  public void basicPublishTypedCodec(RabbitMQChannel channel) {
    channel.registerDefaultCodec(CustomClass.class, new CustomClassCodec());
    channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "routingKey", false, null, new CustomClass(17, "title", 17.8)))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Message sent");
              } else {
                logger.log(Level.SEVERE, "Failed: {0}", ar.cause());
              }
            });
  }  
  
  public void connectionEstablishedCallback(RabbitMQChannel channel) {
    channel.addChannelEstablishedCallback(p -> {
      channel.exchangeDeclare(EXCHANGE_NAME, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> channel.queueDeclare(QUEUE_NAME, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "", null))
              .onComplete(p);
    });
  }
  
  public void connectionEstablishedCallbackForServerNamedAutoDeleteQueue(RabbitMQChannel channel) {
    AtomicReference<RabbitMQConsumer> consumer = new AtomicReference<>();
    channel.addChannelEstablishedCallback(promise -> {
      // Note that the use of of an auto-delete queue will cause message loss when a connection drops, but new messages should be received after recovery.
      channel.exchangeDeclare(EXCHANGE_NAME, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> channel.queueDeclare("", false, true, true, null))
              .compose(brokerQueueName -> {
                logger.info("Queue declared as " + brokerQueueName);

                // The first time this runs there will be no existing consumer
                // on subsequent connections the consumer needs to be update with the new queue name
                RabbitMQConsumer<Long> currentConsumer = consumer.get();

                if (currentConsumer != null) {
                  currentConsumer.setQueueName(brokerQueueName);
                } else {
                  currentConsumer = channel.createConsumer(new RabbitMQLongMessageCodec(), brokerQueueName, new RabbitMQConsumerOptions());
                  currentConsumer.handler(message -> {
                    logger.log(Level.INFO, "Got message {0} from {1}", new Object[]{message.body(), brokerQueueName});
                  });
                  consumer.set(currentConsumer);
                  currentConsumer.consume(true, null);
                }
                return channel.queueBind(brokerQueueName, EXCHANGE_NAME, "", null);
              })
              .onComplete(promise);
    });
  }
 
  /**
   * @see RabbitMQPublishCustomCodecTest )
   */
  public void createConsumerWithCodec(RabbitMQChannel channel) {
    channel.registerCodec(new CustomClassCodec());    
    RabbitMQConsumer<CustomClass> consumer = channel.createConsumer(new CustomClassCodec(), "queue", new RabbitMQConsumerOptions());
    consumer.handler(message -> {
      CustomClass cc = message.body();
    });
    consumer.consume(true, null);
  }
  
  public void basicConsume() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> connection.openChannel())
            .compose(channel -> {
              return channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true, true, null)
                      .compose(v -> channel.queueDeclare("queue", true, true, true, null))
                      .compose(v -> channel.queueBind("queue", EXCHANGE_NAME, "", null))
                      .compose(v -> channel.basicConsume("queue", true, channel.getChannelId(), false, false, null, new DefaultConsumer(channel) {              
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                          System.out.println(new String(body, StandardCharsets.UTF_8));
                        }
                      }));
            });
  }
  
  
  
}
