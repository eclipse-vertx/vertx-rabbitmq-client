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
package io.vertx.rabbitmq.impl.codecs;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.rabbitmq.RabbitMQMessageCodec;

/**
 *
 * @author jtalbut
 */
public class RabbitMQJsonArrayMessageCodec implements RabbitMQMessageCodec<JsonArray> {

  @Override
  public String codecName() {
    return "jsonArray";
  }

  @Override
  public byte[] encodeToBytes(JsonArray value) {
    return Json.encodeToBuffer(value).getBytes();
  }

  @Override
  public JsonArray decodeFromBytes(byte[] data) {
    return new JsonArray(Buffer.buffer(data));
  }

  @Override
  public String getContentType() {
    return "application/json";
  }

  @Override
  public String getContentEncoding() {
    return null;
  }
}
