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

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 *
 * @author jtalbut
 */
@RunWith(VertxUnitRunner.class)
public class RabbitMQClientWithoutServerTest {
  
  @Rule
  public RunTestOnContext testRunContext = new RunTestOnContext();

  @Test  
  public void testCreateWithNoRabbitMq(TestContext context) {
    RabbitMQOptions config = new RabbitMQOptions();
    config.setUri("amqp://nonexistant.localhost:0/");
    RabbitMQConnection connection = RabbitMQClient.create(testRunContext.vertx(), config);

    RabbitMQChannel channel = connection.createChannel();
    Async async = context.async();
    channel.connect()
            .onComplete(ar -> {
              if (ar.succeeded()) {
                context.fail("Expected failure as the URI is invalid");
              } else {
                async.complete();
              }
            });
  }

}
