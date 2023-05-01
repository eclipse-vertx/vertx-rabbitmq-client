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
import static org.junit.Assert.assertFalse;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

/**
 *
 * @author jtalbut
 */
public class RabbitMQConsumerOptionsTest {
  
  @SuppressWarnings("constantname")
  private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerOptionsTest.class);
  
  @Test
  public void testToJson() {
    
    RabbitMQConsumerOptions options = new RabbitMQConsumerOptions()
            .setAutoAck(true)
            .setExclusive(false)
            .setReconnectInterval(1234)
            .setArguments(ImmutableMap.<String, Object>builder().put("a", "b").build())
            .setCancelHandler(con -> {})
            ;
    JsonObject json = options.toJson();
    logger.info("Json: {}", json);
    assertEquals(true, json.getBoolean("autoAck"));
    assertEquals(false, json.getBoolean("exclusive"));
    assertEquals(1234, json.getInteger("reconnectInterval").intValue());
    assertEquals("b", json.getJsonObject("arguments").getString("a"));
    assertFalse(json.containsKey("cancelHandler"));
    
  }
}
