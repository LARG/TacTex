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
package edu.utexas.cs.tactex.subscriptionspredictors;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.springframework.beans.factory.annotation.Autowired;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.utils.BrokerUtils;

/**
 * @author urieli
 *
 */
public class RegressionBasedMigrationPredictor extends
    SingleCustomerMigrationPredictor {

  static private Logger log = Logger.getLogger(RegressionBasedMigrationPredictor.class);

  private ConfiguratorFactoryService configuratorFactoryService;
  
  public RegressionBasedMigrationPredictor(ConfiguratorFactoryService configuratorFactoryService) {
    this.configuratorFactoryService = configuratorFactoryService;
  }  
  
  //
  //
  //
  //
  // ++++++++++++++++++++++++ this code is based on the simulator's code +++++++++++++++++++++++++++++++++
  public double normalizeWithDefaultTariff(
      HashMap<TariffSpecification, Double> allTariff2Evaluations,
      TariffSpecification defaultSpec, double charge) {
    if (configuratorFactoryService.isUseNormEval()) {
      double defaultEval = allTariff2Evaluations.get(defaultSpec);
      double evaluation = (defaultEval  - charge) / defaultEval;
      if (defaultSpec.getPowerType() == PowerType.PRODUCTION) {
        evaluation = -evaluation;
      }
      return evaluation;
    }
    else {
      return charge;
    }
  }
  // ++++++++++++++++++++++++ this code is based on the simulator's code +++++++++++++++++++++++++++++++++
  //
  //
  //
  //
  
  @Override
  protected HashMap<TariffSpecification, Double> doPredictMigrationForSingleCustomer(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competingTariffs,
      int timeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions,
      CustomerInfo customer, TariffSpecification defaultSpec) {
    return evaluateTariffs(customer2tariffSubscriptions.get(customer), 
                           defaultSpec,
                           customer2tariffEvaluations.get(customer),
                           competingTariffs, customer, candidateSpec, timeslot);
  }


  public HashMap<TariffSpecification,Double> evaluateTariffs(
    //private HashMap<TariffSpecification,Double> evaluateTariffs(
      HashMap<TariffSpecification, Integer> tariff2subscriptions,
      TariffSpecification defaultSpec,
      HashMap<TariffSpecification, Double> allTariff2Evaluations,
      List<TariffSpecification> competingTariffs,
      CustomerInfo customer,
      TariffSpecification candidateSpec, int timeslot) {

    HashMap<TariffSpecification,Double> result = new HashMap<TariffSpecification,Double>(); 
    
    TreeMap<Double, Double> 
        e2n = 
            initializeEvaluations2NumSubscribed(
                customer, tariff2subscriptions,
                allTariff2Evaluations, defaultSpec);
  
    // initialize the predicted customer distribution
    result = BrokerUtils.initializeDoubleSubsMap(tariff2subscriptions);
    
    // Assumption: currently assuming that 1) for null tariff, e.g. not publishing, the
    // subscriptions are unchanged, 2) when candidate PowerType is different than
    // customer's there is no customer migration. These assumptions are not necessarily 
    // true.
    if (null != candidateSpec && customer.getPowerType().canUse(candidateSpec.getPowerType())) {
      Double charge = allTariff2Evaluations.get(candidateSpec);
      double candidateEvaluation = normalizeWithDefaultTariff(allTariff2Evaluations, defaultSpec, charge);      
      updatePredictionWithCandidateSpec(result, candidateSpec,
          candidateEvaluation, e2n, customer, 
          customer.getPopulation(), timeslot);
    }

    //filtering out competitors
    for (TariffSpecification spec : competingTariffs) {
        result.remove(spec);
    }

    return result;
  }

  
  /**
   * @deprecated
   */
  @Deprecated
  public HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> oldPredictMigration(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariff2customerSubscriptions,
      List<TariffSpecification> competingTariffs,
      int timeslot) {

    if (null == candidateSpec) {
      return BrokerUtils.revertKeyMapping(
          BrokerUtils.initializePredictedFromCurrentSubscriptions(
              BrokerUtils.revertKeyMapping(tariff2customerSubscriptions)));
    } 
    
    // revert order for convenience
    HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> 
        customer2tariffSubscriptions = 
            BrokerUtils.revertKeyMapping(tariff2customerSubscriptions);
    
    // initialize the predicted customer distribution
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> 
      predictedSubscriptions = 
          BrokerUtils.initializePredictedFromCurrentSubscriptions(
              customer2tariffSubscriptions);
    
    for (CustomerInfo customer : customer2tariffSubscriptions.keySet()) {
     
      int customerPopulation = customer.getPopulation();

      HashMap<TariffSpecification, Integer> 
          myTariff2subscriptions = 
              customer2tariffSubscriptions.get(customer);
      
      HashMap<TariffSpecification, Double> 
          allTariff2Evaluations = 
              customer2tariffEvaluations.get(customer);
      
      TariffSpecification defaultSpec = BrokerUtils.getDefaultSpec(competingTariffs, customer.getPowerType());
      TreeMap<Double, Double> 
          e2n = 
              oldInitializeEvaluations2NumSubscribed(
                  customer, customerPopulation, myTariff2subscriptions,
                  competingTariffs, allTariff2Evaluations, defaultSpec);
      
      
      Double charge = allTariff2Evaluations.get(candidateSpec);
      double candidateEvaluation = normalizeWithDefaultTariff(allTariff2Evaluations, defaultSpec, charge);      
      oldUpdatePredictionWithCandidateSpec(predictedSubscriptions, candidateSpec,
          candidateEvaluation, e2n, customer, customerPopulation, timeslot);
      
    }
    
    // revert key order to return the predicted
    return BrokerUtils.revertKeyMapping(predictedSubscriptions);
  }


  /**
   * @deprecated
   * old code, just for comparison
   */
  @Deprecated
  TreeMap<Double,Double> oldInitializeEvaluations2NumSubscribed(
      CustomerInfo customer, int customerPopulation,
      HashMap<TariffSpecification, Integer> myTariff2subscriptions,
      List<TariffSpecification> competingTariffs,
      HashMap<TariffSpecification, Double> allTariff2Evaluations, TariffSpecification defaultSpec) {
    TreeMap<Double, Double> 
        e2n = // evaluation -> num-subscriptions
            new TreeMap<Double, Double>(); 
  
    // loop to add evaluation -> #subs
    int totalSubscriptions = 0;
    for (TariffSpecification spec : myTariff2subscriptions.keySet()) {
      Integer numSubscriptions = myTariff2subscriptions.get(spec);
      double charge = allTariff2Evaluations.get(spec); 
      double evaluation = normalizeWithDefaultTariff(allTariff2Evaluations, defaultSpec, charge); 
      // if 0 subscriptions - ignoring - maybe this is an old spec
      if (numSubscriptions > 0) {
        e2n.put(evaluation, numSubscriptions.doubleValue());
        totalSubscriptions += numSubscriptions;
      }

    }
  
    // assume the rest of the customers are with the best competing tariff
    log.debug("assuming customers I don't have are with the best competing tariff");
    Double numNonSubscribed = (double) (customerPopulation - totalSubscriptions);
    if (numNonSubscribed > 0) {
      double charge = findPreferedCompetingTariff(customer.getPowerType().getGenericType(), competingTariffs, allTariff2Evaluations);
      double evaluation = normalizeWithDefaultTariff(allTariff2Evaluations, defaultSpec, charge);
      e2n.put(evaluation, numNonSubscribed);
      // just print for visualization
      //log.debug(" lwr Customer competing: " + customer + " {" + evaluation + "=" + numNonSubscribed + "}" );
    }
    else {
      // just print for visualization
      //log.debug(" lwr Customer competing: " + customer + " {" + "}" );	
    }
    return e2n;
  }


  /**
   * @deprecated
   * old code, just for comparison
   */
  @Deprecated
  void oldUpdatePredictionWithCandidateSpec(
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> predictedSubscriptions,
      TariffSpecification candidateSpec, double candidateEvaluation,
      TreeMap<Double,Double> e2n, CustomerInfo customer,
      int customerPopulation, int timeslot) {
    
    // predict fractional value of the new spec and hypotetically add it
    Double hypotheticalNumSubscriptions = 
        predictNumSubscriptions(candidateEvaluation, e2n, customer, timeslot);
    
    predictedSubscriptions.get(customer).put(candidateSpec, hypotheticalNumSubscriptions);
    // add to 1.0 and find normalization constant
    double normalizeConst = (double)customerPopulation / (customerPopulation + hypotheticalNumSubscriptions);
    // add new tariff, normalize, and add a map for this customer
    normalizeSubscriptions(predictedSubscriptions.get(customer), normalizeConst, customer, customerPopulation);
  }


  /**
   * generating regression data: a mapping from tariff evaluation to number
   * of customers subscribed to it
   *
   * @param customer
   * @param customerPopulation
   * @param tariff2subscriptions
   * @param allTariff2Evaluations
   * @param defaultSpec 
   * @return
   * 
   */
  public TreeMap<Double,Double> initializeEvaluations2NumSubscribed(
      CustomerInfo customer,
      HashMap<TariffSpecification, Integer> tariff2subscriptions,
      HashMap<TariffSpecification, Double> allTariff2Evaluations, TariffSpecification defaultSpec) {
    TreeMap<Double, Double> 
        e2n = // evaluation -> num-subscriptions
            new TreeMap<Double, Double>(); 
  
    // loop to add evaluation -> #subs
    int totalSubscriptions = 0;
    for (TariffSpecification spec : tariff2subscriptions.keySet()) {
      Integer numSubscriptions = tariff2subscriptions.get(spec);
      double charge = allTariff2Evaluations.get(spec); 
      double evaluation = normalizeWithDefaultTariff(allTariff2Evaluations, defaultSpec, charge); 
      // if 0 subscriptions - ignoring - maybe this is an old spec
      if (numSubscriptions > 0) {
        e2n.put(evaluation, numSubscriptions.doubleValue());
        totalSubscriptions += numSubscriptions;
      }

    } 
    return e2n;
  }
  
  /**
   * access level is just for testing purposes
   * 
   * @param predictedSubscriptions
   * @param candidateSpec
   * @param e2n
   * @param customer
   * @param customerPopulation
   * @param timeslot 
   * @param allTariff2Evaluations
   * 
   */
  public void updatePredictionWithCandidateSpec(
      HashMap<TariffSpecification, Double> customerSubscriptions,
      TariffSpecification candidateSpec, double candidateEvaluation,
      TreeMap<Double,Double> e2n, CustomerInfo customer,
      int customerPopulation, int timeslot) {
    // predict fractional value of the new spec and hypotetically add it
    Double hypotheticalNumSubscriptions = 
        predictNumSubscriptions(candidateEvaluation, e2n, customer, timeslot);
    customerSubscriptions.put(candidateSpec, hypotheticalNumSubscriptions);
    // add to 1.0 and find normalization constant
    double normalizeConst = (double)customerPopulation / (customerPopulation + hypotheticalNumSubscriptions);
    // add new tariff normalize everyone and add a map for this customer
    normalizeSubscriptions(customerSubscriptions, normalizeConst, customer, customerPopulation);
  }

  /**
   * given a training data of evaluation->subscriptions predict number of
   * subscriptions for a new evaluation point
   * 
   * @param candidateEval
   * @param e2n
   * @param customer 
   * @param timeslot 
   * @return
   * 
   */
   private Double predictNumSubscriptions(double candidateEval,
      TreeMap<Double, Double> e2n, CustomerInfo customer, int timeslot) {

    // need at least n=3(?) example since cross-v uses n-1?
    // or perhaps I need 4?
    if (e2n.size() > 2 && configuratorFactoryService.isUseLWR()) {
      Double res = configuratorFactoryService.getCandidateTariffSubsPredictor().predictNumSubs(candidateEval, e2n, customer, timeslot);
      if (null == res) {
        log.error("LWR returned null, falling back to interpolateOrNN()");
        return interpolateOrNN(candidateEval, e2n);
      }
      return res;
    }
    else {
      return interpolateOrNN(candidateEval, e2n);
    }
  }


  /**
   * This was a baseline, quick implementation that is no longer used.
   *
   * @param candidateEval
   * @param e2n
   * @return
   */
  private double interpolateOrNN(double candidateEval, TreeMap<Double,Double> e2n) {
    log.debug("We interpolate/extrapolate to predictNumSubscriptions()");
    
    Entry<Double, Double> highNeighbor = e2n.ceilingEntry(candidateEval);
    Entry<Double, Double> lowNeighbor = e2n.floorEntry(candidateEval);
    if (null == highNeighbor && null == lowNeighbor) {
      log.error("predictNumSubscriptions() no entries in evaluation map");
      return 0;
    }
    // extrapolate => nearest neighbor
    if (null == highNeighbor) 
      return lowNeighbor.getValue() + 1; // a little better than the lower one
    if (null == lowNeighbor) 
      return Math.max(0, highNeighbor.getValue() - 1); // a little worst then the higher one but not negative
    // if both exist interpolate
    double x1 = lowNeighbor.getKey();
    double x2 = highNeighbor.getKey();
    double y1 = lowNeighbor.getValue();
    double y2 = highNeighbor.getValue();
    double predicted = y1 + (y2 - y1) * (candidateEval - x1) / (x2 - x1);
    return (int)predicted;
  }

  /**
     * normalize values by the normalization const, taking
     * care of fractional parts
     * 
     * @param hashMap
     * @param normalizeConst
     * @param customer 
     * @param customerPopulation 
     *
     */
    private void normalizeSubscriptions(
        HashMap<TariffSpecification, Double> subscriptions, double normalizeConst, CustomerInfo customer, int customerPopulation) {
      
      List<Entry<TariffSpecification, Double>> sortedEntriesByNumSubs = BrokerUtils.sortByValues(subscriptions);
      double accumulatedFractional = 0;
      for (Entry<TariffSpecification, Double> entry : sortedEntriesByNumSubs) {
        TariffSpecification spec = entry.getKey();
        Double oldNumSubscriptions = entry.getValue();
        double newSubscriptions = oldNumSubscriptions * normalizeConst;
        log.debug("oldNumSubscriptions " + oldNumSubscriptions + " newSubscriptions " + newSubscriptions);
        subscriptions.put(spec, newSubscriptions); 
      }
      
      // sanity check
      Double totalSubs = BrokerUtils.sumMapValues(subscriptions);
      final double epsilon = 1e-6;
      if (totalSubs > customerPopulation + epsilon) {
        log.error("normalization issue: customer " + customer + " total subscriptions: " + totalSubs + " customerPopulation " + customerPopulation);
      }
    }

  /**
   * For a given customer, scan competing tariffs and return the one with 
   * the best evaluation
   * @param powerType 
   * 
   * @param customer
   * @param competingTariffs
   * @param customer2tariff2evaluation
   * @return
   * 
   */
  private double findPreferedCompetingTariff(
      PowerType genericPowerType, List<TariffSpecification> competingTariffs,
      HashMap<TariffSpecification, Double> tariff2evaluation) {
    
    log.debug("currently comparing competing tariffs based on generic powertypes");
    
    double bestEval = -Double.MAX_VALUE;
    
    for (TariffSpecification spec : competingTariffs) {
      
      if (spec.getPowerType().getGenericType() != genericPowerType)
        continue;
      
      double currentEval = tariff2evaluation.get(spec);
      if (currentEval > bestEval) {
        bestEval = currentEval;
      }
    }
    return bestEval;
  }

}
