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
