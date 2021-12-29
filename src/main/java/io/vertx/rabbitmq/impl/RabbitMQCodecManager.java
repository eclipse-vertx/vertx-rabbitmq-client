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
package io.vertx.rabbitmq.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQMessageCodec;
import io.vertx.rabbitmq.impl.codecs.RabbitMQBufferMessageCodec;
import io.vertx.rabbitmq.impl.codecs.RabbitMQByteArrayMessageCodec;
import io.vertx.rabbitmq.impl.codecs.RabbitMQJsonArrayMessageCodec;
import io.vertx.rabbitmq.impl.codecs.RabbitMQJsonObjectMessageCodec;
import io.vertx.rabbitmq.impl.codecs.RabbitMQNullMessageCodec;
import io.vertx.rabbitmq.impl.codecs.RabbitMQStringMessageCodec;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RabbitMQCodecManager {

  // The standard message codecs
  public static final RabbitMQMessageCodec<byte[]> BYTE_ARRAY_MESSAGE_CODEC = new RabbitMQByteArrayMessageCodec();
  public static final RabbitMQMessageCodec<Buffer> BUFFER_MESSAGE_CODEC = new RabbitMQBufferMessageCodec();
  public static final RabbitMQMessageCodec<Object> NULL_MESSAGE_CODEC = new RabbitMQNullMessageCodec();
  public static final RabbitMQMessageCodec<String> STRING_MESSAGE_CODEC = new RabbitMQStringMessageCodec();
  public static final RabbitMQMessageCodec<JsonObject> JSON_OBJECT_MESSAGE_CODEC = new RabbitMQJsonObjectMessageCodec();
  public static final RabbitMQMessageCodec<JsonArray> JSON_ARRAY_MESSAGE_CODEC = new RabbitMQJsonArrayMessageCodec();

  private final ConcurrentMap<String, RabbitMQMessageCodec> userCodecMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<Class, RabbitMQMessageCodec> defaultCodecMap = new ConcurrentHashMap<>();

  public RabbitMQMessageCodec lookupCodec(Object body, String codecName) {
    RabbitMQMessageCodec codec;
    if (codecName != null) {
      codec = userCodecMap.get(codecName);
      if (codec == null) {
        throw new IllegalArgumentException("No message codec for name: " + codecName);
      }
    } else if (body instanceof byte[]) {
      codec = BYTE_ARRAY_MESSAGE_CODEC;
    } else if (body == null) {
      codec = NULL_MESSAGE_CODEC;
    } else {
      codec = defaultCodecMap.get(body.getClass());
      if (codec == null) {
        if (body instanceof Buffer) {
          codec = BUFFER_MESSAGE_CODEC;
        } else if (body instanceof String) {
          codec = STRING_MESSAGE_CODEC;
        } else if (body instanceof JsonObject) {
          codec = JSON_OBJECT_MESSAGE_CODEC;
        } else if (body instanceof JsonArray) {
          codec = JSON_ARRAY_MESSAGE_CODEC;
        } 
      }
      if (codec == null) {
        throw new IllegalArgumentException("No message codec for type: " + body.getClass());
      }
    }
    return codec;
  }

  public RabbitMQMessageCodec getCodec(String codecName) {
    return userCodecMap.get(codecName);
  }

  public void registerCodec(RabbitMQMessageCodec codec) {
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(codec.codecName(), "code.codecName()");
    if (userCodecMap.containsKey(codec.codecName())) {
      throw new IllegalStateException("Already a codec registered with name " + codec.codecName());
    }
    userCodecMap.put(codec.codecName(), codec);
  }

  public void unregisterCodec(String name) {
    Objects.requireNonNull(name);
    userCodecMap.remove(name);
  }

  public <T> void registerDefaultCodec(Class<T> clazz, RabbitMQMessageCodec<T> codec) {
    Objects.requireNonNull(clazz);
    Objects.requireNonNull(codec, "codec");
    if (defaultCodecMap.containsKey(clazz)) {
      throw new IllegalStateException("Already a default codec registered for class " + clazz);
    }
    defaultCodecMap.put(clazz, codec);
  }

  public void unregisterDefaultCodec(Class clazz) {
    Objects.requireNonNull(clazz);
    RabbitMQMessageCodec codec = defaultCodecMap.remove(clazz);
  }

}
