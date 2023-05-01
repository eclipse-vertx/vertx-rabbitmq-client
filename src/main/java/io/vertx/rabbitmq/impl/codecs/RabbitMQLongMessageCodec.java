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
public class RabbitMQLongMessageCodec implements RabbitMQMessageCodec<Long> {

  private final String name;
  private final String contentType;

  public RabbitMQLongMessageCodec() {
    this.name = "long";
    this.contentType = "application/x-long";
  }
  
  @Override
  public String codecName() {
    return name;
  }

  @Override
  public byte[] encodeToBytes(Long value) {
    return new byte[] {
      (byte) value.longValue(),
      (byte) (value >> 8),
      (byte) (value >> 16),
      (byte) (value >> 24),
      (byte) (value >> 32),
      (byte) (value >> 40),
      (byte) (value >> 48),
      (byte) (value >> 56)
    };
  }

  @Override
  public Long decodeFromBytes(byte[] data) {
    if (data.length != 8) {
      return null;
    }
    return 
            ((long) data[7] << 56)
            | ((long) data[6] & 0xff) << 48
            | ((long) data[5] & 0xff) << 40
            | ((long) data[4] & 0xff) << 32
            | ((long) data[3] & 0xff) << 24
            | ((long) data[2] & 0xff) << 16
            | ((long) data[1] & 0xff) << 8
            | ((long) data[0] & 0xff)
       ;
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
