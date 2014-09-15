package com.google.api.ads.adwords.axis.templateengine.extension.shared;

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

import java.io.*;
import java.util.*;

/**
 * Makes it easier to generate File objects from String paths.
 *
 * @author Nazmul Idris
 * @version 1.0
 * @since 4/12/12, 4:51 PM
 */
public class FileUtils implements ConstantsIF {

  enum Root {
    UserHomeDirectory("user.home"), CurrentWorkingDirectory("user.dir");
    private String systemPropertyName;

    Root(String s) {
      systemPropertyName = s;
    }

    public String getSystemProperty() {
      return System.getProperty(systemPropertyName);
    }
  }

  /** 
   * return File object, for relative path from home directory 
   * @param path the file path to build the file at
   * @return the File created
   */
  public static File getFile(String path) {

    StringBuilder sb = new StringBuilder();

    sb.append(ROOT_PATH_TYPE.getSystemProperty()).append(File.separator);

    path = ROOTPATH + path;

    if (!path.startsWith(File.separator)) {
      path = File.separator + path;
    }
    sb.append(path);
    return new File(sb.toString());
  }

  /** 
   * Delete the supplied file 
   * @param filePath the file to delete
   */
  public static void deleteFile(String filePath) {
    File file = getFile(filePath);
    if (file.exists()) {
      file.delete();
    }
  }

  /**
   * Write out the supplied messages to the given file
   * @param listOfMessages list of strings that need to be written to a file
   * @param outputFilePath path of output file
   * @param appendMode     true means append the output to the file, false means overwrite the file
   *                       every time.
   */
  public static void write(List<String> listOfMessages, String outputFilePath, boolean appendMode) {

    try {

      File outputFile = getFile(outputFilePath);

      // overwrite mode just replaces an existing file with this new one
      FileWriter fw = new FileWriter(outputFile, appendMode);
      for (String message : listOfMessages) {
        fw.write(message);
        fw.write("\n");
      }

      fw.flush();
      fw.close();

    } catch (Exception e) {
      for (String message : listOfMessages) {
        System.out.println(message);
      }
    }
  }

  /** 
   * Check if file exists 
   * @param file to check
   * @return if it does or not
   */
  public static boolean existsFile(String file) {
    return getFile(file).exists();
  }

  /** 
   * Write a long to the provided file 
   * @param executionCursorFile the file to write to
   * @param value the number to write
   * @throws IOException 
   */
  public static void writeLongToFile(String executionCursorFile, long value) throws IOException {
    PrintWriter out = null;
    try {
      File f = getFile(executionCursorFile);
      out = new PrintWriter(new FileWriter(f));
      out.println(Long.toString(value));
    } finally {
      if (out != null) {
        out.close();
      }
    }
  }

  /** 
   * Read a long from a file 
   * @param executionCursorFile the file to read from
   * @return the value in that file
   * @throws IOException 
   */
  public static long readLongFromFile(String executionCursorFile) throws IOException {
    BufferedReader in = null;
    try {
      String line;
      in = new BufferedReader(new FileReader(getFile(executionCursorFile)));
      line = in.readLine();
      return Long.parseLong(line);
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }

  /** 
   * A main() for testing purposes only
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    System.out.println("writing file...");
    writeLongToFile(EXECUTION_CURSOR_FILE, 100);
    System.out.println("reading file...");
    long line = readLongFromFile(EXECUTION_CURSOR_FILE);
    System.out.println("read the following value: " + line);
  }

}
