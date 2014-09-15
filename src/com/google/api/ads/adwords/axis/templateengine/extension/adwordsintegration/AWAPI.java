package com.google.api.ads.adwords.axis.templateengine.extension.adwordsintegration;

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
import com.google.api.ads.adwords.axis.v201402.cm.AdGroupAdServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.AdGroupCriterionServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.AdGroupServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.BudgetServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignAdExtensionServiceInterface;
import com.google.api.ads.adwords.axis.v201402.cm.CampaignServiceInterface;
import com.google.api.ads.adwords.axis.v201402.mcm.ManagedCustomerServiceInterface;
import com.google.api.ads.adwords.lib.client.AdWordsSession;
import com.google.api.ads.common.lib.auth.OfflineCredentials;
import com.google.api.ads.common.lib.auth.OfflineCredentials.Api;
import com.google.api.ads.common.lib.conf.ConfigurationLoadException;
import com.google.api.ads.common.lib.exception.ServiceException;
import com.google.api.ads.common.lib.exception.ValidationException;
import com.google.api.client.auth.oauth2.Credential;

import com.google.api.ads.adwords.axis.templateengine.extension.engine.LineProcessor;
import com.google.api.ads.adwords.axis.templateengine.extension.shared.ConstantsIF;

import java.io.IOException;
import java.util.*;

/**
 * @author Mark R. Bowyer
 * @author Nazmul Idris
 * @version 2.1
 * @since 25/9/13
 */
public class AWAPI implements ConstantsIF {
  public Credential credential;
  public AdWordsSession session;
  public CampaignAdExtensionServiceInterface campaignAdExtensionService;
  public ManagedCustomerServiceInterface managedCustomerService;
  public BudgetServiceInterface budgetService;
  public CampaignServiceInterface campaignService;
  public AdGroupServiceInterface adGroupService;
  public AdGroupAdServiceInterface adGroupAdService;
  public AdGroupCriterionServiceInterface adGroupCriterionService;

  /** saving ObjectReferences, one per clientaccountid */
  public HashMap<String, ObjectReferences>
  authCache = new HashMap<String, ObjectReferences>();

  /** Simple construct to hold a bunch of references */
  public class ObjectReferences {
    public AdWordsSession session;
    public CampaignAdExtensionServiceInterface campaignAdExtensionService;
    public ManagedCustomerServiceInterface managedCustomerService;
    public BudgetServiceInterface budgetService;
    public CampaignServiceInterface campaignService;
    public AdGroupServiceInterface adGroupService;
    public AdGroupAdServiceInterface adGroupAdService;
    public AdGroupCriterionServiceInterface adGroupCriterionService;

    public ObjectReferences(AdWordsSession session,
        CampaignAdExtensionServiceInterface if1,
        ManagedCustomerServiceInterface if2,
        BudgetServiceInterface if3,
        CampaignServiceInterface if4,
        AdGroupServiceInterface if5,
        AdGroupAdServiceInterface if6,
        AdGroupCriterionServiceInterface if7) {
      this.session = session;
      this.campaignAdExtensionService = if1;
      this.managedCustomerService = if2;
      this.budgetService = if3;
      this.campaignService = if4;
      this.adGroupService = if5;
      this.adGroupAdService = if6;
      this.adGroupCriterionService = if7;
    }
  }

  /** construct an API object for the given client account 
   * @param lineProcessor - the LineProcessor object to use
   * @throws ConfigurationLoadException 
   * @throws ValidationException 
   * @throws IOException 
   * @throws ServiceException */
  public AWAPI(LineProcessor lineProcessor) 
      throws ServiceException, IOException, ValidationException, ConfigurationLoadException {
    try {
      // Generate a refreshable OAuth2 credential similar to a ClientLogin token
      // and can be used in place of a service account.
      credential = new OfflineCredentials.Builder()
      .forApi(Api.ADWORDS)
      .fromFile()
      .build()
      .generateCredential();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Add a session & ObjectReferences for this clientAccountId to the AuthCache
   * 
   * @param clientAccountId
   */
  public void addSession(String clientAccountId){
    if (authCache.containsKey(clientAccountId)) {
      // load references from the map
      ObjectReferences objs = authCache.get(clientAccountId);
      // Get the AdWords Services.
      campaignAdExtensionService = objs.campaignAdExtensionService;
      managedCustomerService = objs.managedCustomerService;
      budgetService = objs.budgetService;
      campaignService = objs.campaignService;
      adGroupService = objs.adGroupService;
      adGroupAdService = objs.adGroupAdService;
      adGroupCriterionService = objs.adGroupCriterionService;
    } else {
      // Construct an AdWordsSession.
      try {  
        // Construct an AdWordsSession.
        session = new AdWordsSession.Builder()
        .fromFile()
        .withOAuth2Credential(credential)
        .build();

      } catch (Exception e) {
        e.printStackTrace();
      }

      AdWordsServices adWordsServices = new AdWordsServices();
      campaignService =
          adWordsServices.get(session, CampaignServiceInterface.class);
      campaignAdExtensionService =
          adWordsServices.get(session, CampaignAdExtensionServiceInterface.class);
      managedCustomerService =
          adWordsServices.get(session, ManagedCustomerServiceInterface.class);
      budgetService = 
          adWordsServices.get(session, BudgetServiceInterface.class);
      adGroupService =
          adWordsServices.get(session, AdGroupServiceInterface.class);
      adGroupAdService =
          adWordsServices.get(session, AdGroupAdServiceInterface.class);
      adGroupCriterionService =
          adWordsServices.get(session, AdGroupCriterionServiceInterface.class);

      // save these for use later
      authCache.put(clientAccountId,
          new ObjectReferences(session,
              campaignAdExtensionService,
              managedCustomerService,
              budgetService,
              campaignService,
              adGroupService,
              adGroupAdService,
              adGroupCriterionService));

      voluntaryWaitLogic();
    }
  }

  /** wait for {@link #PROGRAM_SMALL_PAUSE_LENGTH_MS} after an API operation is invoked */
  private void voluntaryWaitLogic() {
    // small pause
    try {
      Thread.currentThread();
      Thread.sleep(ConstantsIF.PROGRAM_SMALL_PAUSE_LENGTH_MS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
