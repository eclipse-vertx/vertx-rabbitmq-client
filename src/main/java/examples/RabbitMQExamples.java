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
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Envelope;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.rabbitmq.DefaultConsumer;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQChannelBuilder;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import io.vertx.rabbitmq.impl.RabbitMQCodecManager;
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
  private static final boolean EXCHANGE_DURABLE = true;
  private static final boolean EXCHANGE_AUTO_DELETE = false;
  private static final BuiltinExchangeType EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;
  private static final boolean QUEUE_DURABLE = true;
  private static final boolean QUEUE_EXCLUSIVE = false;
  private static final boolean QUEUE_AUTO_DELETE = false;
  private static final boolean PUBLISH_MANDATORY = false;
    
  public void createConnectionWithUri() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {
              RabbitMQChannelBuilder builder = connection.createChannelBuilder();
              return builder.openChannel();
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
  public void createConnectionWithManualParameters() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setHost("brokerhost");
    config.setPort(5672);
    config.setVirtualHost("vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {
              return connection.createChannelBuilder().openChannel();
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
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
            .compose(connection -> {
              return connection.createChannelBuilder().openChannel();
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
  public void createConnectionWithUriAndCredentials() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    config.setUser("guest");
    config.setPassword("guest");
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {
              RabbitMQChannelBuilder builder = connection.createChannelBuilder();
              return builder.openChannel();
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
    
  public void createConnectionWithUriAndUseMgmtChannel() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {
              RabbitMQChannelBuilder builder = connection.createChannelBuilder();
              return builder.openChannel();
            })
            .compose(channel -> {
              return channel.getManagementChannel().exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null)
                      .map(channel);
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }            
  
  public void createConnectionAndDeclareExchangeInHandler() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .withChannelOpenHandler(chann -> {
                        chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      })
                      .openChannel();
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
  public void createConnectionWithUriCreateExchangeOnChannel() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .openChannel();
            })
            .compose(channel -> {
              return channel.onChannel(chann -> {
                return chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
              }).map(channel);
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "", PUBLISH_MANDATORY, new AMQP.BasicProperties(), "body");
            })
            .onSuccess(v -> logger.info("Message published"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
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
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .withChannelOpenHandler(chann -> {
                        chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      })
                      .openChannel();
            })
            .onSuccess(v -> logger.info("Exchange declared"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
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
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .withChannelOpenHandler(chann -> {
                        chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      })
                      .openChannel();
            })
            .onSuccess(v -> logger.info("Exchange declared"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
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
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .withChannelOpenHandler(chann -> {
                        chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      })
                      .openChannel();
            })
            .onSuccess(v -> logger.info("Exchange declared"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
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
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .withChannelOpenHandler(chann -> {
                        chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      })
                      .openChannel();
            })
            .onSuccess(v -> logger.info("Exchange declared"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
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
            .compose(connection -> {
              return connection.createChannelBuilder()
                      .withChannelOpenHandler(chann -> {
                        chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      })
                      .openChannel();
            })
            .onSuccess(v -> logger.info("Exchange declared"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
  public void basicPublish() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQClient.connect(vertx, config).compose(connection -> {      
            return connection.createChannelBuilder()                      
                    .withChannelOpenHandler(chann -> {
                      chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                    })
                    .openChannel();
            })
            .compose(channel -> {
              return channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "routingKey", false, null, "Body".getBytes(StandardCharsets.UTF_8));
            })
            .onSuccess(v -> logger.info("Message sent"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }  
  
  public void basicPublishNamedCodec(Vertx vertx, RabbitMQOptions config) {
    RabbitMQClient.connect(vertx, config).compose(connection -> {      
            return connection.createChannelBuilder()
                    .withNamedCodec(new CustomStringCodec())
                    .withChannelOpenHandler(chann -> {
                      chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                    })
                    .openChannel();
            })
            .compose(channel -> channel.basicPublish(new RabbitMQPublishOptions().setCodec(CustomStringCodec.NAME), EXCHANGE_NAME, "routingKey", false, null, "String message"))
            .onSuccess(v -> logger.info("Message sent"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }  
  
  public void basicPublishTypedCodec(Vertx vertx, RabbitMQOptions config) {
    RabbitMQClient.connect(vertx, config)
            .compose(connection -> {      
              return connection.createChannelBuilder()
                    .withTypedCodec(CustomClass.class, new CustomClassCodec())
                    .withChannelOpenHandler(chann -> {
                      chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                    })
                    .openChannel();
            })
            .compose(channel -> channel.basicPublish(new RabbitMQPublishOptions(), EXCHANGE_NAME, "routingKey", false, null, new CustomClass(17, "title", 17.8)))
            .onSuccess(v -> logger.info("Message sent"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }  
  
  public void connectionEstablishedCallbackForServerNamedAutoDeleteQueue(Vertx vertx, RabbitMQOptions options) {
    String queueName[] = new String[1];
    RabbitMQClient.connect(vertx, options)
            .compose(connection -> {      
              return connection.createChannelBuilder()
                    .withChannelOpenHandler(chann -> {
                      chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      // No queue name passed in, to use an auto created queue
                      DeclareOk dok = chann.queueDeclare("", QUEUE_DURABLE, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);
                      queueName[0] = dok.getQueue();
                      chann.queueBind(QUEUE_NAME, EXCHANGE_NAME, "", null);
                    })
                    .createConsumer(null, null, () -> queueName[0], new RabbitMQConsumerOptions(), (con, msg) -> {
                      logger.log(Level.INFO, "Got message {0} from {1}", new Object[]{msg.body(), queueName[0]});          
                      return Future.succeededFuture();
                    })
                    ;
            })
            .onSuccess(v -> logger.info("Consumer created"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
 
  /**
   * @see RabbitMQPublishCustomCodecTest )
   */
  public void createConsumerWithCodec(RabbitMQChannel channel) {
//    channel.registerCodec(new CustomClassCodec());    
//    RabbitMQConsumer<CustomClass> consumer = channel.createConsumer(new CustomClassCodec(), "queue", new RabbitMQConsumerOptions());
//    consumer.handler(message -> {
//      CustomClass cc = message.body();
//    });
//    consumer.consume(true, null);
  }
  
  public void basicConsumeRaw(Vertx vertx, RabbitMQOptions options) {
    RabbitMQClient.connect(vertx, options)
            .compose(connection -> {      
              return connection.createChannelBuilder()
                    .withChannelOpenHandler(chann -> {
                      chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      chann.queueDeclare(QUEUE_NAME, QUEUE_DURABLE, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);
                      chann.queueBind(QUEUE_NAME, EXCHANGE_NAME, "", null);
                    })
                    .openChannel();
            })            
            .compose(channel -> {
              // Note that the second argument declares this to be an auto-ack consumer, which is no recommended for reliable communication.
              return channel.basicConsume("queue", true, channel.getChannelId(), false, false, null, new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                          System.out.println(new String(body, StandardCharsets.UTF_8));
                        }
                      });
            })
            .onSuccess(v -> logger.info("Consumer created"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
  public void consumer(Vertx vertx, RabbitMQOptions options) {
    RabbitMQClient.connect(vertx, options)
            .compose(connection -> {      
              return connection.createChannelBuilder()
                    .withChannelOpenHandler(chann -> {
                      chann.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE, null);
                      chann.queueDeclare(QUEUE_NAME, QUEUE_DURABLE, QUEUE_EXCLUSIVE, QUEUE_AUTO_DELETE, null);
                      chann.queueBind(QUEUE_NAME, EXCHANGE_NAME, "", null);
                    })
                    .createConsumer(
                            RabbitMQCodecManager.BYTE_ARRAY_MESSAGE_CODEC
                            , QUEUE_NAME
                            , null
                            , new RabbitMQConsumerOptions()
                                    .setAutoAck(false)
                            , (consumer, message) -> {
                              // In most cases consumers will have some asynchronouse code
                              return vertx.<Void>executeBlocking(promise -> {
                                System.out.println(new String(message.body(), StandardCharsets.UTF_8));
                                message.basicAck()
                                        .andThen(promise)
                                        ;
                              });
                            }
                    );
            })
            .onSuccess(v -> logger.info("Consumer created"))
            .onFailure(ex -> logger.log(Level.SEVERE, "Failed: {0}", ex))
            ;
  }
  
}
