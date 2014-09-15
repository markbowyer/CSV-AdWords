package com.google.api.ads.adwords.axis.templateengine.extension.plugins;

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

/** The Abstract Class for all the Plugins in the Package. */
public abstract class BasePlugin {
  
  public static LineProcessor lineProcessor = null;

  /** current line number (from data CSV file) that's used for logging */
  public long currentLineNumber = 0;

  /** The line number of the last line in the last block operated on */
  public long lastBlockLineNumber = 2;

  /** how many columns of this table identify this as the same as previous lines? */

  public int idColumns = 1;

  /** how many operations are generated per input line? */

  public int opsPerLine = 1;

  /** 
   * The old way of doing the creation of operations and then mutate with them, still here for historic reasons
   * @param lineRay the current line of entries to be processed
   * @param currentLineNumber the line number from the CSV file
   * @return did we succeed?
   */
  public abstract boolean process(String[] lineRay, long currentLineNumber);

  /**
   * Create an array of this kind of Operations, ready for a Service mutate call.
   * @param lineRay the incoming line of entries from the CSV file
   * @param currentLineNumber the line number of the CSV file it came from
   * @return did we succeed
   */
  public abstract boolean setOperations(String[] lineRay, long currentLineNumber);

  /**
   * Clear out the array of Operations
   */
  public abstract void clearOperations();

  /**
   * Call the mutate method on the correct Service(s) with the Operation(s) arrays as built
   * @return did we succeed
   */
  public abstract boolean mutate();

  /**
   * The initial setup for this Plugin
   * @param lineRay the columns we're going to be dealing with
   * @param lineProcessor the base LIneProcesser object to use
   */
  public abstract void setup(String[] lineRay, LineProcessor lineProcessor);

}
