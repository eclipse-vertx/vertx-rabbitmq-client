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

/**
 *
 * @author jtalbut
 */
public class CustomClass {
  
  private final long id;
  private final String title;
  private final double duration;

  public CustomClass(long id, String title, double duration) {
    this.id = id;
    this.title = title;
    this.duration = duration;
  }

  public CustomClass(CustomClass other) {
    this.id = other.id;
    this.title = other.title;
    this.duration = other.duration;
  }

  public long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public double getDuration() {
    return duration;
  }

  @Override
  public String toString() {
    return "CustomClass{" + "id=" + id + ", title=" + title + ", duration=" + duration + '}';
  }
  
}
