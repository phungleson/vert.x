package org.vertx.java.platform.impl;

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
 */
public class ModuleIdentifier {

  private static final String SEPARATOR = "~";
  private static final String SPLIT_REGEX = "\\" + SEPARATOR;

  private final String owner;
  private final String name;
  private final String version;
  private final String stringForm;

  private static final String LEGAL = "[A-Za-z0-9!£$()-_+=@~;,]+";

  public ModuleIdentifier(String stringForm) {
    if (stringForm == null) {
      throw new NullPointerException("Module identifier cannot be null");
    }
    this.stringForm = stringForm;
    String[] parts = stringForm.split(SPLIT_REGEX);
    if (parts.length != 3) {
      throw createException("Should be of form owner" + SEPARATOR + "name" + SEPARATOR + "version");
    }
    if (parts[0].isEmpty()) {
      throw createException("owner should not be empty");
    }
    if (parts[1].isEmpty()) {
      throw createException("name should not be empty");
    }
    if (parts[2].isEmpty()) {
      throw createException("version should not be empty");
    }
    owner = parts[0];
    checkIllegalChars(owner);
    name = parts[1];
    checkIllegalChars(name);
    version = parts[2];
    checkIllegalChars(version);
  }

  private static void checkIllegalChars(String str) {
    if (!str.matches(LEGAL)) {
      throw new IllegalArgumentException("Module identifier: " + str + " contains illegal characters");
    }
  }

  private IllegalArgumentException createException(String msg) {
    return new IllegalArgumentException("Invalid module identifier: " + stringForm + ". " + msg);
  }

  public String toString() {
    return stringForm;
  }

  public String getOwner() {
    return owner;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public static ModuleIdentifier createInternalModIDForVerticle(String depName) {
    return new ModuleIdentifier("__vertx" + SEPARATOR + depName + SEPARATOR + "__vertx");
  }

}
