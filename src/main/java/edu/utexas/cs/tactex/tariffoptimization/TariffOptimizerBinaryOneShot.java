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
package edu.utexas.cs.tactex.tariffoptimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.interfaces.ChargeEstimator;
import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.interfaces.TariffSuggestionMaker;
import edu.utexas.cs.tactex.interfaces.UtilityEstimator;
import edu.utexas.cs.tactex.interfaces.WithdrawFeesOptimizer;
import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

public class TariffOptimizerBinaryOneShot extends TariffOptimizerBase {
  
  private static Logger log = Logger.getLogger(TariffOptimizerBinaryOneShot.class);


  private TariffSuggestionMaker consumptionTariffSuggestionMaker;

  private UtilityEstimator utilityEstimator;

  private MarketPredictionManager marketPredictionManager;

  //@Autowired   - doesn't work
  private ConfiguratorFactoryService configuratorFactoryService;



  public TariffOptimizerBinaryOneShot(
      WithdrawFeesOptimizer withdrawFeesOptimizer,
      TariffRepoMgr tariffRepoMgr,
      TariffSuggestionMaker consumptionTariffSuggestionMaker,
      UtilityEstimator utilityEstimator, MarketPredictionManager marketPredictionManager,
      ChargeEstimator chargeEstimator,
      ShiftingPredictor shiftingPredictor,
      // We typically don't send ConfiguratorFactoryService in the constructor,
      // however autowiring doesn't work here (since it's not a service) 
      ConfiguratorFactoryService configuratorFactoryService) {
    super(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor);
    this.consumptionTariffSuggestionMaker = consumptionTariffSuggestionMaker;
    this.utilityEstimator = utilityEstimator;
    this.marketPredictionManager = marketPredictionManager;
    this.configuratorFactoryService = configuratorFactoryService;
  }


  @Override
  public TreeMap<Double, TariffSpecification> optimizeTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy, List<TariffSpecification> competingTariffs,
      MarketManager marketManager, ContextManager contextManager,
      CostCurvesPredictor costCurvesPredictor, int currentTimeslot,
      Broker me) {
    
    List<TariffSpecification> suggestedSpecs = 
        consumptionTariffSuggestionMaker.suggestTariffs(tariffSubscriptions,
            competingTariffs, marketManager, contextManager, me);
    
    log.info("generateTariffsToPublish(): suggestedSpecs:");
    for (TariffSpecification spec : suggestedSpecs) {
      log.info(spec);
      for (Rate r : spec.getRates()) {
        log.info(r);
      }
    }
    
    List<TariffSpecification> allSpecs = new ArrayList<TariffSpecification>();
    allSpecs.addAll(suggestedSpecs);
    allSpecs.addAll(tariffSubscriptions.keySet());
    allSpecs.addAll(competingTariffs);
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        customer2ShiftedEnergy = 
            estimateShiftedPredictions(
                customer2estimatedEnergy, 
                allSpecs,
                currentTimeslot);

    //log.info("estimating tariff charges"); // 0.2 sec
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> 
    customer2RelevantTariffCharges = 
        estimateRelevantTariffCharges(
            suggestedSpecs, tariffSubscriptions, competingTariffs,
            customer2ShiftedEnergy);

    //log.info("adding withdraw fees");
    // add withdraw fees based on the estimates charges per customer
    if (configuratorFactoryService.isUseFees()) {
      withdrawFeesOptimizer.addWithdrawFeeAndMinDuration(suggestedSpecs, customer2RelevantTariffCharges);
    }

    //log.info("computing utilities");
    TreeMap<Double, TariffSpecification> sortedTariffs = binarySearchOptimize(
        suggestedSpecs, tariffSubscriptions, competingTariffs,
        costCurvesPredictor, currentTimeslot, me, customer2ShiftedEnergy,
        customer2estimatedEnergy,
        customer2RelevantTariffCharges);
    //log.info("done computing utilities"); // 4 seconds originally, now < 1 sec
    return sortedTariffs;
  }


  private TreeMap<Double, TariffSpecification> binarySearchOptimize(
      List<TariffSpecification> suggestedSpecs,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot,
      Broker me,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges) {
    
    TreeMap<Double, TariffSpecification> result = new TreeMap<Double, TariffSpecification>();
    
    // a value of null means no-op
    ArrayList<TariffSpecification> consideredTariffActions = new ArrayList<TariffSpecification>();
    consideredTariffActions.add(null);
    TreeMap<Double, TariffSpecification> sortedTariffs = 
        utilityEstimator.estimateUtilities(consideredTariffActions,
                                           tariffSubscriptions,
                                           competingTariffs,
                                           customer2RelevantTariffCharges,
                                           customer2ShiftedEnergy,
                                           customer2NonShiftedEnergy,
                                           marketPredictionManager, 
                                           costCurvesPredictor, 
                                           currentTimeslot,
                                           me);
    result.putAll(sortedTariffs);
    
    // here do the binary search
    //
    // initialize with edges and middle
    TreeMap<Double, Integer> utilToIndex = new TreeMap<Double, Integer>();
    int numTariffs = suggestedSpecs.size();
    int[] initialIndexes = {0, numTariffs / 2, numTariffs - 1};
    for (int index : initialIndexes) {
      evaluateAndRecord(index, utilToIndex, result, suggestedSpecs,
          consideredTariffActions, tariffSubscriptions, competingTariffs,
          costCurvesPredictor, currentTimeslot, me, customer2ShiftedEnergy,
          customer2NonShiftedEnergy,
          customer2RelevantTariffCharges);  
    }
    int bestIndex = utilToIndex.lastEntry().getValue();
    int secondBestIndex = utilToIndex.lowerEntry(utilToIndex.lastKey()).getValue();
    //
    // binary search
    while (Math.abs(secondBestIndex - bestIndex) >= 2) {
      //log.info("evaluating, bestIndex=" + bestIndex + ", secondBestIndex=" + secondBestIndex);
      int midIndex = (secondBestIndex + bestIndex ) / 2;
      evaluateAndRecord(midIndex, utilToIndex, result, suggestedSpecs,
          consideredTariffActions, tariffSubscriptions, competingTariffs,
          costCurvesPredictor, currentTimeslot, me, customer2ShiftedEnergy,
          customer2NonShiftedEnergy,
          customer2RelevantTariffCharges);
      bestIndex = utilToIndex.lastEntry().getValue();
      secondBestIndex = utilToIndex.lowerEntry(utilToIndex.lastKey()).getValue();
      
      // TODO: handling a non-convex case (how come happens?)
      if (midIndex != bestIndex && midIndex != secondBestIndex) {
        log.warn("non-convex utility values found during binary search. breaking...");
        break;
      }
    }
    //log.info("evaluating, bestIndex=" + bestIndex + ", secondBestIndex=" + secondBestIndex);
    
    return result;
  }


  /**
   * evaluate suggestedSpecs(index), and record result to utilToIndex
   * and result
   * 
   * @param index
   * @param utilToIndex
   * @param result
   * @param suggestedSpecs
   * @param consideredTariffActions
   * @param tariffSubscriptions
   * @param competingTariffs
   * @param costCurvesPredictor
   * @param currentTimeslot
   * @param me
   * @param customer2ShiftedEnergy
   * @param customer2RelevantTariffCharges
   * @param customer2NonShiftedEnergy 
   */
  private void evaluateAndRecord(
      int index,
      TreeMap<Double, Integer> utilToIndex,
      TreeMap<Double, TariffSpecification> result,
      List<TariffSpecification> suggestedSpecs,
      ArrayList<TariffSpecification> consideredTariffActions,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot,
      Broker me,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges) {
    TreeMap<Double, TariffSpecification> sortedTariffs;
    consideredTariffActions.clear();
    consideredTariffActions.add(suggestedSpecs.get(index));
    //log.info("computing utilities");
    sortedTariffs = 
        utilityEstimator.estimateUtilities(consideredTariffActions,
                                           tariffSubscriptions,
                                           competingTariffs,
                                           customer2RelevantTariffCharges,
                                           customer2ShiftedEnergy,
                                           customer2NonShiftedEnergy,
                                           marketPredictionManager, 
                                           costCurvesPredictor, 
                                           currentTimeslot,
                                           me);
    utilToIndex .put(sortedTariffs.lastEntry().getKey(), index);
    // maintain top 3
    if (utilToIndex.size() > 3) {
      utilToIndex.remove(utilToIndex.firstKey());
    }
    result.putAll(sortedTariffs);
  } 
}
