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
import org.junit.ClassRule;
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
  
  @ClassRule
  public static final GenericContainer rabbitmq = RabbitMQBrokerProvider.getRabbitMqContainerWithPeerValidation();
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();
  
  private String fullPathForResource(String resource) {
    return this.getClass().getResource(resource).getFile();
  }
      
  @Test
  public void testCreateWithSpecificCert(TestContext context) throws Exception {
    RabbitMQOptions config = new RabbitMQOptions()
            .setUri("amqps://" + rabbitmq.getContainerIpAddress() + ":" + rabbitmq.getMappedPort(5671))
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

    RabbitMQConnection connection = RabbitMQClient.create(testRunContext.vertx(), config);
    RabbitMQChannel channel = connection.createChannel();
    Async async = context.async();
    channel.connect()
            .compose(v -> channel.exchangeDeclare("testCreateWithSpecificCert", BuiltinExchangeType.FANOUT, true, true, null))
            .onComplete(ar -> {
              if (ar.succeeded()) {
                logger.info("Exchange declared");
                logger.info("Completing test");
                connection.close().onComplete(ar2 -> {
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
            .setUri("amqps://" + rabbitmq.getContainerIpAddress() + ":" + rabbitmq.getMappedPort(5671))
            .setConnectionName(this.getClass().getSimpleName() + "testCreateWithSpecificCert")
            .setTlsHostnameVerification(false)
            .setKeyStoreOptions(
                    new JksOptions()
                            .setPassword("password")
                            .setPath(fullPathForResource("/ssl-server/localhost-test-rabbit-store")
                            )
            )
            ;

    RabbitMQConnection connection = RabbitMQClient.create(testRunContext.vertx(), config);
    RabbitMQChannel channel = connection.createChannel();
    Async async = context.async();
    channel.connect()
            .onComplete(ar -> {
              if (ar.succeeded()) {
                context.fail("Expected to fail");
              } else {
                async.complete();
              }
            });
  }

}
