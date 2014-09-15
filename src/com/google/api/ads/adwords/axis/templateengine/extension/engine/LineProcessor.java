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

import com.google.api.ads.adwords.axis.v201402.cm.ApiError;
import com.google.api.ads.adwords.axis.v201402.cm.ApiException;
import com.google.api.ads.adwords.axis.v201402.cm.AuthenticationError;
import com.google.api.ads.adwords.axis.v201402.cm.PolicyViolationError;
import com.google.api.ads.adwords.axis.v201402.cm.RateExceededError;

import com.google.api.ads.adwords.axis.templateengine.extension.adwordsintegration.AWAPI;
import com.google.api.ads.adwords.axis.templateengine.extension.plugins.*;
import com.google.api.ads.adwords.axis.templateengine.extension.engine.LineProcessor;
import com.google.api.ads.adwords.axis.templateengine.extension.shared.ConstantsIF;

import java.util.ArrayList;

/** 
 * Manages the lines given in the CSV file, based on the first String within it
 * 
 * @author Mark Bowyer
 * @author Nazmul Idris
 * @version 2.0
 * @since 8/12/13 13:25
 */

public class LineProcessor implements ConstantsIF {
  /** if this bit is set, then just retry processing the current line */
  public boolean retry = false;

  public String version = "NoVersion";

  /**
   * this is populated in case of successful processing of the data in the current
   * line being processed by the {@link #linePlugin}
   */
  public String successMessage = "N/A";
  /** this is populated in case of error - it holds the summary */
  public String errorSummaryMessage = "N/A";
  /** this is populated in case of error - it holds the error details */
  public String errorCauseMessage = "N/A";

  public ArrayList<String> listOfErrors;
  public ArrayList<String> listOfSuccesses;

  /** This allows us to generate a new CSV file of all the lines that failed, with why at the end,
   *  for easy fix-and-relaunch processing.
   */
  public ArrayList<String> fixupLines;
  public ArrayList<String> headerLine;
  public String lines[][] = new String[MAX_OPERATIONS][];

  /** These are the currently supported Plugin types for this Package.  Add new Plugin names here:
   */
  public enum ProcessorTypes {TEST, FEEDDELETE, CAMPAIGNMIGRATION};

    private BasePlugin linePlugin = null;
    public AWAPI awapi = null;
    
    public BasePlugin getLinePlugin() {
      return linePlugin;
    }

    /**
     * process the given line, in the form of a String[] where each array element is a field value,
     * and the field index is provided by the header row in the CSV file {@link
     * BasePlugin#setup(String[], LineProcessor)} (String[])}
     * 
     * @param processorType which Plugin to use to read the CSV file lines
     * @param version the Version String given as the second word of the file
     */
    public LineProcessor(ProcessorTypes processorType, String version) {
      switch (processorType) {
      case FEEDDELETE:
        linePlugin = new FeedDeletingPlugin();
        break;
      case CAMPAIGNMIGRATION:
        linePlugin = new CampaignMigration();
        break;
      default:
        throw new NullPointerException();
      }
      this.version = version;

      // create a new AdWords client object for the given client account
      try {
        awapi = new AWAPI(this);
        if (awapi == null) {
          System.err.println("AWAPI error.");
          throw new NullPointerException();
        }
      } catch (Exception e) {
        errorSummaryMessage = "Could not construct an AdWords user object given the credentials";
        errorCauseMessage = e.toString();
      }
    }

    /** Clear the local lines[][] ready for creating a new one. */
    public void clearLines() {
      for (int i = 0; i < MAX_OPERATIONS; i++) {
        lines[i] = null;
      }
    }

    /** Check that these two lines are targeting close enough AdWords targets to include
     * them in the same mutate() call.  This is based on the value of idColumns in the
     * selected linePlugin, which sets how many columns from the left should match.
     * So sort your CSV file first, to get the best performance here.
     * 
     * @param lastLine the previous elements we're comparing against
     * @param line the current elements we're checking match
     */
    public boolean sameTarget(String[] lastLine, String[] line) {
      BasePlugin linePlugin = getLinePlugin();
      if (lastLine == null || line == null) {
        return true;
      }
      if (line.length < linePlugin.idColumns) {
        System.err.println(
            String.format("Empty lines in input CSV file! '%d' < '%d'.",
                line.length, linePlugin.idColumns)
            );
        return false;
      }
      for (int i = 0; i < linePlugin.idColumns; i++) {
        if (!lastLine[i].equals(line[i])) {
          return false;
        }
      }
      return true;
    }

    /** Process Authentication Errors 
     * @param awapiex the AdWords API Exception to process
     */
    public void processAuthenticationError(ApiException awapiex) {
      ApiError[] errorRay = awapiex.getErrors();
      for (ApiError apiError : errorRay) {
        if (apiError instanceof AuthenticationError) {
          // AuthenticationError
          System.out.println(
              String.format("AdWords API AuthenticationError was thrown: '%s'",
                  ((AuthenticationError) apiError).getReason().toString())
              );
          slowDownNow("Captcha hit!!!");
        } else {
          System.err.println(awapiex.getMessage());
        }
      }
      errorSummaryMessage = "AdWords API Exception for line #" + linePlugin.currentLineNumber;
      errorCauseMessage = awapiex.toString();
      listOfErrors.add(errorCauseMessage);
    }

    /** Process Rate Exceeded Errors 
     * @param awapiex the AdWords API Exception to process
     */
    public void processRateExceededError(ApiException apiException) {
      ApiError[] errorRay = apiException.getErrors();
      for (ApiError apiError : errorRay) {
        if (apiError instanceof RateExceededError) {
          // Ensure we were called for the right reason: RateExceededError
          slowDownNow("Rate limit hit!!!", 
              ((RateExceededError) apiError).getRetryAfterSeconds() * 1000);
        } else {
          System.err.println(apiError);
          // some other kind of ApiError
          System.err.println("processRateExceeded called with a " 
              + apiError.getApiErrorType());
        }
      }
      errorSummaryMessage = "AdWords API Exception for line #" + linePlugin.currentLineNumber;
      try {
        ApiError apiError = errorRay[0];
        errorCauseMessage =
            String.format(
                "Error: '%s', Problematic-parameter: '%s', Problematic-value: '%s'",
                apiError.getErrorString(), apiError.getFieldPath(), apiError.getTrigger());

      } catch (Exception e) {
        errorCauseMessage = apiException.toString();
      } finally {
        listOfErrors.add(errorCauseMessage);
      }
    }

    /** slows the program down by waiting for {@link ConstantsIF#SLOW_DOWN_TIMEOUT_MS} 
     * @param msg the reason for slowing down
     */
    public void slowDownNow(String msg) {
      slowDownNow(msg, (int) ConstantsIF.SLOW_DOWN_TIMEOUT_MS);
    }

    /** Slows down by provided int value MS 
     * @param msg the reson for slowing down
     * @param retryAfterMS a non-default period to wait
     * */
    public void slowDownNow(String msg, int retryAfterMS) {
      try {
        System.out.printf("SLOWING DOWN!!! Reason: %s%n", msg);
        retry = true;
        Thread.currentThread();
        Thread.sleep(retryAfterMS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    private int programExecutionPauseCounter = 0;

    /** voluntary wait logic implementation */
    public void voluntaryWaitLogic() {
      // long pause
      if (++programExecutionPauseCounter > PROGRAM_PAUSE_AFTER_PROCESSING_RECORD_BLOCK) {
        programExecutionPauseCounter = 0;
        try {
          System.out.printf("Voluntarily pausing program after processing %d lines...%n",
              PROGRAM_PAUSE_AFTER_PROCESSING_RECORD_BLOCK);
          Thread.currentThread();
          Thread.sleep(PROGRAM_PAUSE_LENGTH_MS);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    /** retry logic implementation */
    public void retryLogic(long currentLineNumber) {
      if (retry) {
        System.out.printf("Retrying processing line #%d ... %n", currentLineNumber);
      }
      retry = false;
    }

    /** in case process fails, this will contain the error message for the output error log file */
    public String getErrorMessage() {
      return String.format("Error: %s\nCause: %s\n\n", errorSummaryMessage, errorCauseMessage);
    }

    /** if process fails, we want a short summary message why for this line in the FixUp file */
    public String getFixUpMessage() {
      int comma = errorCauseMessage.indexOf(",");
      if (comma < 0) {
        return errorCauseMessage;
      } else {
        return errorCauseMessage.substring(0, comma);
      }
    }

    /**
     * in case process succeeds, this will contain the success message for the output success log
     * file
     */
    public String getSuccessMessage() {
      return String.format("%s\n\n", successMessage);
    }

    /** Build listOfSuccesses and handle other events as necessary on success 
     * @param line the line of the CSV file this happened on
     * @param successMessage the message to log/report
     */
    public boolean handleSuccess(long line, String successMessage) {
      listOfSuccesses.add(String.format("Line: '%d': '%s'.", 
          (linePlugin.lastBlockLineNumber + (line / linePlugin.opsPerLine) + 1), successMessage));
      return true;
    }

    /** Handle failure, and build listOfErrors and the FixUp file 
     * @param line the line of the CSV file this happened on
     * @param errorMessage the message to log/report
     */
    public boolean handleFailure(int line, String errorMessage) {
      listOfErrors.add(
          String.format("Line: '%d': '%s'.", 
              (linePlugin.lastBlockLineNumber + (line / linePlugin.opsPerLine) + 1), errorMessage));
      String data = "";
      int fixline = line / linePlugin.opsPerLine;
      if (lines[fixline] != null) {
        boolean first = true;
        for (int i = 0; i < lines[fixline].length; i++) {
          data += ((first) ? "" : ",") + lines[fixline][i];
          first = false;
        }
        data += "," + errorMessage;
        fixupLines.add(data); 
      }
      return true;
    }

    /** Handle Policy Violation Errors 
     * @param line the line of the CSV file this happened on
     * @param type which type of violation happened
     * @param policyViolationError the actual error object
     */
    public void handlePolicyViolation(int line, String type, 
        PolicyViolationError policyViolationError) {
      handleFailure(line, String.format("%s violated %s policy \"%s\".", type,
          policyViolationError.getIsExemptable() ? "exemptable" : "non-exemptable",
              policyViolationError.getExternalPolicyName()));
    }

}
