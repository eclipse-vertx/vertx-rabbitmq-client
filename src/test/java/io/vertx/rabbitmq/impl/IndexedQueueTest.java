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
package io.vertx.rabbitmq.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 * @author jtalbut
 */
public class IndexedQueueTest {
  
  @Test
  public void testRemoveFirst() {
    IndexedQueue<String> q = new IndexedQueue<>();
    try {
      q.removeFirst();
      fail("Should have thrown");
    } catch(ArrayIndexOutOfBoundsException ex) {      
    }    
  }

  @Test
  public void testGet() {
  }

  @Test
  public void testSet() {
    IndexedQueue<String> q = new IndexedQueue<>();
    for (int i = 0; i < 1000; ++i) {
      q.set(i, Integer.toString(i));
    }
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), q.get(i));
    }
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), q.removeFirst());
      assertEquals(1000 - 1 - i, q.size());
    }
  }

  @Test
  public void testClear() {
    IndexedQueue<String> q = new IndexedQueue<>();
    for (int i = 0; i < 1000; ++i) {
      q.set(i, Integer.toString(i));
    }
    assertEquals(1000, q.size());
    q.clear();
    assertEquals(0, q.size());
    for (int i = 0; i < 1000; ++i) {
      q.set(i, Integer.toString(i));
    }
    assertEquals(1000, q.size());
  }
  
  @Test
  public void testIterator() {
    IndexedQueue<String> q = new IndexedQueue<>();
    for (int i = 0; i < 1000; ++i) {
      q.set(i, Integer.toString(i));
    }
    int i = 0;
    for (String value : q) {
      assertEquals(Integer.toString(i++), q.removeFirst());
    }
  }

}
