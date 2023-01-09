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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.AfterClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;
import org.junit.BeforeClass;

/**
 *
 * @author jtalbut
 */
public class RabbitMQSslRawTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQSslRawTest.class);
  
  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainer();  
  
  @BeforeClass
  public static void startup() {
    CONTAINER.start();
  }
  
  @AfterClass
  public static void shutdown() {
    CONTAINER.stop();
  }
 
  public static String getPublicAmqpInstance() throws Exception {
    Properties props = new Properties();
    try (InputStream stream = RabbitMQSslRawTest.class.getResourceAsStream("/public-amqp.properties")) {
      props.load(stream);
    }
    return props.getProperty("amqp-url");
  }
  
  @Test
  public void testSslWithTrustAnything() throws Throwable {
    
    // com.rabbitmq:amqp-client:5.13.1 (at least) hangs when using TLSv1.3 with NIO
    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol();
    factory.useNio();
    // factory.enableHostnameVerification();
    factory.setHost("localhost");
    factory.setPort(CONTAINER.getMappedPort(5671));

    Connection conn = factory.newConnection();
    assertNotNull(conn);
    Channel channel = conn.createChannel();
    channel.queueDeclare("queue", true, true, true, null);
    logger.info("Connected");
    conn.close();

  }

  @Test
  public void testSslWithTrustedCert() throws Throwable {
    
    char[] trustPassphrase = "password".toCharArray();
    KeyStore tks = KeyStore.getInstance("JKS");
    InputStream tustKeyStoreStream = this.getClass().getResourceAsStream("/ssl-server/localhost-test-rabbit-store");
    tks.load(tustKeyStoreStream, trustPassphrase);

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(tks);

    // com.rabbitmq:amqp-client:5.13.1 (at least) hangs when using TLSv1.3 with NIO
    SSLContext c = SSLContext.getInstance("TLSv1.2");
    c.init(null, tmf.getTrustManagers(), null);

    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol(c);
    factory.useNio();
    // factory.enableHostnameVerification();
    factory.setHost("localhost");
    factory.setPort(CONTAINER.getMappedPort(5671));

    Connection conn = factory.newConnection();
    assertNotNull(conn);
    Channel channel = conn.createChannel();
    channel.queueDeclare("queue", true, true, true, null);
    logger.info("Connected");
    conn.close();
  }
  
  @Test
  public void testSslWithCertChain() throws Throwable {
    String url = RabbitMQSslRawTest.getPublicAmqpInstance();
    assumeTrue(url != null && !url.isEmpty());
    
    // com.rabbitmq:amqp-client:5.13.1 (at least) hangs when using TLSv1.3 with NIO
    SSLContext c = SSLContext.getInstance("TLSv1.2");
    c.init(null, null, null);

    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol(c);
    factory.useNio();
    factory.enableHostnameVerification();
    factory.setUri(url);

    Connection conn = factory.newConnection();
    assertNotNull(conn);
    Channel channel = conn.createChannel();
    channel.queueDeclare("queue", true, true, true, null);
    logger.info("Connected");
    conn.close();

  }
  
  @Test
  public void testSslWithSslContextFactory() throws Throwable {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setSslContextFactory((String name) -> {
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
    });
    factory.useNio();
    // factory.enableHostnameVerification();
    factory.setHost("localhost");
    factory.setPort(CONTAINER.getMappedPort(5671));

    Connection conn = factory.newConnection("testSslWithSslContextFactory");
    assertNotNull(conn);
    Channel channel = conn.createChannel();
    channel.queueDeclare("queue", true, true, true, null);
    logger.info("Connected");
    conn.close();

  }

}
