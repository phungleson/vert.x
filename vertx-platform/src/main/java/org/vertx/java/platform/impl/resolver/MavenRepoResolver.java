package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.Vertx;
import org.vertx.java.platform.impl.ModuleIdentifier;

/*
 * Copyright 2008-2011 Red Hat, Inc.
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
 */
public class MavenRepoResolver extends HttpRepoResolver {

  public MavenRepoResolver(Vertx vertx, String repoID) {
    super(vertx, repoID);
  }

  @Override
  public boolean getModule(String filename, ModuleIdentifier moduleIdentifier) {
    HttpResolution res = new MavenResolution(vertx, repoHost, repoPort, moduleIdentifier, filename, contentRoot);
    res.getModule();
    return res.waitResult();
  }

  public boolean isOldStyle() {
    return false;
  }

}
