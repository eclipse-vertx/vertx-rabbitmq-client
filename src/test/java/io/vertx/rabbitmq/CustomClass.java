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
