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
import java.util.List;

import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.interfaces.OpponentPredictor;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.utils.BrokerUtils;

public class CustomerMigrationPredictor {

  static private Logger log = Logger.getLogger(CustomerMigrationPredictor.class);
  
  private TariffRepoMgr tariffRepoMgr;

  private SingleCustomerMigrationPredictor chain; // chain of responsibility pattern
  

  public CustomerMigrationPredictor(SingleCustomerMigrationPredictor chain, TariffRepoMgr tariffRepoMgr) {
    this.chain = chain;
    this.tariffRepoMgr = tariffRepoMgr;
  }

  public HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictMigrationForRevoke(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariff2customerSubscriptions,
      List<TariffSpecification> competingTariffs,
      OpponentPredictor opponentPredictor, boolean useOppPred, int currentTimeslot) {
    
    // revert order for convenience
    HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> 
        customer2tariffSubscriptions = 
            BrokerUtils.revertKeyMapping(tariff2customerSubscriptions);
    
    //BrokerUtils.print2LevelMap(customer2tariffSubscriptions);
    
    // partial observability: add dummy subscriptions for the rest of the population
    addDummySubscriptions(customer2tariffEvaluations, competingTariffs,
        customer2tariffSubscriptions);    
    
    // allocate predicted subscriptions structure - initialize from current, so
    // it includes both production and consumption
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>>
        predictedSubscriptions = BrokerUtils.initializePredictedFromCurrentSubscriptions(customer2tariffSubscriptions); 
    
    // for each customer that can use candidateSpec, predict migration using
    // evaluator
    for (CustomerInfo customer : customer2tariffSubscriptions.keySet()) {
      // We want to predict migration for all customers, whether or not
      // they can subscribe to candidateSpec
      if (true || null == candidateSpec || customer.getPowerType().canUse(candidateSpec.getPowerType())) {
        TariffSpecification defaultSpec = BrokerUtils.getDefaultSpec(competingTariffs, customer.getPowerType());

        // Save tariff and temporarily increase its evaluation - restore it later
        Double origEvaluation = null;
        if (null != candidateSpec) {
          origEvaluation = customer2tariffEvaluations.get(customer).get(candidateSpec);
          double defaultSpecEval = customer2tariffEvaluations.get(customer).get(defaultSpec);
          customer2tariffEvaluations.get(customer).put(candidateSpec, 2 * defaultSpecEval );
        } 
        
        HashMap<TariffSpecification, Double> result = chain.predictMigrationForSingleCustomer(
            candidateSpec, customer2tariffEvaluations, competingTariffs, currentTimeslot,
            customer2tariffSubscriptions, customer, defaultSpec);
        
        predictedSubscriptions.put(customer, result);
        
        if (null != candidateSpec) {
          if (null == origEvaluation) {
            log.error("How come origEvaluation is null?");
          }
          customer2tariffEvaluations.get(customer).put(candidateSpec, origEvaluation);
        }
      }
    }
    
    // revert key order to return the predicted
    HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedTariff2customerSubscriptions = BrokerUtils.revertKeyMapping(predictedSubscriptions );
    
    return predictedTariff2customerSubscriptions;
  }


  public HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictMigration(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariff2customerSubscriptions,
      List<TariffSpecification> competingTariffs,
      OpponentPredictor opponentPredictor, boolean useOppPred, int currentTimeslot) {

    // revert order for convenience
    HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> 
        customer2tariffSubscriptions = 
            BrokerUtils.revertKeyMapping(tariff2customerSubscriptions);
    
    //BrokerUtils.print2LevelMap(customer2tariffSubscriptions);
    
    // partial observability: add dummy subscriptions for the rest of the population
    addDummySubscriptions(customer2tariffEvaluations, competingTariffs,
        customer2tariffSubscriptions);    
    
    // allocate predicted subscriptions structure - 
    // initialize from current, so it includes both 
    // production and consumption
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>>
        predictedSubscriptions = BrokerUtils.initializePredictedFromCurrentSubscriptions(customer2tariffSubscriptions); 
    
    // =============== add hypothetical data =============
    
    // add tmp spec to repo (currently needed only for ServerBasedMigrationPredictor)
    if (null != candidateSpec) {
      tariffRepoMgr.addToRepo(candidateSpec);
    }
    
    // Note: opponent prediction is incomplete and was never used in real games
    List<TariffSpecification> competitorResponses = new ArrayList<TariffSpecification>();
    if (false && useOppPred) {
      
      // predict competitor responses - only for fixed-rate tariffs 
      // (assuming TOU is used in a best-response, equilibrium game)
      if (currentTimeslot > 360 + 4 * 6                                             // o don't try to predict in first 4 publication cycles: predict only after having at least 3 points (s,as,as,a)
          && BrokerUtils.getNumberOfBrokers() == 2                                  // o only predict in 2-agent games
          && null != candidateSpec                                                  // o only predict for non noop action 
          && candidateSpec.getPowerType() == PowerType.CONSUMPTION                  // o only predict for consumption tariffs
          && candidateSpec.getRates().size() == 1                                   // o only predict for fixed-rate tariffs
          && isGameInTransientPhase(competingTariffs, candidateSpec.getPowerType()) // o only predict if game in 'price-battle' phase
      ) {

        // select one of the following two:
        // 1. 'matching' opponent:
        //competitorRates = predictOpponentRates(candidateSpec, currentTimeslot);
        //
        // 2. linear regression for opponent prediction:
        List<Double> competitorRates = opponentPredictor.predictOpponentRates(candidateSpec.getRates().get(0).getValue(), currentTimeslot);
        //
        competitorResponses = convertRatesToSpecs(
          competitorRates, candidateSpec, competingTariffs);
      }
      
    }
    // add competitor responses temporarily to repo 
    addCompetitorResponsesData(candidateSpec, customer2tariffEvaluations,
        competitorResponses);
    
    // =============== end add hypothetical data =============
    
    
    // for each customer that can use candidateSpec, 
    // predict migration using evaluator
    for (CustomerInfo customer : customer2tariffSubscriptions.keySet()) {
      // We want to predict migration for all customers, whether or not they
      // can subscribe to candidateSpec
      if (true || null == candidateSpec || customer.getPowerType().canUse(candidateSpec.getPowerType())) {
        TariffSpecification defaultSpec = BrokerUtils.getDefaultSpec(competingTariffs, customer.getPowerType());
        
        HashMap<TariffSpecification, Double> result = chain.predictMigrationForSingleCustomer(
            candidateSpec, customer2tariffEvaluations, competingTariffs, currentTimeslot,
            customer2tariffSubscriptions, customer, defaultSpec);
        
        predictedSubscriptions.put(customer, result);
      }
    }

    // revert key order to return the predicted
    HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedTariff2customerSubscriptions = BrokerUtils.revertKeyMapping(predictedSubscriptions );
    
    
    // ============ cleanup hypothetical data ==============
    
    // clean tmp spec from repo
    if (null != candidateSpec) {
      tariffRepoMgr.removeTmpSpecFromRepo(candidateSpec);
    }
    // remove competitor tariffs in case left (dummy subscriptions were removed
    // in chain only for types of candidateSpec.getPowerType()) filtering out
    // competitors
    for (TariffSpecification spec : competingTariffs) {
        predictedTariff2customerSubscriptions.remove(spec);
    }
    // clean and eliminate any predicted competitor responses
    cleanPredictedCompetitorResponses(customer2tariffEvaluations,
        competitorResponses, predictedTariff2customerSubscriptions);

    // =========== end cleanup hypothetical data ===========
    
    //BrokerUtils.print2LevelMap(BrokerUtils.revertKeyMapping(predictedTariff2customerSubscriptions));
    
    return predictedTariff2customerSubscriptions;
  }


  /**
   * TODO: CUTCORNERS: a rule of thumb to determine whether game 
   * is in transient phase for a given PowerType.
   * @param competingTariffs
   * @param powerType 
   * @return
   */
  private boolean isGameInTransientPhase(
      List<TariffSpecification> competingTariffs, PowerType powerType) {
    return BrokerUtils.isGameInTransientPhase(competingTariffs, powerType); 
  }


  private void addCompetitorResponsesData(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competitorResponses) {
    
    for (TariffSpecification spec : competitorResponses) {  
      tariffRepoMgr.addToRepo(spec);
      // add competitorResponse eval for all tariffs 
      for ( HashMap<TariffSpecification, Double> spec2eval : customer2tariffEvaluations.values()) {
        Double eval = spec2eval.get(candidateSpec);
        if ( eval != null ) {// could be null e.g. for prod-tariff if we are evaluating cons-tariffs 
          spec2eval.put(spec, eval);
        }
      }
    }
  }


  /**
   * clean and eliminate any predicted competitor responses
   * @param customer2tariffEvaluations
   * @param competitorResponses
   * @param predictedTariff2customerSubscriptions
   */
  private void cleanPredictedCompetitorResponses(
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competitorResponses,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedTariff2customerSubscriptions) {
    
    for (TariffSpecification spec : competitorResponses) {
      // clean from repo
      tariffRepoMgr.removeTmpSpecFromRepo(spec);
      // clean from evaluations 
      for ( HashMap<TariffSpecification, Double> spec2eval : customer2tariffEvaluations.values()) {
        spec2eval.remove(spec);
      }
      // clean from predicted subs
      predictedTariff2customerSubscriptions.remove(spec);
    }
  }


  /**
   * currently only for fixed-rate tariffs
   * @param candidateSpec
   * @param currentTimeslot 
   * @param competingTariffs
   * @return
   */
  private List<Double> predictOpponentRates(
      TariffSpecification candidateSpec, int currentTimeslot) {
    
    List<Double> result = new ArrayList<Double>();
    
    if (candidateSpec.getRates().size() == 1) {
      result.add(candidateSpec.getRates().get(0).getValue());
    }
    
    return result;
  }


  private List<TariffSpecification> 
      convertRatesToSpecs(
            List<Double> rates,
            TariffSpecification candidateSpec,
            List<TariffSpecification> competingTariffs) {
    
    List<TariffSpecification> result = new ArrayList<TariffSpecification>();

    // find competing broker
    Broker me = candidateSpec.getBroker();
    Broker broker = null;
    for (TariffSpecification competing : competingTariffs) {
      broker = competing.getBroker();
      if (isCompetitor(broker, me)) 
        break;
    }
    if ( broker != null ) {
      // copy my tariff and add to repo
      for (Double rate : rates) {
        TariffSpecification competitorResponse = new TariffSpecification(broker, candidateSpec.getPowerType());
        Rate copy = new Rate().withValue(rate);
        competitorResponse.addRate(copy);
        result.add(competitorResponse);
      }
    }
    return result;
  }
    

  /**
   * @param broker
   * @param me 
   * @return
   */
  private boolean isCompetitor(Broker broker, Broker me) {
    return ! broker.getUsername().equals("default broker") &&
            ! broker.getUsername().equals(me.getUsername());
  }
    

  private void addDummySubscriptions(
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competingTariffs,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions) {
    // best/worst/median competing
    for (CustomerInfo customer : customer2tariffSubscriptions.keySet()) {
      TariffSpecification bestCompeting = findBestCompetingTariffPerCustomer(customer, 
      //TariffSpecification bestCompeting = findWorstCompetingTariffPerCustomer(customer, 
      //TariffSpecification bestCompeting = findMedianCompetingTariffPerCustomer(customer, 
          competingTariffs, 
          customer2tariffEvaluations.get(customer));
      HashMap<TariffSpecification, Integer> tariffs2subs = customer2tariffSubscriptions.get(customer);
      int subscribedToMe = BrokerUtils.sumMapValues(tariffs2subs);
      int population = customer.getPopulation();
      int notSubscribedToMe = population - subscribedToMe;
      if (notSubscribedToMe > 0) {
        tariffs2subs.put(bestCompeting, notSubscribedToMe);
      }
    }
  }

  
  /*
   * Assumptions:
   * - ignoring inconvenience (which is computed inside the tariff evaluator..)
   *   thus will not work well with non-fixed-rates
   * - copied from regression based migration predictor
   */
  private TariffSpecification findBestCompetingTariffPerCustomer(
      CustomerInfo customer, List<TariffSpecification> competingTariffs,
      HashMap<TariffSpecification, Double> tariff2evaluation) {
    
    log.debug("currently comparing competing tariffs based on generic powertypes");
    
    double bestEval = -Double.MAX_VALUE;
    TariffSpecification bestSpec = null;
    
    for (TariffSpecification spec : competingTariffs) {
      if (customer.getPowerType().canUse(spec.getPowerType())) {
        double currentEval = tariff2evaluation.get(spec);
        if (currentEval > bestEval) {
          bestEval = currentEval;
          bestSpec = spec;
        }
      }
    }
    return bestSpec;
  }


  /*
   * Assumptions:
   * - ignoring inconvenience (which is computed inside the tariff evaluator..)
   *   thus will not work well with non-fixed-rates
   * - copied from regression based migration predictor
   */
  private TariffSpecification findWorstCompetingTariffPerCustomer(
      CustomerInfo customer, List<TariffSpecification> competingTariffs,
      HashMap<TariffSpecification, Double> tariff2evaluation) {
    
    log.debug("currently comparing competing tariffs based on generic powertypes");
    
    double bestEval = Double.MAX_VALUE;
    TariffSpecification bestSpec = null;
    for (TariffSpecification spec : competingTariffs) {
      if (customer.getPowerType().canUse(spec.getPowerType())) {
        double currentEval = tariff2evaluation.get(spec);
        if (currentEval < bestEval) {
          bestEval = currentEval;
          bestSpec = spec;
        }
      }
    }
    return bestSpec;
  }


  /*
   * Assumptions:
   * - ignoring inconvenience (which is computed inside the tariff evaluator..)
   *   thus will not work well with non-fixed-rates
   * - copied from regression based migration predictor
   */
  private TariffSpecification findMedianCompetingTariffPerCustomer(
      CustomerInfo customer, List<TariffSpecification> competingTariffs,
      HashMap<TariffSpecification, Double> tariff2evaluation) {
    
    log.debug("currently comparing competing tariffs based on generic powertypes");
    // find all evaluations of relevant tariffs
    ArrayList<Double> evals = new ArrayList<Double>();    
    for (TariffSpecification spec : competingTariffs) {
      if (customer.getPowerType().canUse(spec.getPowerType())) {
        double currentEval = tariff2evaluation.get(spec);
        evals.add(currentEval);
      }
    }
    
    // sort evaluations
    Collections.sort(evals);
    int medianIndex = evals.size() / 2 + 1;
    double medianEval = evals.get(medianIndex);
    
    // an inefficient loop to find spec with evaluation of medianEval
    for (TariffSpecification spec : competingTariffs) {
      if (customer.getPowerType().canUse(spec.getPowerType())) {
    
        if (tariff2evaluation.get(spec) - medianEval < 1e-6) { // float equality
          return spec;
        }
      }
    }
    log.error("how come didn't find median?");

    return null;
  }
}
