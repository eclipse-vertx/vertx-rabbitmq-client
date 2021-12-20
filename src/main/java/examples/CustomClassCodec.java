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
package examples;

import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.*;

/**
 *
 * @author jtalbut
 */
public class CustomClassCodec implements RabbitMQMessageCodec<CustomClass> {

  @Override
  public String codecName() {
    return "customClass";
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
