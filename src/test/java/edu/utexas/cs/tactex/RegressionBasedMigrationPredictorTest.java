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
package edu.utexas.cs.tactex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.ejml.simple.SimpleMatrix;
import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.subscriptionspredictors.RegressionBasedMigrationPredictor;

public class RegressionBasedMigrationPredictorTest {
  
  private PowerTacBroker brokerContext;
  private Broker thebroker;
  private TariffSpecification defaultConsumptionSpec;
  private TariffSpecification defaultProductionSpec;
  private ConfiguratorFactoryService configuratorFactoryService;
 
  RegressionBasedMigrationPredictor regressionBasedMigrationPredictor;

  
  @Before
  public void setUp () throws Exception
  {

    brokerContext = mock(PowerTacBroker.class);
    thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);

    defaultConsumptionSpec = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    Rate rate = new Rate().withValue(-0.500);
    defaultConsumptionSpec.addRate(rate);
    defaultProductionSpec = new TariffSpecification(brokerContext.getBroker(), PowerType.PRODUCTION);
    rate = new Rate().withValue(0.150);
    defaultProductionSpec.addRate(rate);
    
    configuratorFactoryService = mock(ConfiguratorFactoryService.class);
    
    when(configuratorFactoryService.isUseNormEval()).thenReturn(true);

    regressionBasedMigrationPredictor = new RegressionBasedMigrationPredictor(configuratorFactoryService);
    
  }
  

  /**
   * testing the function 
   * customerPredictionManagerService.updatePredictionWithCandidateSpec()
   */
  @Test
  public void test_normalizeWithDefaultTariff() {

    HashMap<TariffSpecification, Double> allTariff2Evaluations = 
        new HashMap<TariffSpecification, Double>();


    TariffSpecification spec1 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    TariffSpecification spec2 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    allTariff2Evaluations.put(spec1, -5.0);   
    allTariff2Evaluations.put(spec2, -10.0);  
    // add the default spec, should be higher (worse) than others
    allTariff2Evaluations.put(defaultConsumptionSpec, -50.0);


    double actual;
    double expected;
    // consumption tariffs
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultConsumptionSpec, -5.0);
    expected = 45/50.;
    assertEquals("normalizeWithDefaultTariff evaluation: -5", expected, actual, 1e-6);
    // 
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultConsumptionSpec, -10.0);
    expected = 40/50.;
    assertEquals("normalizeWithDefaultTariff evaluation: -10", expected, actual, 1e-6);
    // 
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultConsumptionSpec, 0.0);
    expected = 50/50.;
    assertEquals("normalizeWithDefaultTariff evaluation: 0", expected, actual, 1e-6);
    // 
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultConsumptionSpec, -50.0);
    expected = 0/50.;
    assertEquals("normalizeWithDefaultTariff evaluation: 0", expected, actual, 1e-6);
    //
    // production tariffs
    //
    // add the default spec, should be higher (worse) than others
    allTariff2Evaluations.put(defaultProductionSpec, 15.0);
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultProductionSpec, 35.0);
    expected = 20/15.;
    assertEquals("normalizeWithDefaultTariff evaluation: 35", expected, actual, 1e-6);
    //
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultProductionSpec, 30.0);
    expected = 15/15.;
    assertEquals("normalizeWithDefaultTariff evaluation: 30", expected, actual, 1e-6);
    //
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultProductionSpec, 20.0);
    expected = 5/15.;
    assertEquals("normalizeWithDefaultTariff evaluation: 20", expected, actual, 1e-6);
    // 
    actual = regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
        allTariff2Evaluations, defaultProductionSpec, 15.0);
    expected = 0/15.;
    assertEquals("normalizeWithDefaultTariff evaluation: 15", expected, actual, 1e-6);
  }


  /**
   * testing the function CustomerPredictionManagerService.initializeEvaluations2NumSubscribed()
   */
  @Test
  public void test_initializeEvaluations2NumSubscribed() {
    int customerPopulation = 10;
    CustomerInfo customer = new CustomerInfo("Austin", customerPopulation).withPowerType(PowerType.CONSUMPTION);
            
    HashMap<TariffSpecification, Integer> myTariff2subscriptions = new HashMap<TariffSpecification, Integer>();
    TariffSpecification myspec1 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    TariffSpecification myspec2 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    // no subscriptions yet...
    myTariff2subscriptions.put(myspec1, 0);
    myTariff2subscriptions.put(myspec2, 0);
    

    List<TariffSpecification> competingTariffs = new ArrayList<TariffSpecification>();
    TariffSpecification otherBrokerSpec1 = new TariffSpecification(new Broker("otherbroker1"), PowerType.CONSUMPTION);
    TariffSpecification otherBrokerSpec2 = new TariffSpecification(new Broker("otherbroker2"), PowerType.CONSUMPTION);
    TariffSpecification otherBrokerSpec3 = new TariffSpecification(new Broker("otherbroker2"), PowerType.PRODUCTION);
    competingTariffs.add(otherBrokerSpec1);
    competingTariffs.add(otherBrokerSpec2);
    competingTariffs.add(otherBrokerSpec3);

    
    HashMap<TariffSpecification, Double> allTariff2Evaluations = new HashMap<TariffSpecification, Double>();
    allTariff2Evaluations.put(myspec1, -5.0);   // my tariff
    allTariff2Evaluations.put(myspec2, -10.0);  // my tariff
    allTariff2Evaluations.put(otherBrokerSpec1, -1.0);  // best overall tariff by other broker
    allTariff2Evaluations.put(otherBrokerSpec2, -20.0); // worst overall tariff 
    allTariff2Evaluations.put(otherBrokerSpec3, +10.0); // production tariff - we should ignore it
    // add the default spec, should be higher (worse) than others
    allTariff2Evaluations.put(defaultConsumptionSpec, -100.0);
    
    
    TreeMap<Double, Double> actual;
    TreeMap<Double, Double> expected = new TreeMap<Double, Double>();
    
    
    // test - I have no subscriptions
    actual = 
        regressionBasedMigrationPredictor.initializeEvaluations2NumSubscribed(
            customer, myTariff2subscriptions, allTariff2Evaluations, defaultConsumptionSpec);

    assertTrue("when there are no subscriptions, return empty set", actual.equals(expected));
    
    // test - I have 5 subscriptions to one of the specs
    myTariff2subscriptions.put(myspec1, 5);
    actual = 
        regressionBasedMigrationPredictor.initializeEvaluations2NumSubscribed(
            customer, myTariff2subscriptions, allTariff2Evaluations, defaultConsumptionSpec);
    expected.clear();
    //// shouldn't show ones with 0 subscriptions because maybe they are old
    expected.put(
        regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
            allTariff2Evaluations, defaultConsumptionSpec, -5.0), 
                5.0); // mine
    
    assertTrue("1 tariff 5 subscriptions", actual.equals(expected));

    // test - I have 5 + 2 subscriptions to one of the specs
    myTariff2subscriptions.put(myspec2, 2);
    actual = 
        regressionBasedMigrationPredictor.initializeEvaluations2NumSubscribed(
            customer, myTariff2subscriptions, allTariff2Evaluations, defaultConsumptionSpec);
    expected.clear();
    expected.put(
        regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
            allTariff2Evaluations, defaultConsumptionSpec, -5.0), 
                5.0); // mine
    expected.put(
        regressionBasedMigrationPredictor.normalizeWithDefaultTariff(
            allTariff2Evaluations, defaultConsumptionSpec, -10.0), 
                2.0); // mine
    assertTrue("2 tariffs, 5 + 2 subscriptions", actual.equals(expected));
  }
  
  /**
   * testing the function 
   * customerPredictionManagerService.updatePredictionWithCandidateSpec()
   */
  @Test
  public void test_updatePredictionWithCandidateSpec() {
    int customerPopulation = 18;
    CustomerInfo customer = new CustomerInfo("Austin", customerPopulation).withPowerType(PowerType.CONSUMPTION);
            
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> predictedSubscriptions = new HashMap<CustomerInfo, HashMap<TariffSpecification,Double>>();
    predictedSubscriptions.put(customer, new HashMap<TariffSpecification, Double>());
        
    TariffSpecification myspec1 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    TariffSpecification myspec2 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    // no subscriptions yet...
    predictedSubscriptions.get(customer).put(myspec1, 6.0);
    predictedSubscriptions.get(customer).put(myspec2, 12.0);
    
    TreeMap<Double, Double> e2n = new TreeMap<Double, Double>();
    e2n.put(-4.0, 6.0);
    e2n.put(-2.0, 12.0);
    
    TariffSpecification candidateSpec = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    double candidateEvaluation = -3; 
    

    // test: interpolation
    int randomtimeslot3 = 333;
    regressionBasedMigrationPredictor.updatePredictionWithCandidateSpec(
        predictedSubscriptions.get(customer), candidateSpec, candidateEvaluation, e2n, customer, customerPopulation, randomtimeslot3);
    // prediction should predict 9 for candidate and then normalize by multiplying by 18.0/27.0 = 0.6666
    assertEquals("original customers for each spec are multiplied by 0.66666", 4, predictedSubscriptions.get(customer).get(myspec1), 1e-6);
    assertEquals("number of customers for candidate is the average between existing subscriptions", 6, predictedSubscriptions.get(customer).get(candidateSpec), 1e-6);
    assertEquals("original customers for each spec are multiplied by 0.66666", 8, predictedSubscriptions.get(customer).get(myspec2), 1e-6);
    
    // test: extrapolation (high)
    predictedSubscriptions.get(customer).clear();
    predictedSubscriptions.get(customer).put(myspec2, (double) customerPopulation); // population is 18
    e2n.clear();
    e2n.put(-2.0, 18.0); // will extrapolate since there is only 1
    candidateEvaluation = -1.0; // higher  => should be 18+1 before normalization
    int randomtimeslot4 = 444;
    regressionBasedMigrationPredictor.updatePredictionWithCandidateSpec(
        predictedSubscriptions.get(customer), candidateSpec, candidateEvaluation, e2n, customer, customerPopulation, randomtimeslot4);
    assertEquals("original customers for each spec are multiplied by 18/(18+19) + accumulatedFraction", 8.756756756756758, predictedSubscriptions.get(customer).get(myspec2), 1e-6);
    assertEquals("number of customers for candidate is extrapolated by +1 and multiplied by 18/(18+19)", 9.243243243243244, predictedSubscriptions.get(customer).get(candidateSpec), 1e-6);


    // test: extrapolation (low)
    predictedSubscriptions.get(customer).clear();
    predictedSubscriptions.get(customer).put(myspec2, (double) customerPopulation); // population is 18
    e2n.clear();
    e2n.put(-2.0, 18.0); // will extrapolate since there is only 1
    candidateEvaluation = -3.0; // lower  => should be 18-1 before normalization  
    int randomtimeslot5 = 555;
    regressionBasedMigrationPredictor.updatePredictionWithCandidateSpec(
        predictedSubscriptions.get(customer), candidateSpec, candidateEvaluation, e2n, customer, customerPopulation, randomtimeslot5);
    assertEquals("original customers for each spec are multiplied by 18/(18+17) + accumulatedFraction", 9.257142857142856, predictedSubscriptions.get(customer).get(myspec2), 1e-6);
    assertEquals("number of customers for candidate is extrapolated by -1 and multiplied by 18/(18+17)", 8.742857142857142, predictedSubscriptions.get(customer).get(candidateSpec), 1e-6);
  }
  

}
