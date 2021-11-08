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

import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

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
            .setMaxInternalQueueSize(78)
            ;
    JsonObject json = options.toJson();
    logger.info("Json: {}", json);
    assertEquals(true, json.getBoolean("autoAck").booleanValue());
    assertEquals(78, json.getInteger("maxInternalQueueSize").intValue());
    
  }
}
