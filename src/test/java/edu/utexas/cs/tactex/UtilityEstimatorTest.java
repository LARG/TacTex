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
 */
/**
 * 
 */
package edu.utexas.cs.tactex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.interfaces.ChargeEstimator;
import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.CustomerPredictionManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.shiftingpredictors.ShiftingPredictorNoShifts;
import edu.utexas.cs.tactex.utilityestimation.UtilityEstimatorDefaultForConsumption;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

/**
 * @author urieli
 *
 */
public class UtilityEstimatorTest {
  private TariffRepoMgr tariffRepoMgr;
  private CustomerRepo customerRepo;
  private PowerTacBroker brokerContext;  
  private Broker thebroker;
  private ArrayRealVector estimatedMarketPrices;
  private CostCurvesPredictor costCurvesPredictor;
  private int currentTimeslot;
  private MarketPredictionManager marketPredictionManager;
  private CustomerPredictionManager customerPredictionManager;
  private double distributionFee;
  private double publicationFee;
  private ContextManager contextManager;
  private ConfiguratorFactoryService configuratorFactoryService;  
  private ChargeEstimator chargeEstimator;

  
  private UtilityEstimatorDefaultForConsumption utilityEstimatorDefault;
  private ShiftingPredictor shiftingPredictor;
  

  
  @Before
  public void setUp () throws Exception
  {
    tariffRepoMgr = mock(TariffRepoMgr.class);
    customerRepo = new CustomerRepo();
    
    brokerContext = mock(PowerTacBroker.class);
    thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);
    
    contextManager = mock(ContextManager.class);
    distributionFee = -1.5;
    publicationFee = -3000;
    when(contextManager.getDistributionFee()).thenReturn(distributionFee);
    when(contextManager.getPublicationFee()).thenReturn(publicationFee);
    
    estimatedMarketPrices = new ArrayRealVector(7*24, 100.0);
    marketPredictionManager = mock(MarketPredictionManager.class);
    when(marketPredictionManager.getPricePerKwhPredictionForAbout7Days()).thenReturn(estimatedMarketPrices);
    costCurvesPredictor = mock(CostCurvesPredictor.class);
    when(costCurvesPredictor.predictUnitCostKwh(any(Integer.class), any(Integer.class), any(Double.class), any(Double.class))).thenReturn(100.0); //assume always same price
    //
    // some timeslot, used in "predictUnitCostMwh()" but since it is mocked, it
    // doesn't matter what timeslot we use
    currentTimeslot = 400; 
    
    customerPredictionManager = mock(CustomerPredictionManager.class);
    

    chargeEstimator = mock(ChargeEstimator.class);    
    when(chargeEstimator.estimateCharge(any(ArrayRealVector.class), any(TariffSpecification.class))).thenReturn(0.1);

    shiftingPredictor = new ShiftingPredictorNoShifts();
    
    configuratorFactoryService = mock(ConfiguratorFactoryService.class);
    when(configuratorFactoryService.getContextManager()).thenReturn(contextManager);
    when(configuratorFactoryService.getMarketPredictionManager()).thenReturn(marketPredictionManager);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    //ConfiguratorFactoryService.GlobalConstants CONSTANTS = configuratorFactoryService.new GlobalConstants();
    //ReflectionTestUtils.setField(configuratorFactoryService, 
    //                             "CONSTANTS", 
    //                             CONSTANTS);
    

    //ReflectionTestUtils.setField(utilityEstimatorDefault,
    //                             "configuratorFactoryService",
    //                             configuratorFactoryService);

    utilityEstimatorDefault = new UtilityEstimatorDefaultForConsumption(contextManager, customerPredictionManager, configuratorFactoryService);
  }
  
  @Test
  public void testUtilityEstimation () { 
    CustomerInfo customer1 = new CustomerInfo("Austin", 2);
    CustomerInfo customer2 = new CustomerInfo("Dallas", 4);
    
    TariffSpecification spec1 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    TariffSpecification spec2 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);

    ArrayRealVector energy1 = new ArrayRealVector(7*24, 6.0);
    ArrayRealVector energy2 = new ArrayRealVector(7*24, 8.0);

    // allocate data structures used for utility computation
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>>
        customer2estimatedTariffCharges = 
            new HashMap<CustomerInfo, HashMap<TariffSpecification,Double>>();
    
    HashMap<CustomerInfo, ArrayRealVector> 
        customer2energy = 
            new HashMap<CustomerInfo, ArrayRealVector>();

    HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>>
        currentCustomerSubscriptions = 
            new HashMap<TariffSpecification, HashMap<CustomerInfo,Integer>>();

    HashMap<TariffSpecification, HashMap<CustomerInfo, Double>>
        predictedCustomerSubscriptions = 
            new HashMap<TariffSpecification, HashMap<CustomerInfo,Double>>(); 

    
    customer2estimatedTariffCharges.put(customer1, new HashMap<TariffSpecification,Double>());
    customer2estimatedTariffCharges.put(customer2, new HashMap<TariffSpecification,Double>());

    currentCustomerSubscriptions.put(spec1, new HashMap<CustomerInfo,Integer>());
    currentCustomerSubscriptions.put(spec2, new HashMap<CustomerInfo,Integer>());

    predictedCustomerSubscriptions.put(spec1, new HashMap<CustomerInfo,Double>());
    predictedCustomerSubscriptions.put(spec2, new HashMap<CustomerInfo,Double>());
    


    // populate data structures with data from above
    
    // tariff charges - customer2 has 2x consumption so we did
    // 2x (it's probably not necessary for testing purposes)
    // remember that charge is per single population member of a customer
    customer2estimatedTariffCharges.get(customer1).put(spec1, -10.0);
    customer2estimatedTariffCharges.get(customer1).put(spec2, -20.0);
    customer2estimatedTariffCharges.get(customer2).put(spec1, -30.0); 
    customer2estimatedTariffCharges.get(customer2).put(spec2, -40.0);

    // energy consumption vector of customers
    customer2energy.put(customer1, energy1);
    customer2energy.put(customer2, energy2);

    // tests: incrementally update the following subscriptions and check
    // utility
    //tariffSubscriptions.get(spec1).put(customer1, 1);
    //tariffSubscriptions.get(spec2).put(customer1, 1);
    //tariffSubscriptions.get(spec1).put(customer2, 2);
    //tariffSubscriptions.get(spec2).put(customer2, 2);
    
    HashMap<CustomerInfo,HashMap<TariffSpecification,ShiftedEnergyData>> 
        customerTariff2ShiftedEnergy = 
            shiftingPredictor.updateEstimatedEnergyWithShifting(
                customer2energy, predictedCustomerSubscriptions, 0);

    // test: empty subscriptions
    double u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, 
        marketPredictionManager, costCurvesPredictor, 0);
    // test for both cost-curves and old method (avg) should give same result
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    assertEquals("utility of empty subscriptions", 0, u, 1e-6);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    assertEquals("utility of empty subscriptions", 0, u, 1e-6);


    // test: update subscription, verify utility
    predictedCustomerSubscriptions.get(spec1).put(customer1, 1.0);
    customerTariff2ShiftedEnergy = 
        shiftingPredictor.updateEstimatedEnergyWithShifting(
            customer2energy, predictedCustomerSubscriptions, 0);

    double expectedTariffIncome = 1 * -(-10.0);              // 1 customer x estimated charge of -10.0 per customer
    double expectedMarketCharges = -1 * (7*24 * 6.0)  * 100; // -(1 customer x total-energy x energy price)
    double expectedBalancing = 0;                            // currently estimated as 0
    double expectedDistribution = 1 * (7*24 * 6.0) * -1.5;   // 1 customer x total-energy x distribution-fee
    double expectedWithdrawFees = 0;                         // the above tariffs don't have withdraw fees
    double expected = expectedTariffIncome + expectedMarketCharges + expectedBalancing + expectedDistribution + expectedWithdrawFees;

    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, 
        marketPredictionManager, costCurvesPredictor, 0);
    // test for both cost-curves and old method (avg) should give same result
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    assertEquals("utility of spec1->customer1(1)", expected, u, 1e-6);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    assertEquals("utility of spec1->customer1(1)", expected, u, 1e-6);



    // test: update subscription, verify utility
    predictedCustomerSubscriptions.get(spec2).put(customer1, 1.0);
    customerTariff2ShiftedEnergy = 
        shiftingPredictor.updateEstimatedEnergyWithShifting(
            customer2energy, predictedCustomerSubscriptions, 0);

    expectedTariffIncome += 1 * -(-20.0);              // 1 customer x estimated charge of -20.0 per customer
    expectedMarketCharges += -1 * (7*24 * 6.0)  * 100; // -(1 customer x total-energy x energy price)
    expectedBalancing += 0;                            // currently estimated as 0
    expectedDistribution += 1 * (7*24 * 6.0) * -1.5;   // 1 customer x total-energy x distribution-fee
    expectedWithdrawFees += 0;                         // the above tariffSubscriptions don't have withdraw fees
    expected = expectedTariffIncome + expectedMarketCharges + expectedBalancing + expectedDistribution + expectedWithdrawFees;

    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, 
        marketPredictionManager, costCurvesPredictor, 0);
    // test for both cost-curves and old method (avg) should give same result
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    assertEquals("utility of spec2->customer1(1)", expected, u, 1e-6);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    assertEquals("utility of spec2->customer1(1)", expected, u, 1e-6);



    // test: update subscription, verify utility
    predictedCustomerSubscriptions.get(spec1).put(customer2, 2.0);
    customerTariff2ShiftedEnergy = 
        shiftingPredictor.updateEstimatedEnergyWithShifting(
            customer2energy, predictedCustomerSubscriptions, 0);

    expectedTariffIncome += 2 * -(-30.0);              // 2 customer x estimated charge of -30.0 per customer
    expectedMarketCharges += -2 * (7*24 * 8.0)  * 100; // -(2 customer x total-energy x energy price)
    expectedBalancing += 0;                            // currently estimated as 0
    expectedDistribution += 2 * (7*24 * 8.0) * -1.5;   // 2 customer x total-energy x distribution-fee
    expectedWithdrawFees += 0;                         // the above tariffSubscriptions don't have withdraw fees
    expected = expectedTariffIncome + expectedMarketCharges + expectedBalancing + expectedDistribution + expectedWithdrawFees;

    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, 
        marketPredictionManager, costCurvesPredictor, 0);
    // test for both cost-curves and old method (avg) should give same result
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    assertEquals("utility of spec1->customer2(2)", expected, u, 1e-6);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    assertEquals("utility of spec1->customer2(2)", expected, u, 1e-6);


    // test: update subscription, verify utility
    predictedCustomerSubscriptions.get(spec2).put(customer2, 2.0);
    customerTariff2ShiftedEnergy = 
        shiftingPredictor.updateEstimatedEnergyWithShifting(
            customer2energy, predictedCustomerSubscriptions, 0);

    expectedTariffIncome += 2 * -(-40.0);              // 2 customer x estimated charge of -40.0 per customer
    expectedMarketCharges += -2 * (7*24 * 8.0)  * 100; // -(2 customer x total-energy x energy price)
    expectedBalancing += 0;                            // currently estimated as 0
    expectedDistribution += 2 * (7*24 * 8.0) * -1.5;   // 2 customer x total-energy x distribution-fee
    expectedWithdrawFees += 0;                         // the above tariffSubscriptions don't have withdraw fees
    expected = expectedTariffIncome + expectedMarketCharges + expectedBalancing + expectedDistribution + expectedWithdrawFees;

    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, 
        marketPredictionManager, costCurvesPredictor, 0);
    // test for both cost-curves and old method (avg) should give same result
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    assertEquals("utility of spec2->customer2(2)", expected, u, 1e-6);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    assertEquals("utility of spec2->customer2(2)", expected, u, 1e-6);


    // test: changing prediction method works market prediction decreased by
    // half for costCurvesPredictor only
    when(costCurvesPredictor.predictUnitCostKwh(any(Integer.class), any(Integer.class), any(Double.class), any(Double.class))).
        thenReturn(-50.0); 
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(true);
    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, 
        marketPredictionManager, costCurvesPredictor, 0);
    assertEquals("setting predictions method using a boolean - curves", expected + 0.5 * Math.abs(expectedMarketCharges), u, 1e-6);
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, marketPredictionManager, costCurvesPredictor, 0);
    assertEquals("setting predictions method using a boolean - mkt-avg", expected, u, 1e-6);

    
    
    // ======================
    // test for withdraw fees
    // ======================
    
    // testing from scratch, re-allocating everything
    customer1 = new CustomerInfo("Austin", 2);
    customer2 = new CustomerInfo("Dallas", 4);
    
    // creating tariffs with early withdraw payments.
    // The minDuration is set to 0 so that if we ever account
    // for subscription durations in our utility architecture,
    // this test will fail. The reason is that a min duration of
    // 0 should cause no payment, but currently we 
    // assume any migration from revoked tariff pays and 
    // ignore the min duration. 
    spec1 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION)
                                     .withEarlyWithdrawPayment(-1.234)
                                     .withMinDuration(0);
    spec2 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION)
                                     .withEarlyWithdrawPayment(-2.345)
                                     .withMinDuration(0);

    energy1 = new ArrayRealVector(7*24, 0.0); // no consumption, we only test fees
    energy2 = new ArrayRealVector(7*24, 0.0); // no consumption, we only test fees

    // allocate data structures used for utility computation
    customer2estimatedTariffCharges = 
        new HashMap<CustomerInfo, HashMap<TariffSpecification,Double>>();

    customer2energy = 
        new HashMap<CustomerInfo, ArrayRealVector>();

    currentCustomerSubscriptions = 
        new HashMap<TariffSpecification, HashMap<CustomerInfo,Integer>>();

    predictedCustomerSubscriptions = 
        new HashMap<TariffSpecification, HashMap<CustomerInfo,Double>>(); 

    
    customer2estimatedTariffCharges.put(customer1, new HashMap<TariffSpecification,Double>());
    customer2estimatedTariffCharges.put(customer2, new HashMap<TariffSpecification,Double>());

    // initializing current subscriptions to:
    // spec1: customer1=>2, customer2=>0
    // spec2: customer1=>0, customer2=>1
    // 2 and 1 to break symmetry when testing 
    currentCustomerSubscriptions.put(spec1, new HashMap<CustomerInfo,Integer>());
    currentCustomerSubscriptions.put(spec2, new HashMap<CustomerInfo,Integer>());
    //
    currentCustomerSubscriptions.get(spec1).put(customer1, 2);
    currentCustomerSubscriptions.get(spec1).put(customer2, 0);
    currentCustomerSubscriptions.get(spec2).put(customer1, 0);
    currentCustomerSubscriptions.get(spec2).put(customer2, 1);
    
    // initializing predicted subscriptions to:
    // spec1: customer1=>0, customer2=>1
    // spec2: customer1=>2, customer2=>0
    predictedCustomerSubscriptions.put(spec1, new HashMap<CustomerInfo,Double>());
    predictedCustomerSubscriptions.put(spec2, new HashMap<CustomerInfo,Double>());
    //
    predictedCustomerSubscriptions.get(spec1).put(customer1, 0.0);
    predictedCustomerSubscriptions.get(spec1).put(customer2, 1.0);
    predictedCustomerSubscriptions.get(spec2).put(customer1, 2.0);
    predictedCustomerSubscriptions.get(spec2).put(customer2, 0.0);
    

    // populate data structures with data from above
    
    // estimated tariff charges per customer are 0 - we only test fees
    customer2estimatedTariffCharges.get(customer1).put(spec1, 0.0);
    customer2estimatedTariffCharges.get(customer1).put(spec2, 0.0);
    customer2estimatedTariffCharges.get(customer2).put(spec1, 0.0); 
    customer2estimatedTariffCharges.get(customer2).put(spec2, 0.0);

    // energy consumption vector of customers
    customer2energy.put(customer1, energy1);
    customer2energy.put(customer2, energy2);

    customerTariff2ShiftedEnergy = 
        shiftingPredictor.updateEstimatedEnergyWithShifting(
            customer2energy, predictedCustomerSubscriptions, 0);

    
    // test: migration => fee payments
    when(configuratorFactoryService.isUseCostCurves()).thenReturn(false);
    u = utilityEstimatorDefault.estimateUtility(currentCustomerSubscriptions,
        predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customerTariff2ShiftedEnergy, 
        customer2energy, marketPredictionManager, costCurvesPredictor, 0);
    // 2/1 migration from tariff, '-' is to convert to broker perspective
    expected = -(2 * -1.234 - 2.345); 
    assertEquals("utility of withdraw fees ", expected, u, 1e-6);

    
  }
  
  @Test
  public void testAddAndRemoveTariffEvaluation() {
    CustomerInfo customer1 = new CustomerInfo("Austin", 2);
    CustomerInfo customer2 = new CustomerInfo("Dallas", 4);
    
    TariffSpecification spec1 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    TariffSpecification spec2 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);

    ArrayRealVector energy1 = new ArrayRealVector(7*24, 6.0);
    ArrayRealVector energy2 = new ArrayRealVector(7*24, 8.0);

    // allocate data structures used for utility computation
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>>
        customer2estimatedTariffCharges = 
            new HashMap<CustomerInfo, HashMap<TariffSpecification,Double>>();
    
    HashMap<CustomerInfo, ArrayRealVector> 
        customer2energy = 
            new HashMap<CustomerInfo, ArrayRealVector>();
   
    customer2estimatedTariffCharges.put(customer1, new HashMap<TariffSpecification,Double>());
    customer2estimatedTariffCharges.put(customer2, new HashMap<TariffSpecification,Double>());


    // populate data structures with data from above
    
    // tariff charges - customer2 has 2x consumption so we did
    // 2x (it's probably not necessary for testing purposes)
    // remember that charge is per single population member of a customer
    customer2estimatedTariffCharges.get(customer1).put(spec1, -10.0);
    customer2estimatedTariffCharges.get(customer1).put(spec2, -20.0);
    customer2estimatedTariffCharges.get(customer2).put(spec1, -30.0); 
    customer2estimatedTariffCharges.get(customer2).put(spec2, -40.0);

    // energy consumption vector of customers
    customer2energy.put(customer1, energy1);
    customer2energy.put(customer2, energy2);

    // verify initial state
    assertEquals("number of customers in initial set", 2, customer2estimatedTariffCharges.size());
    assertEquals("number of specs per customer1", 2, customer2estimatedTariffCharges.get(customer1).size());
    assertEquals("number of specs per customer2", 2, customer2estimatedTariffCharges.get(customer2).size());

    
  }
}
  
