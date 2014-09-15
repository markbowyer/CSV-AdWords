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

import com.google.api.ads.adwords.axis.factory.AdWordsServices;
import com.google.api.ads.adwords.axis.v201402.cm.AdGroupAdOperation;
import com.google.api.ads.adwords.axis.v201402.cm.ApiError;
import com.google.api.ads.adwords.axis.v201402.cm.ApiException;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignFeed;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignFeedOperation;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignFeedPage;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignFeedReturnValue;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignFeedServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.Feed;
import com.google.api.ads.adwords.axis.v201402.cm.FeedItem;
import com.google.api.ads.adwords.axis.v201402.cm.FeedItemOperation;
import com.google.api.ads.adwords.axis.v201402.cm.FeedItemPage;
import com.google.api.ads.adwords.axis.v201402.cm.FeedItemReturnValue;
import com.google.api.ads.adwords.axis.v201402.cm.FeedItemServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.FeedMapping;
import com.google.api.ads.adwords.axis.v201402.cm.FeedMappingOperation;
import com.google.api.ads.adwords.axis.v201402.cm.FeedMappingPage;
import com.google.api.ads.adwords.axis.v201402.cm.FeedMappingReturnValue;
import com.google.api.ads.adwords.axis.v201402.cm.FeedMappingServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.FeedOperation;
import com.google.api.ads.adwords.axis.v201402.cm.FeedPage;
import com.google.api.ads.adwords.axis.v201402.cm.FeedReturnValue;
import com.google.api.ads.adwords.axis.v201402.cm.FeedServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.Operator;
import com.google.api.ads.adwords.axis.v201402.cm.OrderBy;
import com.google.api.ads.adwords.axis.v201402.cm.PolicyViolationError;
import com.google.api.ads.adwords.axis.v201402.cm.Predicate;
import com.google.api.ads.adwords.axis.v201402.cm.PredicateOperator;
import com.google.api.ads.adwords.axis.v201402.cm.RateExceededError;
import com.google.api.ads.adwords.axis.v201402.cm.Selector;
import com.google.api.ads.adwords.axis.v201402.cm.SortOrder;
import com.google.api.ads.adwords.lib.client.AdWordsSession;

import com.google.api.ads.adwords.axis.templateengine.extension.adwordsintegration.AWAPI;
import com.google.api.ads.adwords.axis.templateengine.extension.engine.LineProcessor;
import com.google.api.ads.adwords.axis.templateengine.extension.shared.ConstantsIF;

import java.rmi.RemoteException;

/** Handle cleaning out Feeds for the Package
 * @author Mark Bowyer
 * @version 2.0
 * @since 9/1/13 17:00
 */
public class FeedDeletingPlugin extends BasePlugin implements ConstantsIF {
  public AdGroupAdOperation[] operations = null;
  public int operationsIterator = 0;

  private static FeedMappingServiceInterface feedMappingService = null;
  private static FeedServiceInterface feedService = null;
  private static FeedItemServiceInterface feedItemService = null;
  private static CampaignFeedServiceInterface campaignFeedService = null;

  private CampaignFeedPage campaignFeedPage = null;
  private FeedPage feedPage = null;
  private FeedItemPage feedItemPage = null;
  private FeedMappingPage feedMappingPage = null;

  private CampaignFeedOperation[] campaignFeedOperation = null;
  private FeedOperation[] feedOperation = null;
  private FeedItemOperation[] feedItemOperation = null;
  private FeedMappingOperation[] feedMappingOperation = null;

  private int cFOperationsIterator = 0;
  private int fOperationsIterator = 0;
  private int fIOperationsIterator = 0;
  private int fMOperationsIterator = 0;

  /** holds the fields in a single row, from the CSV file */
  private String[] lineRay = null;

  /**
   * This enumeration pulls field values out of the given line (String[]). The field names and
   * indices are mapped at runtime, based on the information in the header of the CSV file.
   */
  enum FieldExtractor {
    ClientAccountId("client_account_id");

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
    FieldExtractor[] numericValueCheck =
      {FieldExtractor.ClientAccountId};

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
            "For line#%d could not add the TextAd with URL '%s' to this MCC.",
            currentLineNumber, name);
    lineProcessor.errorCauseMessage = e2.toString();
    lineProcessor.listOfErrors.add(lineProcessor.errorSummaryMessage);
    e2.printStackTrace();
  }

  /**
   * Create the various Services required by this Plugin
   * @param clientCustomerId which CCID to use to base these Services on
   */
  private static void setupServices(String clientCustomerId) {

    AWAPI.ObjectReferences objectReferences = lineProcessor.awapi.authCache.get(clientCustomerId);
    AdWordsSession session = objectReferences.session;
    AdWordsServices adWordsServices = new AdWordsServices();

    // Get the FeedMappingService.
    feedMappingService = adWordsServices.get(session, FeedMappingServiceInterface.class);
    // Get the FeedService.
    feedService = adWordsServices.get(session, FeedServiceInterface.class);
    // Get the FeedItemService.
    feedItemService = adWordsServices.get(session, FeedItemServiceInterface.class);
    // Get the CampaignFeedService.
    campaignFeedService = adWordsServices.get(session, CampaignFeedServiceInterface.class);
  }

  @Override
  public boolean process(String[] lineRay, long currentLineNumber) {
    // not supporting the old way with this very new plugin.
    return false;
  }

  @Override
  public boolean setOperations(String[] lineRay, long currentLineNumber) {
    this.lineRay = lineRay;
    this.currentLineNumber = currentLineNumber;
    if (validate()) {
      // extract all the required fields from the row/line
      String clientAccountId =
          FieldExtractor.getFieldValue(lineRay, FieldExtractor.ClientAccountId);

      lineProcessor.awapi.addSession(clientAccountId);
      setupServices(clientAccountId); // We know we only process blocks of the same ID.

      String[] deleted = new String[] {"DELETED"};
      String[] user = new String[] {"USER"};


      try {
        // Create selector.
        Selector cfSelector = new Selector();
        cfSelector.setFields(new String[] {"FeedId"});
        cfSelector.setOrdering(new OrderBy[] {new OrderBy("FeedId", SortOrder.ASCENDING)});
        // Create predicates.
        Predicate cfPredicate = new Predicate(
            "Status", PredicateOperator.NOT_EQUALS, deleted);
        cfSelector.setPredicates(new Predicate[] {cfPredicate});
        campaignFeedPage = campaignFeedService.get(cfSelector);

        Selector fSelector = new Selector();
        fSelector.setFields(new String[] {"Id", "Origin"});
        fSelector.setOrdering(new OrderBy[] {new OrderBy("Origin", SortOrder.ASCENDING)});
        // Create predicates.
        Predicate fdPredicate = new Predicate(
            "FeedStatus", PredicateOperator.NOT_EQUALS, deleted);
        Predicate foPredicate = new Predicate(
            "Origin", PredicateOperator.EQUALS, user);
        fSelector.setPredicates(new Predicate[] {fdPredicate, foPredicate});
        feedPage = feedService.get(fSelector);
        System.err.println(feedPage.getTotalNumEntries() + " Feeds found!");

        Selector fiSelector = new Selector();
        fiSelector.setFields(new String[] {"FeedItemId"});
        fiSelector.setOrdering(new OrderBy[] {new OrderBy("FeedItemId", SortOrder.ASCENDING)});
        // Create predicates.
        Predicate fiPredicate = new Predicate(
            "Status", PredicateOperator.NOT_EQUALS, deleted);
        fiSelector.setPredicates(new Predicate[] {fiPredicate});
        feedItemPage = feedItemService.get(fiSelector);
        System.err.println(feedItemPage.getTotalNumEntries() + " FeedItems found!");


        Selector fmSelector = new Selector();
        fmSelector.setFields(new String[] {"FeedMappingId"});
        fmSelector.setOrdering(new OrderBy[] {new OrderBy("FeedMappingId", SortOrder.ASCENDING)});
        // Create predicates.
        Predicate fmPredicate = new Predicate(
            "Status", PredicateOperator.NOT_EQUALS, deleted);
        fmSelector.setPredicates(new Predicate[] {fmPredicate});
        feedMappingPage = feedMappingService.get(fmSelector);
        System.err.println(feedMappingPage.getTotalNumEntries() + " FeedMappings found!");

      } catch (ApiException e) {
        e.printStackTrace();
      } catch (RemoteException e) {
        e.printStackTrace();
      }

      if (campaignFeedPage != null && campaignFeedPage.getEntries() != null) {
        for (CampaignFeed campaignFeed : campaignFeedPage.getEntries()) {
          CampaignFeedOperation cfo = new CampaignFeedOperation();
          cfo.setOperator(Operator.REMOVE);
          cfo.setOperand(campaignFeed);
          campaignFeedOperation[cFOperationsIterator++] = cfo;
        }
      }
      if (feedPage != null && feedPage.getEntries() != null) {
        for (Feed feed : feedPage.getEntries()) {
          FeedOperation fo = new FeedOperation();
          fo.setOperator(Operator.REMOVE);
          fo.setOperand(feed);
          feedOperation[fOperationsIterator++] = fo;
        }
      }
      if (feedItemPage != null && feedItemPage.getEntries() != null) {
        for (FeedItem feedItem : feedItemPage.getEntries()) {
          FeedItemOperation fio = new FeedItemOperation();
          fio.setOperator(Operator.REMOVE);
          fio.setOperand(feedItem);
          feedItemOperation[fIOperationsIterator++] = fio;
        }
      }
      if (feedMappingPage != null && feedMappingPage.getEntries() != null) {
        for (FeedMapping feedMapping : feedMappingPage.getEntries()) {
          FeedMappingOperation fmo = new FeedMappingOperation();
          fmo.setOperator(Operator.REMOVE);
          fmo.setOperand(feedMapping);
          feedMappingOperation[fMOperationsIterator++] = fmo;
        }
      }
      return true;

    }
    return false;
  }

  @Override
  public boolean mutate() {

    boolean state = true;

    if (!ConstantsIF.DEBUG_MODE) { 
      try {
        if (campaignFeedOperation[0] != null) {
          CampaignFeedReturnValue result = campaignFeedService.mutate(campaignFeedOperation);

          if (result != null) {
            for (CampaignFeed campaignFeed : result.getValue()) {
              lineProcessor.handleSuccess(currentLineNumber, String.format(
                  "CampaignFeed for CampaignId \"%d\" now has Status %s.",
                  campaignFeed.getCampaignId(), campaignFeed.getStatus()));
            }
          } else {
            lineProcessor.handleFailure((int) currentLineNumber, "No CampaignFeeds were deleted.");
            lineProcessor.errorCauseMessage = "CampaignFeed delete failed.";
            state = false;
          }
        }
      } catch (ApiException apiException) {
        ApiError[] errorRay = apiException.getErrors();
        for (ApiError apiError : errorRay) {
          if (apiError instanceof RateExceededError) {
            lineProcessor.processRateExceededError(apiException);
            state = false;
          } else if (apiError instanceof PolicyViolationError) {
            lineProcessor.handlePolicyViolation((int) currentLineNumber, "CampaignFeed", 
                (PolicyViolationError) apiError);
          } else {
            System.err.println(apiError.getErrorString());
            lineProcessor.handleFailure((int) currentLineNumber, 
                apiError.getErrorString());
            state = false;
          }
        } 
      } catch (Exception generalException) {
        reportError("Failed to Delete CampaignFeed", generalException);
        state = false;
        generalException.printStackTrace();
      }

      try {
        if (feedOperation[0] != null) {
          FeedReturnValue result = feedService.mutate(feedOperation);

          if (result != null) {
            for (Feed feed : result.getValue()) {
              lineProcessor.handleSuccess(currentLineNumber, String.format(
                  "Feed of Id \"%d\" now has Status %s.",
                  feed.getId(), feed.getStatus()));
            }
          } else {
            lineProcessor.handleFailure((int) currentLineNumber, "No Feeds were deleted.");
            lineProcessor.errorCauseMessage = "Feed delete failed.";
            state = false;
          }
        }
      } catch (ApiException apiException) {
        ApiError[] errorRay = apiException.getErrors();
        for (ApiError apiError : errorRay) {
          if (apiError instanceof RateExceededError) {
            lineProcessor.processRateExceededError(apiException);
            state = false;
          } else if (apiError instanceof PolicyViolationError) {
            lineProcessor.handlePolicyViolation((int) currentLineNumber, "Feed", 
                (PolicyViolationError) apiError);
          } else {
            System.err.println(apiError.getErrorString());
            lineProcessor.handleFailure((int) currentLineNumber, apiError.getErrorString());
            state = false;
          }
        } 
      } catch (Exception generalException) {
        reportError("Failed to Delete Feed", generalException);
        state = false;
        generalException.printStackTrace();
      }

      try {
        if (feedItemOperation[0] != null) {
          FeedItemReturnValue result = feedItemService.mutate(feedItemOperation);

          if (result != null) {
            for (FeedItem feedItem : result.getValue()) {
              lineProcessor.handleSuccess(currentLineNumber, String.format(
                  "FeedItem of Id \"%d\" now has Status %s.",
                  feedItem.getFeedItemId(), feedItem.getStatus()));
            }
          } else {
            lineProcessor.handleFailure((int) currentLineNumber, "No FeedItems were deleted.");
            lineProcessor.errorCauseMessage = "FeedItem delete failed.";
            state = false;
          }
        }
      } catch (ApiException apiException) {
        ApiError[] errorRay = apiException.getErrors();
        for (ApiError apiError : errorRay) {
          if (apiError instanceof RateExceededError) {
            lineProcessor.processRateExceededError(apiException);
            state = false;
          } else if (apiError instanceof PolicyViolationError) {
            lineProcessor.handlePolicyViolation((int) currentLineNumber, "FeedItem", 
                (PolicyViolationError) apiError);
          } else {
            System.err.println(apiError.getErrorString());
            lineProcessor.handleFailure((int) currentLineNumber, apiError.getErrorString());
            state = false;
          }
        } 
      } catch (Exception generalException) {
        reportError("Failed to Delete FeedItem", generalException);
        state = false;
        generalException.printStackTrace();
      }

      try {
        if (feedMappingOperation[0] != null) {
          FeedMappingReturnValue result = feedMappingService.mutate(feedMappingOperation);

          if (result != null) {
            for (FeedMapping feedMapping : result.getValue()) {
              lineProcessor.handleSuccess(currentLineNumber, String.format(
                  "FeedMapping of Id \"%d\" now has Status %s.",
                  feedMapping.getFeedMappingId(), feedMapping.getStatus()));
            }
          } else {
            lineProcessor.handleFailure((int) currentLineNumber, "No FeedMappings were deleted.");
            lineProcessor.errorCauseMessage = "FeedMapping delete failed.";
            state = false;
          }
        }
      } catch (ApiException apiException) {
        ApiError[] errorRay = apiException.getErrors();
        for (ApiError apiError : errorRay) {
          if (apiError instanceof RateExceededError) {
            lineProcessor.processRateExceededError(apiException);
            state = false;
          } else if (apiError instanceof PolicyViolationError) {
            lineProcessor.handlePolicyViolation((int) currentLineNumber, "FeedMapping", 
                (PolicyViolationError) apiError);
          } else {
            System.err.println(apiError.getErrorString());
            lineProcessor.handleFailure((int) currentLineNumber, apiError.getErrorString());
            state = false;
          }
        } 
      } catch (Exception generalException) {
        reportError("Failed to Delete FeedMapping", generalException);
        state = false;
        generalException.printStackTrace();
      }

    } 

    return state;

  }

  @Override
  public void clearOperations() {
    lastBlockLineNumber = currentLineNumber;
    campaignFeedPage = null;
    feedPage = null;
    feedItemPage = null;
    feedMappingPage = null;
    campaignFeedOperation = new CampaignFeedOperation[MAX_OPERATIONS];
    feedOperation = new FeedOperation[MAX_OPERATIONS];
    feedItemOperation = new FeedItemOperation[MAX_OPERATIONS];
    feedMappingOperation = new FeedMappingOperation[MAX_OPERATIONS];
    cFOperationsIterator = 0;
    fOperationsIterator = 0;
    fIOperationsIterator = 0;
    fMOperationsIterator = 0;
  }

  @Override
  public void setup(String[] lineRay, LineProcessor parent) {
    lineProcessor = parent;
    FieldExtractor.setup(lineRay);
    opsPerLine = 4;
    idColumns = 1;
    campaignFeedOperation = new CampaignFeedOperation[MAX_OPERATIONS];
    feedOperation = new FeedOperation[MAX_OPERATIONS];
    feedItemOperation = new FeedItemOperation[MAX_OPERATIONS];
    feedMappingOperation = new FeedMappingOperation[MAX_OPERATIONS];
  }

}
