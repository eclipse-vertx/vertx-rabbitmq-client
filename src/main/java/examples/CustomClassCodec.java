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
package examples;

import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.*;

/**
 *
 * @author jtalbut
 */
public class CustomClassCodec implements RabbitMQMessageCodec<CustomClass> {

  public static final String NAME = "customClass";
  
  @Override
  public String codecName() {
    return NAME;
  }

  @Override
  public byte[] encodeToBytes(CustomClass value) {
    Buffer buffer = Buffer.buffer();
    buffer.appendLong(value.getId());
    buffer.appendInt(value.getTitle().length());
    buffer.appendString(value.getTitle());
    buffer.appendDouble(value.getDuration());
    return buffer.getBytes();
  }

  @Override
  public CustomClass decodeFromBytes(byte[] data) {
    Buffer buffer = Buffer.buffer(data);
    Long id = buffer.getLong(0);
    int titleLength = buffer.getInt(Long.BYTES);
    String title = buffer.getString(Long.BYTES + Integer.BYTES, Long.BYTES + Integer.BYTES + titleLength);
    Double duration = buffer.getDouble(Long.BYTES + Integer.BYTES + titleLength);
    return new CustomClass(id, title, duration);
  }

  @Override
  public String getContentType() {
    return "application/x-custom-class";
  }

  @Override
  public String getContentEncoding() {
    return null;
  }
  
}
