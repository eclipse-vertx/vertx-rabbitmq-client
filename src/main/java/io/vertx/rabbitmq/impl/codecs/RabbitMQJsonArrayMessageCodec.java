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
