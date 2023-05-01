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

import com.rabbitmq.client.Channel;

/**
 * Simple Handler class that accepts a synchronous Channel and returns a value.
 * 
 * The handler may also throw an Exception.
 * 
 * @author jtalbut
 * @param <T> The type of object that the handler method returns.
 */
public interface ChannelFunction<T> {
  
    /**
     * @param channel
     * @return
     * @throws Exception 
     */
    T handle(Channel channel) throws Exception;
}
