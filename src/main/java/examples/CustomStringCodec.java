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

import io.netty.util.CharsetUtil;
import io.vertx.rabbitmq.*;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 *
 * @author jtalbut
 */
public class CustomStringCodec implements RabbitMQMessageCodec<String> {

  public static final String NAME = "deflated-utf16";
  private static final String CONTENT_TYPE = "text/plain";

  @Override
  public String codecName() {
    return NAME;
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
    return CONTENT_TYPE;
  }

  @Override
  public String getContentEncoding() {
    return "deflate";
  }

}
