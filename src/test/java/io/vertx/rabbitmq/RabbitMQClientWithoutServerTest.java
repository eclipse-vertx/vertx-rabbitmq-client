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
    
    RabbitMQClient.connect(testRunContext.vertx(), config)
            .compose(connection -> connection.openChannel())
            .onComplete(ar -> {
              if (ar.succeeded()) {
                context.fail("Expected failure as the URI is invalid");
              } else {
                context.async().complete();
              }
            });
  }

}
