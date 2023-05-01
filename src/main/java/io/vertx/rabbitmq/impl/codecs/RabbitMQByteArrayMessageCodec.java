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

import io.vertx.rabbitmq.RabbitMQMessageCodec;

/**
 *
 * @author jtalbut
 */
public class RabbitMQByteArrayMessageCodec implements RabbitMQMessageCodec<byte[]> {

  private final String name;

  public RabbitMQByteArrayMessageCodec() {
    this.name = "byte-array";
  }
  
  @Override
  public String codecName() {
    return name;
  }

  @Override
  public byte[] encodeToBytes(byte[] value) {
    return value;
  }

  @Override
  public byte[] decodeFromBytes(byte[] data) {
    return data;
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public String getContentEncoding() {
    return null;
  }

}
