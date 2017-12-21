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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.MatchResult;

import org.apache.ibatis.migration.Change;
import org.apache.ibatis.migration.FileMigrationLoader;
import org.apache.ibatis.migration.MigrationException;
import org.apache.ibatis.migration.MigrationReader;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class GitMigrationLoader extends FileMigrationLoader {
  public GitMigrationLoader(File scriptsDir, String charset, Properties variables) {
    super(scriptsDir, charset, variables);
  }

  @Override
  public Reader getScriptReader(Change change, boolean undo) {
    String hash = null;
    String path = null;
    Reader reader = super.getScriptReader(change, undo);
    try (Scanner scanner = new Scanner(reader)) {
      while (scanner.hasNextLine()) {
        if (scanner.findInLine("commit:([0-9a-f]+?):(.*)") != null) {
          MatchResult match = scanner.match();
          hash = match.group(1);
          path = match.group(2);
          break;
        }
        scanner.nextLine();
      }
      if (hash == null) {
        // Assumed to be a plain SQL script.
        return super.getScriptReader(change, undo);
      }
    }
    System.out.println("Read SQL from file '" + path + "' of commit '" + hash + "'");
    try {
      PipedInputStream in = new PipedInputStream();
      PipedOutputStream out = new PipedOutputStream(in);
      new GitFileReader(hash, path, out).start();
      if (undo) {
        String undoTag = "-- //@UNDO\n";
        InputStream undoIn = new SequenceInputStream(new ByteArrayInputStream(undoTag.getBytes(charset)), in);
        return new MigrationReader(undoIn, charset, undo, variables);
      } else {
        return new MigrationReader(in, charset, undo, variables);
      }
    } catch (IOException e) {
      throw new MigrationException("Failed to create PipedOutputStream.", e);
    }
  }

  class GitFileReader extends Thread {
    private final String hash;

    private final String path;

    private final OutputStream out;

    public GitFileReader(String hash, String path, OutputStream out) {
      super();
      this.hash = hash;
      this.path = path;
      this.out = out;
    }

    /**
     * @author Dominik Stadler
     * @see https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/api/ReadFileFromCommit.java
     */
    @Override
    public void run() {
      try (Repository repository = new FileRepositoryBuilder().readEnvironment().findGitDir().build()) {
        ObjectId commitId = repository.resolve(hash);
        try (RevWalk revWalk = new RevWalk(repository)) {
          RevCommit commit = revWalk.parseCommit(commitId);
          RevTree tree = commit.getTree();
          try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            if (!treeWalk.next()) {
              throw new IllegalStateException("Did not find expected file " + path);
            }
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);
            loader.copyTo(out);
          }
          revWalk.dispose();
        }
      } catch (IOException e) {
        throw new MigrationException("Error reading the content of '" + path + "' revision '" + hash + "'", e);
      } finally {
        try {
          out.close();
        } catch (IOException e) {
        }
      }
    }
  }
}
