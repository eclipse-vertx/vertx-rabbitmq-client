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

import com.rabbitmq.client.BuiltinExchangeType;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import static org.junit.Assume.assumeTrue;
import org.junit.BeforeClass;






/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQSslTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQSslTest.class);
  
  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();
  
  @BeforeClass
  public static void startup() {
    CONTAINER.start();
  }
  
  @AfterClass
  public static void shutdown() {
    CONTAINER.stop();
  }
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();
  
  private String fullPathForResource(String resource) {
    return this.getClass().getResource(resource).getFile();
  }
    
  @Test
  public void testCreateWithInsecureServer(TestContext context) {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithInsecureServer")
            .setTrustAll(true)
            ;
    
    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].createChannelBuilder().openChannel();
            })
            .compose(chann -> chann.getManagementChannel().exchangeDeclare("testCreateWithWorkingServer", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
                logger.info("Completing test");
                connection[0].close().onComplete(ar2 -> {
                  async.complete();                     
                });
              } else {
                logger.info("Failing test");
                context.fail(ar.cause());
              }
            });
  }
  
  @Test
  public void testFailWithInsecureServer(TestContext context) {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithInsecureServer")
            // .setTrustAll(true)
            ;
    
    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].createChannelBuilder().openChannel();
            })
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Should have failed to connect");
                context.fail(ar.cause());
              } else {
                logger.info("Completing test");
                async.complete();                     
              }
            });
  }
  
  @Test
  public void testCreateWithSpecificCert(TestContext context) throws Exception {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            .setTlsHostnameVerification(false)
            .setTrustStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath(fullPathForResource("/ssl-server/localhost-test-rabbit-store")
                            )
            )
            ;

    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].createChannelBuilder().openChannel();
            })
            .compose(channel -> channel.getManagementChannel().exchangeDeclare("testCreateWithSpecificCert", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
                logger.info("Completing test");
                connection[0].close().onComplete(ar2 -> {
                  async.complete();                     
                });
              } else {
                logger.info("Failing test");
                context.fail(ar.cause());
              }
            });
  }

  
  @Test
  public void testCreateWithSslContextFactory(TestContext context) throws Exception {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            .setTlsHostnameVerification(false)
            .setSslContextFactory((String name) -> {
              logger.info("Creating SSL Context for {}", name);
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
                logger.error("Failed to prepare SSLContext: ", ex);
              }
              return c;
            })
            ;

    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].createChannelBuilder().openChannel();
            })
            .compose(channel -> channel.getManagementChannel().exchangeDeclare("testCreateWithSpecificCert", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
                logger.info("Completing test");
                connection[0].close().onComplete(ar2 -> {
                  async.complete();                     
                });
              } else {
                logger.info("Failing test");
                context.fail(ar.cause());
              }
            });
  }
  
  @Test
  public void testFailWithSpecificCert(TestContext context) throws Exception {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            // .setTlsHostnameVerification(false)
            .setTrustStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath(fullPathForResource("/ssl-server/localhost-test-rabbit-store")
                            )
            )
            ;

    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].createChannelBuilder().openChannel();
            })
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Should have failed to connect");
                context.fail(ar.cause());
              } else {
                logger.info("Completing test");
                async.complete();                     
              }
            });
  }
    
  @Test
  public void testCreateWithPublicCertChain(TestContext context) throws Exception {
    String url = RabbitMQSslRawTest.getPublicAmqpInstance();
    assumeTrue(url != null && !url.isEmpty());
    
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri(url)
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithPublicCertChain")
            ;
    
    RabbitMQConnection connection[] = new RabbitMQConnection[1];
    Async async = context.async();
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].createChannelBuilder().openChannel();
            })
            .compose(channel -> channel.getManagementChannel().exchangeDeclare("testCreateWithPublicCertChain", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
                logger.info("Completing test");
                connection[0].close().onComplete(ar2 -> {
                  async.complete();                     
                });
              } else {
                logger.info("Failing test");
                context.fail(ar.cause());
              }
            });
  }
    
}
