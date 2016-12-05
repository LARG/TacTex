/*
 * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
 * Copyright (c) 2013-2016 Daniel Urieli and Peter Stone {urieli,pstone}@cs.utexas.edu               
 *
 *
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *
 *     Copyright (c) 2012 by the original author
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package edu.utexas.cs.tactex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.Timeslot;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.PortfolioManagerService;
import edu.utexas.cs.tactex.TariffRepoMgrService;
import edu.utexas.cs.tactex.PortfolioManagerService.CustomerRecord;
import edu.utexas.cs.tactex.core.PowerTacBroker;

/**
 * @author jcollins
 */
public class PortfolioManagerTest
{
  private Instant baseTime;
  
  
  private TimeslotRepo timeslotRepo;
  private CustomerRepo customerRepo;
  private TariffRepo tariffRepo;
  private TariffRepoMgrService tariffRepoMgrService;
  
  private PortfolioManagerService portfolioManagerService;
  private PowerTacBroker brokerContext;  
  private Broker thebroker;
  private ConfiguratorFactoryService configuratorFactoryService;

  CustomerInfo podunk;
  CustomerInfo midvale;


  private TariffSpecification spec_CONSUMPTION;
  private TariffSpecification spec_PRODUCTION;
  private TariffSpecification spec_SOLAR_PRODUCTION;
  private TariffSpecification spec_WIND_PRODUCTION;
  private TariffSpecification spec_ELECTRIC_VEHICLE;
  private TariffSpecification spec_INTERRUPTIBLE_CONSUMPTION;
  private TariffSpecification spec_THERMAL_STORAGE_CONSUMPTION;

  /**
   *
   */
  @Before
  public void setUp () throws Exception
  {
 // set the time
    baseTime =
        new DateTime(2011, 2, 1, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    
    brokerContext = mock(PowerTacBroker.class);
    thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);
    
    timeslotRepo = mock(TimeslotRepo.class);

    customerRepo = new CustomerRepo();
    
    
    // set up a competition
    Competition.newInstance("bla"); // must have a competition initialized
    
    
    podunk = new CustomerInfo("Podunk", 3);
    customerRepo.add(podunk);
    midvale = new CustomerInfo("Midvale", 1000); 
    customerRepo.add(midvale);

    tariffRepo = new TariffRepo();
    tariffRepoMgrService = new TariffRepoMgrService();

    portfolioManagerService = new PortfolioManagerService();
    configuratorFactoryService = new ConfiguratorFactoryService();
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "timeslotRepo",
                                 timeslotRepo);
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "customerRepo",
                                 customerRepo);
    ReflectionTestUtils.setField(tariffRepoMgrService,
                                 "tariffRepo",
                                 tariffRepo);
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "tariffRepoMgr",
                                 tariffRepoMgrService);
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "configuratorFactoryService",
                                 configuratorFactoryService);
        
    portfolioManagerService.initialize(brokerContext);



    // for tests of PowerType and tariffs the use and can be used
    //
    spec_CONSUMPTION = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    spec_PRODUCTION = new TariffSpecification(brokerContext.getBroker(), PowerType.PRODUCTION);
    spec_SOLAR_PRODUCTION = new TariffSpecification(brokerContext.getBroker(), PowerType.SOLAR_PRODUCTION);
    spec_WIND_PRODUCTION = new TariffSpecification(brokerContext.getBroker(), PowerType.WIND_PRODUCTION);
    spec_ELECTRIC_VEHICLE = new TariffSpecification(brokerContext.getBroker(), PowerType.ELECTRIC_VEHICLE);
    spec_INTERRUPTIBLE_CONSUMPTION = new TariffSpecification(brokerContext.getBroker(), PowerType.INTERRUPTIBLE_CONSUMPTION);
    spec_THERMAL_STORAGE_CONSUMPTION = new TariffSpecification(brokerContext.getBroker(), PowerType.THERMAL_STORAGE_CONSUMPTION);
    //
    // initialize
    portfolioManagerService.getCompetingTariffs(spec_CONSUMPTION.getPowerType()).add(spec_CONSUMPTION);
    portfolioManagerService.getCompetingTariffs(spec_PRODUCTION.getPowerType()).add(spec_PRODUCTION);
    portfolioManagerService.getCompetingTariffs(spec_SOLAR_PRODUCTION.getPowerType()).add(spec_SOLAR_PRODUCTION);
    portfolioManagerService.getCompetingTariffs(spec_WIND_PRODUCTION.getPowerType()).add(spec_WIND_PRODUCTION);
    portfolioManagerService.getCompetingTariffs(spec_ELECTRIC_VEHICLE.getPowerType()).add(spec_ELECTRIC_VEHICLE);
    portfolioManagerService.getCompetingTariffs(spec_INTERRUPTIBLE_CONSUMPTION.getPowerType()).add(spec_INTERRUPTIBLE_CONSUMPTION);
    portfolioManagerService.getCompetingTariffs(spec_THERMAL_STORAGE_CONSUMPTION.getPowerType()).add(spec_THERMAL_STORAGE_CONSUMPTION);

  }
  
  /**
   * Test customer boot data
   */
  @SuppressWarnings("unchecked")
  @Test
  public void test_initialize () { 
     
    // initialize with arbitrary values
    
    ReflectionTestUtils.setField(portfolioManagerService,"customerProfiles", null);
    ReflectionTestUtils.setField(portfolioManagerService,"customerProfilesByPowerType", null);
    ReflectionTestUtils.setField(portfolioManagerService,"customerSubscriptions", null);
    ReflectionTestUtils.setField(portfolioManagerService,"competingTariffs", null);
    ReflectionTestUtils.setField(portfolioManagerService,"bootstrapTimeSlotNum", 1234);
    ReflectionTestUtils.setField(portfolioManagerService,"gameStart", false);
    ReflectionTestUtils.setField(portfolioManagerService,"tookAllCrashedAction", true);

    // initialize should set all fields correctly
    portfolioManagerService.initialize(brokerContext);

    // maps should be initialized to empty
    //
    HashMap<CustomerInfo, CustomerRecord> customerProfiles = 
        (HashMap<CustomerInfo, CustomerRecord>) 
            ReflectionTestUtils.getField(portfolioManagerService, "customerProfiles");
    assertNotNull("customerProfiles", customerProfiles);
    assertEquals("customerProfiles.length", 0, customerProfiles.size());
    
    HashMap<PowerType, HashMap<CustomerInfo, CustomerRecord>> customerProfilesByPowerType = 
        (HashMap<PowerType, HashMap<CustomerInfo, CustomerRecord>>) 
            ReflectionTestUtils.getField(portfolioManagerService, "customerProfilesByPowerType");
    assertNotNull("customerProfilesByPowerType", customerProfilesByPowerType);
    assertEquals("customerProfilesByPowerType.length", 0, customerProfilesByPowerType.size());

    HashMap<TariffSpecification, HashMap<CustomerInfo, CustomerRecord>> customerSubscriptions = 
      (HashMap<TariffSpecification, HashMap<CustomerInfo, CustomerRecord>>)
          ReflectionTestUtils.getField(portfolioManagerService, "customerSubscriptions");
    assertNotNull("customerSubscriptions", customerSubscriptions);
    assertEquals("customerSubscriptions.length", 0, customerSubscriptions.size());

    HashMap<PowerType, List<TariffSpecification>> competingTariffs = 
      (HashMap<PowerType, List<TariffSpecification>>)
          ReflectionTestUtils.getField(portfolioManagerService, "competingTariffs");
    assertNotNull("competingTariffs", competingTariffs);
    assertEquals("competingTariffs.length", 0, competingTariffs.size());

    int bootstrapTimeSlotNum = (Integer) 
        ReflectionTestUtils.getField(portfolioManagerService, "bootstrapTimeSlotNum");
    assertEquals("bootstrapTimeSlotNum", -1, bootstrapTimeSlotNum);

    boolean gameStart = (Boolean) 
        ReflectionTestUtils.getField(portfolioManagerService, "gameStart");
    assertEquals("gameStart", true, gameStart);
    
    boolean tookAllCrashedAction = (Boolean) 
        ReflectionTestUtils.getField(portfolioManagerService, "tookAllCrashedAction");
    assertEquals("tookAllCrashedAction", false, tookAllCrashedAction);

    
  }
  
  /**
   * Test customer boot data
   */
  @Test
  public void testCustomerBootstrap ()
  {
    // create a Timeslot for use by the bootstrap data
    Timeslot ts0 = new Timeslot(8*24, baseTime.plus(TimeService.DAY * 8));
    when(timeslotRepo.currentTimeslot()).thenReturn(ts0);
    // send to broker and check
    double[] podunkData = new double[7*24];
    Arrays.fill(podunkData, 3.6);
    double[] midvaleData = new double[7*24];
    Arrays.fill(midvaleData, 1600.0);
    CustomerBootstrapData boot =
        new CustomerBootstrapData(podunk, PowerType.CONSUMPTION, podunkData);
    portfolioManagerService.handleMessage(boot);
    boot = new CustomerBootstrapData(midvale, PowerType.CONSUMPTION, midvaleData);
    portfolioManagerService.handleMessage(boot);
    
    double[] podunkUsageByPowerType = 
        portfolioManagerService.getRawUsageForCustomerByPowerType(podunk).get(PowerType.CONSUMPTION);
    double [] podunkGeneralUsage = 
        portfolioManagerService.getGeneralRawUsageForCustomer(podunk, false).toArray();
    assertNotNull("podunk usage is recorded", podunkUsageByPowerType);
    assertEquals("correct usage value for podunk", 1.2, podunkUsageByPowerType[23], 1e-6);
    assertArrayEquals(podunkUsageByPowerType, podunkGeneralUsage, 1e-6);
    
    double[] midvaleUsageByPowerType = 
        portfolioManagerService.getRawUsageForCustomerByPowerType(midvale).get(PowerType.CONSUMPTION);
    double [] midvaleGeneralUsage = 
        portfolioManagerService.getGeneralRawUsageForCustomer(midvale, false).toArray();
    assertNotNull("midvale usage is recorded", midvaleUsageByPowerType);
    assertEquals("correct usage value for midvale", 1.6, midvaleUsageByPowerType[27], 1e-6);
    assertArrayEquals(midvaleUsageByPowerType, midvaleGeneralUsage, 1e-6);
  }
  
  /**
   * Test CONSUME transaction
   */
  @Test
  public void testTariffTransactionCONSUME () {
    
    // create a Timeslot for use by the bootstrap data
    Timeslot ts0 = new Timeslot(8*24, baseTime.plus(TimeService.DAY * 8));
    when(timeslotRepo.currentTimeslot()).thenReturn(ts0);
    // send to broker and check
    double[] podunkData = new double[7*24];
    Arrays.fill(podunkData, 3.6);
    double[] midvaleData = new double[7*24];
    Arrays.fill(midvaleData, 1600.0);
    
    // get some boot data
    CustomerBootstrapData boot =
        new CustomerBootstrapData(podunk, PowerType.CONSUMPTION, podunkData);
    portfolioManagerService.handleMessage(boot);
    boot = new CustomerBootstrapData(midvale, PowerType.CONSUMPTION, midvaleData);
    portfolioManagerService.handleMessage(boot);
   
    
    // publish spec
    TariffSpecification spec1 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    // inject it to repo - it's supposed to be there when TariffTransactions arrives
    // I don't even add rates, since they are not used here
    tariffRepo.addSpecification(spec1);
        
    
    // test transaction that changes the average
    TariffTransaction ttx1 = new TariffTransaction(brokerContext.getBroker(), 7*24 + 1, TariffTransaction.Type.CONSUME, spec1, podunk, 1, 2.0, 1234);
    portfolioManagerService.handleMessage(ttx1);        
    // extract data and compare
    double [] podunkUsageByTariff = 
        portfolioManagerService.getRawUsageForCustomerByTariff(podunk, spec1).toArray();
    double[] podunkUsageByPowerType = 
        portfolioManagerService.getRawUsageForCustomerByPowerType(podunk).get(PowerType.CONSUMPTION);
    double [] podunkGeneralUsage = 
        portfolioManagerService.getGeneralRawUsageForCustomer(podunk, false).toArray();
    assertNotNull("podunk usage is recorded", podunkUsageByTariff);
    assertNotNull("podunk usage is recorded", podunkUsageByPowerType);
    assertNotNull("podunk usage is recorded", podunkGeneralUsage);
    assertTrue("usage changed in correct index for podunk", 1.2 < podunkUsageByPowerType[1] && podunkUsageByPowerType[1] < 2.0);
    assertEquals("usage did not change in other indices for podunk", 1.2,  podunkUsageByPowerType[0], 1e-6);
    assertEquals("usage did not change in other indices for podunk", 1.2,  podunkUsageByPowerType[2], 1e-6);
    assertArrayEquals("usage-by-powertype equals general-usage", podunkUsageByPowerType, podunkGeneralUsage, 1e-6);
    assertArrayEquals("usage-by-powertype equals usage-by-tariff", podunkUsageByPowerType, podunkUsageByTariff, 1e-6);
    


    // test a transaction that doesn't change the average 
    TariffTransaction ttx2 = new TariffTransaction(brokerContext.getBroker(), 7*24 + 1, TariffTransaction.Type.CONSUME, spec1, midvale, 10, 16, 1234);    
    portfolioManagerService.handleMessage(ttx2);
    // extract data
    double[] midvaleUsageByTariff = portfolioManagerService.getRawUsageForCustomerByTariff(midvale,spec1).toArray();
    double[] midvaleUsageByPowerType = 
        portfolioManagerService.getRawUsageForCustomerByPowerType(midvale).get(PowerType.CONSUMPTION);
    double [] midvaleGeneralUsage = 
        portfolioManagerService.getGeneralRawUsageForCustomer(midvale, false).toArray();
    // asserts
    assertNotNull("midvale usage is recorded", midvaleUsageByTariff);
    assertNotNull("midvale usage is recorded", midvaleUsageByPowerType);
    assertNotNull("midvale usage is recorded", midvaleGeneralUsage);
    assertEquals("correct usage value for midvale", 1.6, midvaleUsageByPowerType[1], 1e-6);
    assertArrayEquals("usage-by-powertype equals general-usage", midvaleUsageByPowerType, midvaleGeneralUsage, 1e-6);
    assertArrayEquals("usage-by-powertype equals usage-by-tariff", midvaleUsageByPowerType, midvaleUsageByTariff, 1e-6);

    
    // test new consumption tariff - now the tariff record
    // should defer from the powertype and general records
    // publish spec
    TariffSpecification spec2 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    // inject it to repo - it's supposed to be there when TariffTransactions arrives
    // I don't even add rates, since they are not used here
    tariffRepo.addSpecification(spec2);
    TariffTransaction ttx3 = new TariffTransaction(brokerContext.getBroker(), 7*24 + 1, TariffTransaction.Type.CONSUME, spec2, podunk, 1, 2.0, 1234);
    portfolioManagerService.handleMessage(ttx3);
    // extract data 
    double[] podunkUsageByTariff1 = 
        portfolioManagerService.getRawUsageForCustomerByTariff(podunk, spec1).toArray();
    double[] podunkUsageByTariff2 = 
        portfolioManagerService.getRawUsageForCustomerByTariff(podunk, spec2).toArray();
    podunkUsageByPowerType = 
        portfolioManagerService.getRawUsageForCustomerByPowerType(podunk).get(PowerType.CONSUMPTION);
    podunkGeneralUsage = 
        portfolioManagerService.getGeneralRawUsageForCustomer(podunk, false).toArray();
    // asserts
    assertNotNull("podunk usage is recorded", podunkUsageByTariff);
    assertNotNull("podunk usage is recorded", podunkUsageByPowerType);
    assertNotNull("podunk usage is recorded", podunkGeneralUsage);
    assertArrayEquals("usage-by-powerType is still similar to general-usage", podunkUsageByPowerType, podunkGeneralUsage, 1e-6);
    assertTrue("usage-by-tariff for old spec should be different than general-usage", podunkGeneralUsage[1] != podunkUsageByTariff1[1]);
    // however, the new spec was seeded with the general data so it's similar to it
    assertArrayEquals("usage-by-tariff for new spec is still the same as genearal usage", podunkGeneralUsage, podunkUsageByTariff2, 1e-6);
    

  }
  
  /**
   * Test SIGNUP, WITHDRAW and getCustomerSubscriptions() as well
   */
  @Test
  public void testSignupWithraw ()
  {
    // reset portfolio manager - another test should verify 
    // that it is indeed reset
    portfolioManagerService.initialize(brokerContext);
    
    // create a Timeslot for use by the bootstrap data
    Timeslot ts0 = new Timeslot(8*24, baseTime.plus(TimeService.DAY * 8));
    when(timeslotRepo.currentTimeslot()).thenReturn(ts0);

    TariffSpecification spec = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    
    
    // test errors:
    // test illegal time? 
    // transaction with tariff I didn't publish should leave state unchanged
    // non-signup/withdraw transactions should leave subscriptions unchanged
    // test withdraw before signup
    // non existing spec
    // non existing customer, customer with wrong powertype?
    // negative customer count?
    HashMap<TariffSpecification, 
        HashMap<CustomerInfo, Integer>> customerSubscriptionCounts;
    TariffTransaction ttx;

    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("empty subscriptions after initialization", 0, customerSubscriptionCounts.size());

    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.WITHDRAW,// withdraw 
                                 spec, 
                                 podunk, 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("withdraw from non-published tariff leaves state unchanged", 0, customerSubscriptionCounts.size());


    // adding spec to portfolioManagerService through 'publishing'
    tariffRepo.addSpecification(spec);
    

    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.PUBLISH,// publish
                                 spec, 
                                 podunk, 
                                 7, 
                                 0, 
                                 0);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.REVOKE,// revoke
                                 spec, 
                                 podunk, 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("non signup/withdraw transactions leave state unchanged", 0, customerSubscriptionCounts.size());


    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.WITHDRAW,// withdraw 
                                 null,                          // null spec 
                                 podunk, 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.SIGNUP, // signup
                                 null,                          // null spec
                                 podunk, 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.SIGNUP, // signup
                                 new TariffSpecification(null, null),     // non-existing spec
                                 podunk, 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("signup/withdraw from null/non-existing spec leaves state unchanged", 0, customerSubscriptionCounts.size());


    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.WITHDRAW, // withdraw
                                 spec, 
                                 null,                            // null customer 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.SIGNUP,   // signup
                                 spec, 
                                 null,                            // null customer 
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.SIGNUP,   // signup
                                 spec, 
                                 new CustomerInfo("BrightNewCustomer",2),              // non-existing customer
                                 7, 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("signup/withdraw with null/non-existing customer leaves state unchanged", 0, customerSubscriptionCounts.size());


    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.SIGNUP, 
                                 spec,
                                 podunk, 
                                 0,                            // non-positive customer-count
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("signup with non-positive customer count spec leaves state unchanged", 0, customerSubscriptionCounts.size());

    
    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                7*24 + 1, 
                                TariffTransaction.Type.WITHDRAW,// withdraw 
                                spec, 
                                podunk, 
                                7, 
                                0, 
                                0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("withdraw before signup (for published tariff) adds record but 0 population", 1, customerSubscriptionCounts.size());    
    assertEquals("1 customer record for this tariff", 1, customerSubscriptionCounts.get(spec).size());
    assertEquals("customer count is 0", 0, customerSubscriptionCounts.get(spec).get(podunk).intValue());



    // TEST OK conditions:
    
    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.SIGNUP,   // signup
                                 spec,
                                 podunk, 
                                 2,                               // 2 customers  
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("1 subscribed tariff", 1, customerSubscriptionCounts.size());
    assertEquals("1 customer type subscribed for this tariff", 1, customerSubscriptionCounts.get(spec).size());
    assertEquals("customer count is 2", 2, customerSubscriptionCounts.get(spec).get(podunk).intValue());
    
    
    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                 7*24 + 1, 
                                 TariffTransaction.Type.WITHDRAW, // withdraw
                                 spec,
                                 podunk, 
                                 1,                               // 1 customers 
                                 0, 
                                 0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("1 subscribed tariff", 1, customerSubscriptionCounts.size());
    assertEquals("1 customer type subscribed for this tariff", 1, customerSubscriptionCounts.get(spec).size());
    assertEquals("customer count is 1", 1, customerSubscriptionCounts.get(spec).get(podunk).intValue());

    
    // test
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                  7*24 + 1, 
                                  TariffTransaction.Type.WITHDRAW, // withdraw
                                  spec,
                                  podunk, 
                                  1,                               // 1 customers 
                                  0, 
                                  0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("1 subscribed tariff", 1, customerSubscriptionCounts.size());
    assertEquals("1 customer type subscribed for this tariff", 1, customerSubscriptionCounts.get(spec).size());
    assertEquals("customer count is 0", 0, customerSubscriptionCounts.get(spec).get(podunk).intValue());

    
    // test another ERROR
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                  7*24 + 1, 
                                  TariffTransaction.Type.WITHDRAW, // withdraw
                                  spec,
                                  podunk, 
                                  3,                               // 3 customers 
                                  0, 
                                  0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("1 subscribed tariff", 1, customerSubscriptionCounts.size());
    assertEquals("1 customer type subscribed for this tariff", 1, customerSubscriptionCounts.get(spec).size());
    assertEquals("withdrawing more customers than you have: customer count is 0", 0, customerSubscriptionCounts.get(spec).get(podunk).intValue());

    
    //production transactions shouldn't affect consumption
    TariffSpecification productionSpec = new TariffSpecification(brokerContext.getBroker(), PowerType.PRODUCTION);
    tariffRepo.addSpecification(productionSpec);
    CustomerInfo producer = new CustomerInfo("producer", 1).withPowerType(PowerType.PRODUCTION);
    customerRepo.add(producer);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                  7*24 + 1, 
                                  TariffTransaction.Type.SIGNUP, // withdraw
                                  productionSpec ,
                                  producer, 
                                  1,                               // 3 customers 
                                  0, 
                                  0);
    CustomerInfo windmil = new CustomerInfo("windmil", 1).withPowerType(PowerType.WIND_PRODUCTION);
    customerRepo.add(windmil);
    ttx = new TariffTransaction(brokerContext.getBroker(), 
                                  7*24 + 1, 
                                  TariffTransaction.Type.SIGNUP, // withdraw
                                  productionSpec ,
                                  windmil , 
                                  1,                               // 3 customers 
                                  0, 
                                  0);
    portfolioManagerService.handleMessage(ttx);
    customerSubscriptionCounts = portfolioManagerService.getCustomerSubscriptions(PowerType.CONSUMPTION);
    assertEquals("no change when adding production tariff - 1 tariff returns", 1, customerSubscriptionCounts.size());
    assertEquals("no change when adding production tariff - 1 customer-type subscribed", 1, customerSubscriptionCounts.get(spec).size());
    
    
  }
  

  @Test
  public void  test_getCompetingTariffsThatCanBeUsedBy() {
    
    
    List<TariffSpecification> expected;
    List<TariffSpecification> actual;

    //expected.clear();
    //expected.add(spec_CONSUMPTION);
    expected = Arrays.asList(spec_CONSUMPTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.CONSUMPTION);
    assertTrue("used by CONSUMPTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_PRODUCTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.PRODUCTION);
    assertTrue("used by PRODUCTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_SOLAR_PRODUCTION, spec_PRODUCTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.SOLAR_PRODUCTION);
    assertTrue("used by SOLAR_PRODUCTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_WIND_PRODUCTION, spec_PRODUCTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.WIND_PRODUCTION);
    assertTrue("used by WIND_PRODUCTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_ELECTRIC_VEHICLE, spec_INTERRUPTIBLE_CONSUMPTION, spec_CONSUMPTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.ELECTRIC_VEHICLE);
    assertTrue("used by ELECTRIC_VEHICLE" + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_INTERRUPTIBLE_CONSUMPTION, spec_CONSUMPTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.INTERRUPTIBLE_CONSUMPTION);
    assertTrue("used by INTERRUPTIBLE_CONSUMPTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_THERMAL_STORAGE_CONSUMPTION, spec_INTERRUPTIBLE_CONSUMPTION, spec_CONSUMPTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanBeUsedBy(PowerType.THERMAL_STORAGE_CONSUMPTION);
    assertTrue("used by THERMAL_STORAGE_CONSUMPTION " + actual.toString(), equalLists(actual, expected));
  }

  @Test
  public void test_getCompetingTariffsThatCanUse() {
    
    List<TariffSpecification> expected;
    List<TariffSpecification> actual;

    expected = Arrays.asList(spec_CONSUMPTION, spec_ELECTRIC_VEHICLE, spec_INTERRUPTIBLE_CONSUMPTION, spec_THERMAL_STORAGE_CONSUMPTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.CONSUMPTION);
    assertTrue("can use CONSUMPTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_PRODUCTION, spec_SOLAR_PRODUCTION, spec_WIND_PRODUCTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.PRODUCTION);
    assertTrue("can use PRODUCTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_SOLAR_PRODUCTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.SOLAR_PRODUCTION);
    assertTrue("can use SOLAR_PRODUCTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_WIND_PRODUCTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.WIND_PRODUCTION);
    assertTrue("can use WIND_PRODUCTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_ELECTRIC_VEHICLE);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.ELECTRIC_VEHICLE);
    assertTrue("can use ELECTRIC_VEHICLE " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_INTERRUPTIBLE_CONSUMPTION, spec_THERMAL_STORAGE_CONSUMPTION, spec_ELECTRIC_VEHICLE);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.INTERRUPTIBLE_CONSUMPTION);
    assertTrue("can use INTERRUPTIBLE_CONSUMPTION " + actual.toString(), equalLists(actual, expected));

    expected = Arrays.asList(spec_THERMAL_STORAGE_CONSUMPTION);
    actual = portfolioManagerService.getCompetingTariffsThatCanUse(PowerType.THERMAL_STORAGE_CONSUMPTION);
    assertTrue("can use THERMAL_STORAGE_CONSUMPTION " + actual.toString(), equalLists(actual, expected));
  }
 
  /**
   * From http://stackoverflow.com/questions/16207718/java-compare-two-lists-object-values
   * NOTE: will not work with duplicate objects
   */
  private boolean equalLists(List<TariffSpecification> listA, List<TariffSpecification> listB) {
    return listA.size() == listB.size() && listA.containsAll(listB);
  }
  
}
