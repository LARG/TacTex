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
package edu.utexas.cs.tactex.subscriptionspredictors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.CustomerRepo;

import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;

/**
 * Predicting customer migration based on simulator's code.
 *
 * @author urieli
 *
 */
public class ServerBasedMigrationPredictor extends
    SingleCustomerMigrationPredictor {

  static private Logger log = Logger.getLogger(ServerBasedMigrationPredictor.class);

  // Used to override inconvenience due to TOU tariffs
  private static final double TOU_FACTOR_CAP = 0.05;
  
  private boolean firstTimePrinting = true;

  private TariffRepoMgr tariffRepoMgr;
  private CustomerRepo customerRepo;

  // initialized to empty containers
  private HashMap<CustomerInfo, CustomerXMLBasedParams> customer2params; 
  private HashMap<CustomerInfo, ServerBasedTariffEvaluator> customer2evaluator;


  public ServerBasedMigrationPredictor(TariffRepoMgr tariffRepoMgr, CustomerRepo customerRepo) {
    super();
    
    this.tariffRepoMgr = tariffRepoMgr;
    this.customerRepo = customerRepo;

    // initialized to empty containers
    this.customer2params = new HashMap<CustomerInfo, ServerBasedMigrationPredictor.CustomerXMLBasedParams>();
    this.customer2evaluator = new HashMap<CustomerInfo, ServerBasedTariffEvaluator>();

  }


  /**
   * ++++++++++++++++++++++++++++++++++++++++++++++++++
   * This function is based on server code
   * ++++++++++++++++++++++++++++++++++++++++++++++++++
   * This code will need to be updated in future versions
   * of the simulator if new customers are added, or existing
   * customers are removed, since it assumes it knows the 
   * set of simulated customers.
   */
  private void initializeCustomer2Params(CustomerRepo customerRepo) {
    customer2params = 
        new HashMap<CustomerInfo, ServerBasedMigrationPredictor.CustomerXMLBasedParams>();
    CustomerInfo cust;


    // factored-customers

    // parameters from TariffSubscriberStructure
    // TODO: ignoring inertia
    double touFactor = 0.05; // Math.min(0.05, TOU_FACTOR_CAP); // 0.01; //0.0; // 0.05;
    double interruptibilityFactor = 0.5;
    double variablePricingFactor = 0.7;
    double tieredRateFactor = 0.1;
    double tariffSwitchFactor = 0.1;
    double brokerSwitchFactor = 0.02;
    double divisor = (touFactor + interruptibilityFactor
            + variablePricingFactor + tieredRateFactor
            + tariffSwitchFactor + brokerSwitchFactor);
    touFactor /= divisor;
    interruptibilityFactor /= divisor;
    variablePricingFactor /= divisor;
    tieredRateFactor /= divisor;
    tariffSwitchFactor /= divisor;
    brokerSwitchFactor /= divisor;
    int expectedDuration = 14; // expected subscription duration, days
    double inconvenienceWeight = 0.2;
    Double tariffVolumeThreshold = 20000.0;

    // parameters from xml file 
    double logitChoiceRationality;
    double expMeanPriceWeight;
    double maxValuePriceWeight;
    double realizedPriceWeight;

    // parameters not from xml
    boolean isFactoredCustomer = true;

    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.75;
    cust = customerRepo.findByName("BrooksideHomes").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 1.0;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.95;
    cust = customerRepo.findByName("CentervilleHomes").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("DowntownOffices").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.95;
    cust = customerRepo.findByName("EastsideOffices").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.95;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("FrostyStorage").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("HextraChemical").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.75;
    cust = customerRepo.findByName("MedicalCenter@1").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.95;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.6;
    cust = customerRepo.findByName("MedicalCenter@2").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.95;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("SunnyhillSolar1").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    
    logitChoiceRationality = 0.95;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("SunnyhillSolar2").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    
    logitChoiceRationality = 0.95;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("WindmillCoOp@1").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 1.0;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("WindmillCoOp@2").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;
    maxValuePriceWeight = 0.4;
    realizedPriceWeight = 0.8;
    cust = customerRepo.findByName("SolarLeasing@1").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));



    // cold storage customers

    isFactoredCustomer = false;

    // TODO: ignoring regulation factors and inertia
    tariffSwitchFactor = 0.04;
    expectedDuration = 14; 
    inconvenienceWeight = 0.2;
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6; 
    maxValuePriceWeight = 0.4; 
    realizedPriceWeight = 0.8; 
    tariffVolumeThreshold = 10000.0; 
    touFactor = 0.0; // Math.min(0.0, TOU_FACTOR_CAP); // 0.01; //0.0; // 0.05;
    tieredRateFactor = 0.01; 
    variablePricingFactor = 0.0; 
    interruptibilityFactor = 0.0;
    cust = customerRepo.findByName("seafood-1").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    cust = customerRepo.findByName("seafood-2").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    cust = customerRepo.findByName("freezeco-1").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    cust = customerRepo.findByName("freezeco-2").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));
    cust = customerRepo.findByName("freezeco-3").get(0); 
    customer2params.put(cust, new CustomerXMLBasedParams(
                                                isFactoredCustomer,
                                                tariffSwitchFactor,
                                                expectedDuration, 
                                                inconvenienceWeight, 
                                                logitChoiceRationality, 
                                                expMeanPriceWeight, 
                                                maxValuePriceWeight, 
                                                realizedPriceWeight, 
                                                tariffVolumeThreshold, 
                                                touFactor, 
                                                tieredRateFactor, 
                                                variablePricingFactor, 
                                                interruptibilityFactor));


    // villages

    // TODO: ignoring intertia
    tariffSwitchFactor = 0.1; 
    expectedDuration = 14; // TODO: server actually samples a value in {1 * 7, 2 * 7, 3 * 7}
    inconvenienceWeight = 0.5; // TODO: server actually samples a value in [0..1]
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;        // TODO: didn't see initialization of that, assume using default
    maxValuePriceWeight = 0.4;       // TODO: didn't see initialization of that, assume using default
    realizedPriceWeight = 0.8;       // TODO: didn't see initialization of that, assume using default
    tariffVolumeThreshold = 10000.0; // TODO: didn't see initialization of that, assume using default
    touFactor = 0.05; // Math.min(0.05, TOU_FACTOR_CAP); // 0.01; //0.0; // 0.05;
    tieredRateFactor = 0.1;
    variablePricingFactor = 0.7;
    interruptibilityFactor = 0.5;
    String[] villages = {"Village 1 NS Base",
                       "Village 1 RaS Base",
                       "Village 1 NS Controllable",
                       "Village 1 ReS Base",
                       "Village 1 RaS Controllable",
                       "Village 1 SS Base",
                       "Village 1 ReS Controllable",
                       "Village 1 SS Controllable",
                       "Village 2 SS Controllable",
                       "Village 2 NS Base",
                       "Village 2 NS Controllable",
                       "Village 2 RaS Base",
                       "Village 2 RaS Controllable",
                       "Village 2 ReS Base",
                       "Village 2 ReS Controllable",
                       "Village 2 SS Base"};
    for (String village : villages) {
      cust = customerRepo.findByName(village).get(0); 
      customer2params.put(cust, new CustomerXMLBasedParams(
                                                  isFactoredCustomer,
                                                  tariffSwitchFactor,
                                                  expectedDuration, 
                                                  inconvenienceWeight, 
                                                  logitChoiceRationality, 
                                                  expMeanPriceWeight, 
                                                  maxValuePriceWeight, 
                                                  realizedPriceWeight, 
                                                  tariffVolumeThreshold, 
                                                  touFactor, 
                                                  tieredRateFactor, 
                                                  variablePricingFactor, 
                                                  interruptibilityFactor));
    } 

    
    // office complexes

    // TODO: ignoring intertia
    tariffSwitchFactor = 0.1; // 
    expectedDuration = 14; // TODO: server actually samples a value in {1 * 7, 2 * 7, 3 * 7}
    inconvenienceWeight = 0.5; // TODO: server actually samples a value in [0..1]
    logitChoiceRationality = 0.9;
    expMeanPriceWeight = 0.6;        // TODO: didn't see initialization of that, assume using default
    maxValuePriceWeight = 0.4;       // TODO: didn't see initialization of that, assume using default
    realizedPriceWeight = 0.8;       // TODO: didn't see initialization of that, assume using default
    tariffVolumeThreshold = 10000.0; // TODO: didn't see initialization of that, assume using default
    touFactor = 0.05; // Math.min(0.05, TOU_FACTOR_CAP); // 0.01; //0.0; // 0.05;
    tieredRateFactor = 0.1;
    variablePricingFactor = 0.7;
    interruptibilityFactor = 0.5;
    String[] officeComplexes = {"OfficeComplex 1 NS Base",
                                "OfficeComplex 2 NS Controllable",
                                "OfficeComplex 2 NS Base",
                                "OfficeComplex 2 SS Controllable",
                                "OfficeComplex 1 SS Base",
                                "OfficeComplex 2 SS Base",
                                "OfficeComplex 1 NS Controllable",
                                "OfficeComplex 1 SS Controllable"};
    for (String officeComplex : officeComplexes) {
      cust = customerRepo.findByName(officeComplex).get(0); 
      customer2params.put(cust, new CustomerXMLBasedParams(
                                                  isFactoredCustomer,
                                                  tariffSwitchFactor,
                                                  expectedDuration, 
                                                  inconvenienceWeight, 
                                                  logitChoiceRationality, 
                                                  expMeanPriceWeight, 
                                                  maxValuePriceWeight, 
                                                  realizedPriceWeight, 
                                                  tariffVolumeThreshold, 
                                                  touFactor, 
                                                  tieredRateFactor, 
                                                  variablePricingFactor, 
                                                  interruptibilityFactor));
    } 

  }


  @Override
  protected HashMap<TariffSpecification, Double> doPredictMigrationForSingleCustomer(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competingTariffs,
      int timeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions,
      CustomerInfo customer, TariffSpecification defaultSpec) {
    
    HashMap<TariffSpecification, Double> result = null;
    initializeCustomer2ParamsIfNeeded();
    
    ServerBasedTariffEvaluator evaluator = getTariffEvaluator(customer);
    if (null != evaluator) {
      result = evaluator.evaluateTariffs(customer2tariffSubscriptions.get(customer),
                                                                     defaultSpec,
                                                                     customer2tariffEvaluations.get(customer),
                                                                     competingTariffs, customer, candidateSpec);
    }
    
    return result;
  }


  private void initializeCustomer2ParamsIfNeeded() {
    if (customer2params.size() == 0) {
      initializeCustomer2Params(customerRepo);
    }    
  }


  private ServerBasedTariffEvaluator getTariffEvaluator(CustomerInfo customer) {
    ServerBasedTariffEvaluator result = customer2evaluator.get(customer);
    if (null == result) {
      
      CustomerXMLBasedParams params = customer2params.get(customer);      
      if (null == params) {
        if (firstTimePrinting) { // avoid printing more than once
          log.error("didn't find xml params for customer " + customer);
          firstTimePrinting = false;
        }
        return null;
      }
      
      result = new ServerBasedTariffEvaluator(new ServerCustomerModelAccessor(customer), tariffRepoMgr)
        .withChunkSize(Math.max(1, customer.getPopulation()/1000))
        .withTariffSwitchFactor(params.tariffSwitchFactor)
        .withPreferredContractDuration(params.expectedDuration)
        .withInconvenienceWeight(params.inconvenienceWeight)
        .withRationality(params.logitChoiceRationality)
        .withEvaluateAllTariffs(params.isFactoredCustomer);
      result.initializeCostFactors(params.expMeanPriceWeight,
                                      params.maxValuePriceWeight,
                                      params.realizedPriceWeight,
                                      params.tariffVolumeThreshold);
      result.initializeInconvenienceFactors(params.touFactor,
                                       params.tieredRateFactor,
                                       params.variablePricingFactor,
                                       params.interruptibilityFactor);
      customer2evaluator.put(customer, result);
    }
    return result;
  }
  

  public class CustomerXMLBasedParams {

    public CustomerXMLBasedParams(
        boolean isFactoredCustomer, double tariffSwitchFactor,
        double expectedDuration, double inconvenienceWeight,
        double logitChoiceRationality, double expMeanPriceWeight,
        double maxValuePriceWeight, double realizedPriceWeight,
        double tariffVolumeThreshold, double touFactor,
        double tieredRateFactor, double variablePricingFactor,
        double interruptibilityFactor) {
      this.isFactoredCustomer = isFactoredCustomer;
      this.tariffSwitchFactor = tariffSwitchFactor;
      this.expectedDuration = expectedDuration;
      this.inconvenienceWeight = inconvenienceWeight;
      this.logitChoiceRationality = logitChoiceRationality;
      this.expMeanPriceWeight = expMeanPriceWeight;
      this.maxValuePriceWeight = maxValuePriceWeight;
      this.realizedPriceWeight = realizedPriceWeight;
      this.tariffVolumeThreshold = tariffVolumeThreshold;
      this.touFactor = touFactor;
      this.tieredRateFactor = tieredRateFactor;
      this.variablePricingFactor = variablePricingFactor;
      this.interruptibilityFactor = interruptibilityFactor;
    }
    
    final public boolean isFactoredCustomer;
    final public double tariffSwitchFactor;
    final public double expectedDuration;
    final public double inconvenienceWeight;
    final public double logitChoiceRationality;
    final public double expMeanPriceWeight;
    final public double maxValuePriceWeight;
    final public double realizedPriceWeight;
    final public double tariffVolumeThreshold;
    final public double touFactor;
    final public double tieredRateFactor;
    final public double variablePricingFactor;
    final public double interruptibilityFactor;
  
  }
}
