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
