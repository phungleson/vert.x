package org.vertx.java.core;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 * Vert.x only throws instances of VertxException which is a RuntimeException.
 * Vert.x hates Java checked exceptions and doesn't want to pollute it's API with them.
 * You can catch them if you like, but it's recommended to let them bubble up to the Vert.x platform
 * which will, cause the verticle to be redeployed.
 */
public class VertxException extends RuntimeException {

  public VertxException(String message) {
    super(message);
  }

  public VertxException(String message, Throwable cause) {
    super(message, cause);
  }

  public VertxException(Throwable cause) {
    super(cause);
  }
}
