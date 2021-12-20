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

import io.netty.util.CharsetUtil;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 *
 * @author jtalbut
 */
public class CustomStringCodec implements RabbitMQMessageCodec<String> {

  private final String name;
  private final String contentType;

  public CustomStringCodec() {
    this.name = "deflated-utf16";
    this.contentType = "text/plain";
  }  
  
  @Override
  public String codecName() {
    return name;
  }

  @Override
  public byte[] encodeToBytes(String value) {
    Deflater deflater = new Deflater();
    byte[] input = value.getBytes(CharsetUtil.UTF_16);
    deflater.setInput(input);
    deflater.finish();
    int upperboundonlength = input.length + ((input.length + 7) >> 3) + ((input.length + 63) >> 6) + 5;
    byte[] output = new byte[upperboundonlength];
    int bytes = deflater.deflate(output);
    deflater.end();
    return Arrays.copyOf(output, bytes);
  }

  @Override
  public String decodeFromBytes(byte[] data) {
    Inflater inflater = new Inflater();
    inflater.setInput(data);
    byte[] output = new byte[data.length * 2];
    try {
      int bytes = inflater.inflate(output);
      return new String(Arrays.copyOf(output, bytes), CharsetUtil.UTF_16);
    } catch(DataFormatException ex) {
      return "";
    }
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public String getContentEncoding() {
    return "deflate";
  }

}
