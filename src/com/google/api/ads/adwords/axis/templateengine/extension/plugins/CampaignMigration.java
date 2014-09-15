package com.google.api.ads.adwords.axis.templateengine.extension.plugins;

//Copyright 2012 Google Inc. All Rights Reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

import com.google.api.ads.adwords.axis.v201402.cm.AdvertisingChannelType;
import com.google.api.ads.adwords.axis.v201402.cm.ApiError;
import com.google.api.ads.adwords.axis.v201402.cm.ApiException;
import com.google.api.ads.adwords.axis.v201402.cm.Campaign;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignOperation;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignReturnValue;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.NetworkSetting;
import com.google.api.ads.adwords.axis.v201402.cm.Operator;
import com.google.api.ads.adwords.axis.v201402.cm.PolicyViolationError;
import com.google.api.ads.adwords.axis.v201402.cm.RateExceededError;

import com.google.api.ads.adwords.axis.templateengine.extension.engine.LineProcessor;
import com.google.api.ads.adwords.axis.templateengine.extension.shared.ConstantsIF;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handle Campaign migration for the Package
 * @author Mark Bowyer
 * @version 1.0
 * @since 20/5/14
 */
public class CampaignMigration extends BasePlugin implements ConstantsIF {

  /** holds the fields in a single row, from the CSV file */
  private String[] lineRay = null;

  public CampaignOperation[] operations = null;
  public int operationsIterator = 0;

  private static CampaignServiceInterface campaignService = null;

  private static Pattern operationIndexPattern = 
      Pattern.compile("^.*operations\\[(\\d+)\\].*$");

  /**
   * This enumeration pulls field values out of the given line (String[]). The field names and
   * indices are mapped at runtime, based on the information in the header of the CSV file.
   */
  enum FieldExtractor {
    ClientAccountId("client_account_id"),
    CampaignId("campaignid"),
    ChannelType("channel_type"),
    GoogleSearch("google_search"),
    SearchNetwork("search_network"),
    ContentNetwork("content_network"),
    PartnerSearchNetwork("partner_search_network"),
    DisplaySelect("display_select");

    String fieldname;
    int fieldindex = -1;

    public String getFieldName() {
      return fieldname;
    }

    public int getFieldIndex() {
      return fieldindex;
    }

    FieldExtractor(String fieldname) {
      this.fieldname = fieldname;
    }

    /** 
     * calculate all the field indices in lineRay for all the desired fields 
     * 
     * @param lineRay the incoming line of entries from the CSV file
     */
    public static void setup(String[] lineRay) {
      for (String fieldname : lineRay) {
        FieldExtractor[] values = values();
        FieldExtractor extractor = null;
        for (int i = 0, length = values.length; i < length; i++) {
          extractor = values[i];
          if (extractor.fieldname.equals(fieldname)) {
            extractor.fieldindex = i;
            break;
          }
        }
        if (extractor.fieldindex == -1) {
          System.err.println("Header line contains spaces or invalid column name.");
          System.exit(1);
        }
      }
    }

    public static String getFieldValue(String[] lineRay, FieldExtractor field) {
      return lineRay[field.fieldindex];
    }
  }

  /**
   * Check the numeric fields from the CSV are in fact numeric
   * 
   * @return did they all check out OK?
   */
  public boolean validate() {
    FieldExtractor[] numericValueCheck = {FieldExtractor.ClientAccountId, FieldExtractor.CampaignId};
    for (FieldExtractor numericField : numericValueCheck) {
      String value = FieldExtractor.getFieldValue(lineRay, numericField);
      try {
        Long.parseLong(value);
      } catch (Exception e) {
        lineProcessor.errorSummaryMessage =
            String.format(
                "Problem parsing data in row #%d. Field value was expected to be numeric",
                currentLineNumber);
        lineProcessor.errorCauseMessage =
            String.format("'%s' contains value of '%s'",
                numericField.getFieldName(), value);
        lineProcessor.handleFailure((int) currentLineNumber, lineProcessor.errorSummaryMessage);
        return false;
      }
    }
    return true;
  }

  /** detailed error reporting 
   * @param customerName identifying this line
   * @param e2 the exception we received 
   */
  private void reportError(String name, Exception e2) {
    lineProcessor.errorSummaryMessage =
        String.format(
            "For line#%d could not Migrate the Campaign named '%s' to this MCC.",
            currentLineNumber, name);
    lineProcessor.errorCauseMessage = e2.toString();
    lineProcessor.listOfErrors.add(lineProcessor.errorSummaryMessage);
    e2.printStackTrace();
  }

  @Override
  public boolean setOperations(String[] lineRay, long currentLineNumber) {
    this.lineRay = lineRay;
    this.currentLineNumber = currentLineNumber;

    if (validate()) {
      // extract all the required fields from the row/line
      String clientAccountId =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.ClientAccountId);
      String campaignId = 
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.CampaignId);
      String advertisingChannelType =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.ChannelType);
      String googleSearch =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.GoogleSearch);
      String searchNetwork =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.SearchNetwork);
      String contentNetwork =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.ContentNetwork);
      String partnerSearchNetwork =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.PartnerSearchNetwork);
      String displaySelect =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.DisplaySelect);

      try {

        lineProcessor.awapi.addSession(clientAccountId);
        Campaign campaign = new Campaign();
        campaign.setId(Long.decode(campaignId));
        campaign.setAdvertisingChannelType(AdvertisingChannelType.fromString(advertisingChannelType));
        // Set the campaign network options to Search and Search Network.
        NetworkSetting networkSetting = new NetworkSetting();
        networkSetting.setTargetGoogleSearch(Boolean.valueOf(googleSearch));
        networkSetting.setTargetSearchNetwork(Boolean.valueOf(searchNetwork));
        networkSetting.setTargetContentNetwork(Boolean.valueOf(contentNetwork));
        networkSetting.setTargetPartnerSearchNetwork(Boolean.valueOf(partnerSearchNetwork));
        campaign.setNetworkSetting(networkSetting);
        if (displaySelect.equalsIgnoreCase("TRUE")) {
          campaign.setDisplaySelect(true);
        } else if (displaySelect.equalsIgnoreCase("FALSE")) {
          campaign.setDisplaySelect(false);
        }

        // Create operations.
        CampaignOperation operation = new CampaignOperation();
        operation.setOperand(campaign);
        operation.setOperator(Operator.SET);

        operations[operationsIterator++] = operation;

        return true;

      } catch (Exception generalException) {
        // catch general failures...
        reportError(campaignId, generalException);
        return false;
      }
    } else {
      return false;
    }


  }

  @Override
  public boolean mutate() {
    boolean state = true;
    int line = 0;
    ApiError partialFailures[] = new ApiError[MAX_OPERATIONS];
    ApiError returnedFailures[] = null;

    // Get the CampaignService.
    campaignService = lineProcessor.awapi.campaignService;

    if (campaignService == null) {
      System.err.println("Error fetching CampaignService.");
      throw new NullPointerException();
    }

    if (!ConstantsIF.DEBUG_MODE) {
      try {
        // Add campaign.
        CampaignReturnValue result = campaignService.mutate(operations);
        lineProcessor.voluntaryWaitLogic();

        if (result != null) {
          returnedFailures = result.getPartialFailureErrors();
          if (returnedFailures != null) {
            for (ApiError apiError : returnedFailures) {
              Matcher matcher = operationIndexPattern.matcher(apiError.getFieldPath());
              if (matcher.matches()) {
                int operationIndex = Integer.parseInt(matcher.group(1));
                partialFailures[operationIndex] = apiError;
              }
            }
          }
          for (Campaign campaignResult : result.getValue()) {
            String message = String.format("Campaign with id '%d' and name '%s' ", 
                campaignResult.getId(), campaignResult.getName());

            if (partialFailures[line] == null) {
              // successfully processed a row of data!
              lineProcessor.handleSuccess(line, message + "was migrated.");
            } else if (partialFailures[line] instanceof PolicyViolationError) {
              lineProcessor.handlePolicyViolation(line, "Campaign", 
                  (PolicyViolationError) partialFailures[line]);
            } else {
              lineProcessor.handleFailure(line, message + "failed with: " 
                  + partialFailures[line].getErrorString());
              state = false;
            }
            line++;
          }
        } else {
          lineProcessor.handleFailure(line, "No Campaigns were migrated.");
          lineProcessor.errorCauseMessage = "Campaign Migration failed.";
          state = false;
        }
      } catch (ApiException apiException) {
        // catch specific API errors... eg: RateLimitError...
        ApiError[] errorRay = apiException.getErrors();
        for (ApiError apiError : errorRay) {
          if (apiError instanceof RateExceededError) {
            // RateExceededError
            lineProcessor.processRateExceededError(apiException);
            state = false;
          } else if (apiError instanceof PolicyViolationError) {
            lineProcessor.handlePolicyViolation(line, "Campaign", 
                (PolicyViolationError) apiError);
          } else {
            System.err.println(apiError.getErrorString());
            lineProcessor.handleFailure(line++, apiError.getErrorString());
            state = false;
          }
        }
      } catch (Exception generalException) {
        // catch general failures...
        reportError("Failed to migrate Campaigns", generalException);
        state = false;
        generalException.printStackTrace();
      }
    }
    return state;
  }


  @Override
  public boolean process(String[] lineRay, long currentLineNumber) {
    boolean result = false;

    if (setOperations(lineRay, currentLineNumber)) {
      result = mutate();    
    }
    return result;
  }


  @Override
  public void setup(String[] lineRay, LineProcessor parent) {
    lineProcessor = parent;
    FieldExtractor.setup(lineRay);
    operations = new CampaignOperation[MAX_OPERATIONS];
    operationsIterator = 0;
    opsPerLine = 1;
    idColumns = 1;
  }

  @Override
  public void clearOperations(){
    operations = new CampaignOperation[MAX_OPERATIONS];
    operationsIterator = 0;
    lastBlockLineNumber = currentLineNumber;
  }

}
