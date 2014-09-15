package com.google.api.ads.adwords.axis.templateengine.extension.engine;

//Copyright 2012 Google Inc. All Rights Reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

import com.google.api.ads.adwords.axis.templateengine.extension.engine.LineProcessor;
import com.google.api.ads.adwords.axis.templateengine.extension.shared.ConstantsIF;
import com.google.api.ads.adwords.axis.templateengine.extension.shared.FileUtils;

import au.com.bytecode.opencsv.*;

import java.io.*;
import java.util.*;

/**
 * This Class allows the calling of the rest of this Package to make use of multiple Operations per
 * Mutate call, rather than doing each line of the CSV file per mutate request.  Both are supported,
 * but this is cheaper and more efficient.
 * 
 * @author markbowyer
 *
 */
public class MultiBulkAdWordsLauncher implements ConstantsIF {

  // A holder for the lineProcessor instance created later, but used throughout.
  public static LineProcessor lineProcessor = null;

  public static void main(String[] args) {

    CSVReader reader = null;

    try {
      // read the CSV file
      reader = new CSVReader(
          new FileReader(
              FileUtils.getFile(SOURCE_DATA_FILE)));

      long previousRunSavedLineNumber = -1;

      if (FileUtils.existsFile(EXECUTION_CURSOR_FILE)) {
        previousRunSavedLineNumber = FileUtils.readLongFromFile(EXECUTION_CURSOR_FILE);
      } else {
        FileUtils.deleteFile(ERROR_LOG_FILE);
        FileUtils.deleteFile(SUCCESS_LOG_FILE);
        FileUtils.deleteFile(FIX_UP_FILE);
      }

      String[] line = reader.readNext();

      //Select the LineProcessor plugin type based on the first String in the input CSV file.
      LineProcessor.ProcessorTypes processorType =
          LineProcessor.ProcessorTypes.valueOf(line[0]);
      String version = line[1];

      // Copy the first line to the FixUp file, ready for any broken lines.
      ArrayList<String> firstLine = new ArrayList<String>();
      firstLine.add(line[0] + "," + line[1]);
      FileUtils.write(firstLine, FIX_UP_FILE, true);

      // Set the plugin type, and pass in this file version
      lineProcessor = new LineProcessor(processorType, version);
      lineProcessor.listOfErrors = new ArrayList<String>();
      lineProcessor.listOfSuccesses = new ArrayList<String>();
      lineProcessor.headerLine = new ArrayList<String>();
      lineProcessor.fixupLines = new ArrayList<String>();

      long currentLineNumber = 2;  // Skip title and header lines for processing

      // read the 1st line to setup the parser (field mapping based on header row)
      lineProcessor.getLinePlugin().setup(line = reader.readNext(), lineProcessor);

      // Gather the header column titles, and copy them into the Fixup file.
      String headers = "";
      boolean first = true;
      for (int i = 0; i < line.length; i++) {
        headers += ((first) ? "" : ",") + line[i];
        first = false;
      }
      lineProcessor.headerLine.add(headers);
      FileUtils.write(lineProcessor.headerLine, FIX_UP_FILE, true);
      String lastLine[] = null;
      line = reader.readNext();

      do {
        int opIterator = 0;
        int lineIterator = 0;
        while (line != null 
            && (opIterator += lineProcessor.getLinePlugin().opsPerLine) < MAX_OPERATIONS
            && lineProcessor.sameTarget(lastLine, line)) {
          currentLineNumber++;
          lineProcessor.lines[lineIterator++] = line;
          lastLine = line;

          do {
            lineProcessor.retryLogic(currentLineNumber);
            // don't process lines if they have already been processed in previous run
            if (currentLineNumber > previousRunSavedLineNumber) {
              // actually process line numbers
              // voluntary wait logic...
              lineProcessor.voluntaryWaitLogic();

              if (!lineProcessor.getLinePlugin().setOperations(line, currentLineNumber)) {
                lineProcessor.handleFailure(0, "Failed reading CSV line " + currentLineNumber);
              } else {
                // We've used this line now, so get the next one
                line = reader.readNext();
                // update the execution cursor (marking processing of currentLineNumber/row)
                FileUtils.writeLongToFile(EXECUTION_CURSOR_FILE, currentLineNumber);
                // dump output to console
                System.out.println("Finished processing line # " + currentLineNumber);
              }
            }
          } while (lineProcessor.retry == true && !line.equals(""));
        }
        // We now have an Operations array in the Plugin waiting to be used, so call
        // mutate() on it to push them out to AdWords.
        if (lineProcessor.getLinePlugin().mutate()) {
          System.out.println("Success from Mutate block before line " + currentLineNumber);
        } else {
          System.out.println("Errors from Mutate block before line " + currentLineNumber);
        }
        lineProcessor.getLinePlugin().clearOperations();
        lineProcessor.clearLines();
        lastLine = line;
      } while (line != null);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      // Clean up when done...
      FileUtils.write(lineProcessor.listOfSuccesses, SUCCESS_LOG_FILE, true);
      FileUtils.write(lineProcessor.listOfErrors, ERROR_LOG_FILE, true);
      FileUtils.write(lineProcessor.fixupLines, FIX_UP_FILE, true);
      FileUtils.deleteFile(EXECUTION_CURSOR_FILE);
      System.out.println("success count: " + lineProcessor.listOfSuccesses.size());
      System.out.println("failure count: " + lineProcessor.listOfErrors.size());
      try {
        reader.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
