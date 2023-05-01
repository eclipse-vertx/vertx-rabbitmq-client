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

import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author jtalbut
 */
public class RabbitMQPublisherOptionsTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQPublisherOptionsTest.class);
  
  @Test
  public void testToJson() {
    
    RabbitMQPublisherOptions options = new RabbitMQPublisherOptions()
            .setResendOnReconnect(true)
            ;
    JsonObject json = options.toJson();
    logger.info("Json: {}", json);
    assertEquals(true, json.getBoolean("resendOnReconnect"));
    assertEquals(1, json.size());
    
  }
}
