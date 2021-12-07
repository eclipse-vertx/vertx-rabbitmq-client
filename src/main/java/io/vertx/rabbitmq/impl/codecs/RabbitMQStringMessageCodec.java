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

import io.netty.util.CharsetUtil;
import io.vertx.rabbitmq.RabbitMQMessageCodec;

/**
 *
 * @author jtalbut
 */
public class RabbitMQStringMessageCodec implements RabbitMQMessageCodec<String> {

  private final String name;
  private final String contentType;

  public RabbitMQStringMessageCodec() {
    this.name = "string";
    this.contentType = "text/plain";
  }
  
  public RabbitMQStringMessageCodec(String name, String contentType) {
    this.name = name;
    this.contentType = contentType;
  }
  
  @Override
  public String codecName() {
    return name;
  }

  @Override
  public byte[] encodeToBytes(String value) {
    return value.getBytes(CharsetUtil.UTF_8);
  }

  @Override
  public String decodeFromBytes(byte[] data) {
    return new String(data, CharsetUtil.UTF_8);
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public String getContentEncoding() {
    return null;
  }

}
