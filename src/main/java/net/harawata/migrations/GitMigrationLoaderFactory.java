/**
 *    Copyright 2010-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.harawata.migrations;

import org.apache.ibatis.migration.Environment;
import org.apache.ibatis.migration.FileMigrationLoaderFactory;
import org.apache.ibatis.migration.MigrationLoader;
import org.apache.ibatis.migration.options.SelectedPaths;

public class GitMigrationLoaderFactory implements FileMigrationLoaderFactory {
  @Override
  public MigrationLoader create(SelectedPaths paths, Environment environment) {
    return new GitMigrationLoader(paths.getScriptPath(), environment.getScriptCharset(),
        environment.getVariables());
  }
}
