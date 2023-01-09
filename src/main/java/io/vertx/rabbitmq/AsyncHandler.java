/*
 * Copyright 2023 Eclipse.
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

import io.vertx.core.Future;

/**
 *  A generic event handler for asynchronous events.
 *  <p>
 * 
 * Callers of this interface will suspend processing until the returned Future completes.
 * <p>
 *
 * @param <E> The type of the parameter passed in to the handler.
 */
@FunctionalInterface
public interface AsyncHandler<E extends Object> {

  public Future<Void> handle(E e);
}
