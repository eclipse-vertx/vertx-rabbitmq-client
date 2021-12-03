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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 *
 * @author jtalbut
 */
public class CustomClassCodec implements MessageCodec<CustomClass, CustomClass> {

  @Override
  public void encodeToWire(Buffer buffer, CustomClass s) {
    buffer.appendLong(s.getId());
    buffer.appendInt(s.getTitle().length());
    buffer.appendString(s.getTitle());
    buffer.appendDouble(s.getDuration());
  }

  @Override
  public CustomClass decodeFromWire(int i, Buffer buffer) {
    Long id = buffer.getLong(0);
    int titleLength = buffer.getInt(Long.BYTES);
    String title = buffer.getString(Long.BYTES + Integer.BYTES, Long.BYTES + Integer.BYTES + titleLength);
    Double duration = buffer.getDouble(Long.BYTES + Integer.BYTES + titleLength);
    return new CustomClass(id, title, duration);
  }

  /**
   * This isn't the optimal way to do this (the copy constructor would be more efficient) but it does test the codec better.
   * @param s The object to the transformed.
   * @return The object transformed, which should be the same as the original object.
   */
  @Override
  public CustomClass transform(CustomClass s) {
    Buffer buffer = Buffer.buffer();
    encodeToWire(buffer, s);
    return decodeFromWire(0, buffer);
  }

  @Override
  public String name() {
    return "CustomClassMessageCodec";
  }

  @Override
  public byte systemCodecID() {
    return -1;
  }
  
}
