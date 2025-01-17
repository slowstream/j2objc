/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.j2objc.pipeline;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.google.common.io.Files;
import com.google.devtools.j2objc.Options;
import com.google.devtools.j2objc.file.InputFile;
import com.google.devtools.j2objc.file.RegularInputFile;
import com.google.devtools.j2objc.util.ErrorUtil;
import com.google.devtools.j2objc.util.Parser;

/**
 * Preprocesses each Java file in the batch.
 */
public class InputFilePreprocessor {

  private static final Logger logger = Logger.getLogger(InputFilePreprocessor.class.getName());

  private final Parser parser;
  private File strippedSourcesDir;
  private final Options options;

  public InputFilePreprocessor(Parser parser) {
    this.parser = parser;
    this.options = parser.options();
  }

  public void processInputs(Iterable<ProcessingContext> inputs) {
    for (ProcessingContext input : inputs) {
      if (input.getFile().getUnitName().endsWith(".java")) {
        processInput(input);
      }
    }
  }

  private void processInput(ProcessingContext input) {
    try {
      if (!input.getFile().getUnitName().endsWith("package-info.java")) {
        processRegularSource(input);
      }
    } catch (IOException e) {
      ErrorUtil.error(e.getMessage());
    }
  }

  public File getStrippedSourcesDir() {
    return strippedSourcesDir;
  }

  private File getCreatedStrippedSourcesDir() {
    if (strippedSourcesDir == null) {
      strippedSourcesDir = Files.createTempDir();
      logger.finest("Created temp dir for stripped sources: " + strippedSourcesDir.getPath());
    }
    return strippedSourcesDir;
  }

  private void processRegularSource(ProcessingContext input) throws IOException {
    InputFile file = input.getFile();
    String source = options.fileUtil().readFile(file);
    boolean shouldMapHeaders = options.getHeaderMap().useSourceDirectories();
    boolean doIncompatibleStripping = source.contains("J2ObjCIncompatible");
    if (!(shouldMapHeaders || doIncompatibleStripping)) {
      // No need to parse.
      return;
    }
    Parser.ParseResult parseResult = parser.parseWithoutBindings(file, source);
    if (parseResult == null) {
      // The parser found and reported one or more errors.
      return;
    }
    String qualifiedName = parseResult.mainTypeName();
    if (shouldMapHeaders) {
      options.getHeaderMap().put(qualifiedName, input.getGenerationUnit().getOutputPath() + ".h");
    }
    if (false && doIncompatibleStripping) {
      parseResult.stripIncompatibleSource();
      File strippedDir = getCreatedStrippedSourcesDir();
      String relativePath = qualifiedName.replace('.', File.separatorChar) + ".java";
      File strippedFile = new File(strippedDir, relativePath);
      Files.createParentDirs(strippedFile);
      Files.asCharSink(strippedFile, options.fileUtil().getCharset())
          .write(parseResult.getSource());
      input.setFile(new RegularInputFile(strippedFile.getPath(), relativePath));
    }
  }
}
