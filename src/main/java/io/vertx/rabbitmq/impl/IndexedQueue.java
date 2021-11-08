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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 *
 * @author jtalbut
 */
public class IndexedQueue<T> implements Iterable<T> {
  
  private static final int BLOCK_SIZE = 128;
  
  private final List<T[]> blocks = new ArrayList<>();
  private int head = 0;
  private int tail = -1;
  
  public T removeFirst() {
    T value = get(head);
    if (++head == BLOCK_SIZE) {
      blocks.remove(0);
      head -= BLOCK_SIZE;
      tail -= BLOCK_SIZE;
    }
    return value;
  }
  public T get(int index) {    
    if (index > tail) {
      throw new ArrayIndexOutOfBoundsException("Attempt to access beyond end of queue " + index + " > " + tail);
    }
    return blocks.get(index / BLOCK_SIZE)[index % BLOCK_SIZE];
  }
  public void set(int index, T value) {
    if (index + 1 > BLOCK_SIZE * blocks.size()) {
      blocks.add((T[]) new Object[BLOCK_SIZE]);
    }
    if (index + 1 > BLOCK_SIZE * blocks.size()) {
      throw new ArrayIndexOutOfBoundsException("Excessive queue growth, attempting to set too far beyond end of queue, probably bad");
    }
    blocks.get(index / BLOCK_SIZE)[index % BLOCK_SIZE] = value;
    if (index > tail) {
      tail = index;
    }
  }
  private class IndexedQueueIterator implements Iterator<T> {

    private int index = head - 1;
    
    @Override
    public boolean hasNext() {
      return index < tail;
    }

    @Override
    public T next() {
      return get(++index);
    }

  }
  @Override
  public Iterator<T> iterator() {
    return new IndexedQueueIterator();
  }
  public int size() {
    return 1 + tail - head;
  }
  public void clear() {
    tail = -1;
    head = 0;
    blocks.clear();
  }

}
