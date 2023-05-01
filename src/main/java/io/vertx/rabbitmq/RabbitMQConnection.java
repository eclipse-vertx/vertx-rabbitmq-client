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
package io.vertx.rabbitmq;

import io.vertx.core.Future;

/**
 *
 * @author jtalbut
 */
public interface RabbitMQConnection {
  
  /**
   * Create a new {@link RabbitMQChannelBuilder} instance for this connection.
   * <p>
   * @return a new {@link RabbitMQChannelBuilder} instance.
   */
  RabbitMQChannelBuilder createChannelBuilder();
  
  /**
   * Returns a strictly increasing identifier of the underlying connection (a number that is incremented each time the connection is reconnected).
   * @return an identifier of the underlying connection.
   */
  long getConnectionInstance();
  
  String getConnectionName();
  
  int getConfiguredReconnectAttempts();
  
  Future<Void> close();
  
  Future<Void> close(int closeCode, String closeMessage, int timeout);

  boolean isClosed();
}
