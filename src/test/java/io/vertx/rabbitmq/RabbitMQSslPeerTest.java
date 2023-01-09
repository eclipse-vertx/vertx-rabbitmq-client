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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;


/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQSslPeerTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQSslPeerTest.class);
  
  private static final GenericContainer CONTAINER = RabbitMQBrokerProvider.getRabbitMqContainerWithPeerValidation();
  
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
  public void testCreateWithSpecificCert(TestContext context) throws Exception {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            .setTlsHostnameVerification(false)
            .setTrustStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath(fullPathForResource("/ssl-server/localhost-test-rabbit-store"))
            )
            .setKeyStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath(fullPathForResource("/ssl-server/client/client_certificate.p12"))
            )
            ;

    RabbitMQConnection[] connection = new RabbitMQConnection[1];
    Async async = context.async();
    
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].openChannel();
            })
            .compose(channel -> channel.exchangeDeclare("testCreateWithSpecificCert", BuiltinExchangeType.FANOUT, true, true, null))
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
  public void testFailWithoutPeerCertCreateWithSpecificCert(TestContext context) throws Exception {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            .setTlsHostnameVerification(false)
            .setKeyStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath(fullPathForResource("/ssl-server/localhost-test-rabbit-store")
                            )
            )
            ;
    
    RabbitMQConnection[] connection = new RabbitMQConnection[1];
    Async async = context.async();
    

    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(conn -> {
              connection[0] = conn;
              return connection[0].openChannel();
            })
            .onComplete(ar -> {
              if (ar.succeeded()) {
                context.fail("Expected to fail");
              } else {
                async.complete();
              }
            });
  }

}
