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
package examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Envelope;
import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.rabbitmq.DefaultConsumer;
import io.vertx.rabbitmq.RabbitMQChannel;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConnection;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQConsumerOptions;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rabbitmq.RabbitMQPublishOptions;
import io.vertx.rabbitmq.impl.codecs.RabbitMQLongMessageCodec;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
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
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);    
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .onComplete(ar -> {
            });
  }
  
  public void createConnectionWithManualParameters() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setHost("brokerhost");
    config.setPort(5672);
    config.setVirtualHost("vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);    
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .onComplete(ar -> {
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
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);    
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .onComplete(ar -> {
            });
  }
  
  public void createConnectionAndUseImmediately() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);    
    RabbitMQChannel channel = connection.createChannel();
    channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.queueDeclare("queue", true, true, true, null))
            .compose(v -> channel.queueBind("queue", "exchange", "", null))
            .onComplete(ar -> {
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
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .compose(v -> channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.info("Failing test");
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

    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .compose(v -> channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.info("Failing test");
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
              } catch(Exception ex) {
                logger.log(Level.SEVERE, "Failed to prepare SSLContext: ", ex);
              }
              return c;
            })
            ;

    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .compose(v -> channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.info("Failing test");
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
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .compose(v -> channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.info("Failing test");
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

    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);
    RabbitMQChannel channel = connection.createChannel();
    channel.connect()
            .compose(v -> channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
              } else {
                logger.info("Failing test");
              }
            });
  }
  
  public void basicPublish() {
    Vertx vertx = Vertx.vertx();
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://brokerhost/vhost");
    config.setConnectionName(this.getClass().getSimpleName());
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);    
    RabbitMQChannel channel = connection.createChannel();
    channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.basicPublish(new RabbitMQPublishOptions(), "exchange", "routingKey", false, null, "Body".getBytes(StandardCharsets.UTF_8)))
            .onComplete(ar -> {
            });
  }  
  
  public void basicPublishNamedCodec(RabbitMQConnection connection) {
    RabbitMQChannel channel = connection.createChannel();
    channel.registerCodec(new CustomClassCodec());
    channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.basicPublish(new RabbitMQPublishOptions().setCodec("deflated-utf16"), "exchange", "routingKey", false, null, "String message"))
            .onComplete(ar -> {
            });
  }  
  
  public void basicPublishTypedCodec(RabbitMQConnection connection) {
    RabbitMQChannel channel = connection.createChannel();
    channel.registerDefaultCodec(CustomClass.class, new CustomClassCodec());
    channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.basicPublish(new RabbitMQPublishOptions(), "exchange", "routingKey", false, null, new CustomClass(17, "title", 17.8)))
            .onComplete(ar -> {
            });
  }  
  
  public void connectionEstablishedCallback(RabbitMQConnection connection) {
    RabbitMQChannel channel = connection.createChannel();
    channel.addChannelEstablishedCallback(p -> {
      channel.exchangeDeclare(EXCHANGE_NAME, DEFAULT_RABBITMQ_EXCHANGE_TYPE, DEFAULT_RABBITMQ_EXCHANGE_DURABLE, DEFAULT_RABBITMQ_EXCHANGE_AUTO_DELETE, null)
              .compose(v -> channel.queueDeclare(QUEUE_NAME, DEFAULT_RABBITMQ_QUEUE_DURABLE, DEFAULT_RABBITMQ_QUEUE_EXCLUSIVE, DEFAULT_RABBITMQ_QUEUE_AUTO_DELETE, null))
              .compose(v -> channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "", null))
              .onComplete(p);
    });
  }
  
  public void connectionEstablishedCallbackForServerNamedAutoDeleteQueue(RabbitMQConnection connection) {
    AtomicReference<RabbitMQConsumer> consumer = new AtomicReference<>();
    RabbitMQChannel channel = connection.createChannel();
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
  public void createConsumerWithCodec(RabbitMQConnection connection) {
    RabbitMQChannel channel = connection.createChannel();
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
    
    RabbitMQConnection connection = RabbitMQClient.create(vertx, config);    
    RabbitMQChannel channel = connection.createChannel();
    channel.exchangeDeclare("exchange", BuiltinExchangeType.FANOUT, true, true, null)
            .compose(v -> channel.queueDeclare("queue", true, true, true, null))
            .compose(v -> channel.queueBind("queue", "exchange", "", null))
            .compose(v -> channel.basicConsume("queue", true, channel.getChannelId(), false, false, null, new DefaultConsumer(channel) {              
              @Override
              public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                System.out.println(new String(body, StandardCharsets.UTF_8));
              }
            }))
            .onComplete(ar -> {
            });
  }
  
  
  
}
