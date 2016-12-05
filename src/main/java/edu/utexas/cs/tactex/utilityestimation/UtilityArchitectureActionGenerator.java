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
 *     Copyright (c) 2012-2013 by the original author
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

package edu.utexas.cs.tactex.utilityestimation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffMessage;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.TariffRevoke;

import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.EnergyPredictionManager;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.TariffActionGenerator;
import edu.utexas.cs.tactex.interfaces.TariffOptimizer;
import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.UtilityEstimator;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerRevoke;

/**
 * This class implements a strategy of generating candidate
 * tariffs, sending them to evaluation, and choosing which
 * ones to publish based on their evaluation.
 * @author urieli
 *
 */
public class UtilityArchitectureActionGenerator 
implements TariffActionGenerator {

  static private Logger log = Logger.getLogger(UtilityArchitectureActionGenerator.class);

  // @Autowired replacements
  private EnergyPredictionManager energyPredictionManager;

  private TariffOptimizer tariffOptimizer;

  private TariffOptimizerRevoke tariffOptimizerRevoke;
  
  // used just for printing predicted vs. actual customer subscriptions
  public HashMap<TariffSpecification, HashMap<TariffSpecification, HashMap<CustomerInfo, Double>>> predictions;
    

  
  /**
   * @param energyPredictionManager
   * @param utilityEstimator
   */
  public UtilityArchitectureActionGenerator(
      EnergyPredictionManager energyPredictionManager,
      TariffOptimizer tariffOptimizer,
      TariffOptimizerRevoke tariffOptimizerRevoke) {
    super();
    this.energyPredictionManager = energyPredictionManager;
    this.tariffOptimizer = tariffOptimizer;
    this.tariffOptimizerRevoke = tariffOptimizerRevoke;
  }


  /**
   * Potential template method - any function call can be refactored to an interface
   */
  @Override
  public List<TariffMessage> selectTariffActions(
      boolean useCanUse,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      MarketManager marketManager, 
      ContextManager contextManager, 
      CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot,
      boolean useRevoke, 
      Broker me) {
    
    boolean customerPerspective = true;
    HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy = 
        energyPredictionManager.
            getAbout7dayPredictionForAllCustomers(customerPerspective, currentTimeslot, /*false*/ true); // false: don't use fixed-rate only, use the standard one
 
    TreeMap<Double, TariffSpecification> sortedTariffs = tariffOptimizer.optimizeTariffs(
        tariffSubscriptions, customer2estimatedEnergy, competingTariffs, marketManager,
        contextManager, costCurvesPredictor, currentTimeslot, me);

    log.info("Estimated Utilities: ");
    for (Entry<Double, TariffSpecification> e : sortedTariffs.entrySet()){
      log.info("u: " + e.getKey() + " spec: " + e.getValue());
    }

    TreeMap<Double, TariffMessage> sortedTariffActions = new TreeMap<Double, TariffMessage>();
    sortedTariffActions.putAll(sortedTariffs);

    if (useRevoke) {
      TreeMap<Double, TariffSpecification> sortedTariffRevokes = tariffOptimizerRevoke.optimizeTariffs(
          tariffSubscriptions, customer2estimatedEnergy, competingTariffs, marketManager,
          contextManager, costCurvesPredictor, currentTimeslot, me);

      log.info("(Revoke) Estimated Utilities: ");
      for (Entry<Double, TariffSpecification> e : sortedTariffRevokes.entrySet()){
        log.info("u: " + e.getKey() + " spec: " + e.getValue());
      }

      // fill util=>action
      for ( Entry<Double, TariffSpecification> entry : sortedTariffRevokes.entrySet()) {
        TariffSpecification spec = entry.getValue();
        if (null == spec) {
          sortedTariffActions.put(entry.getKey(), spec);
        } 
        else {
          sortedTariffActions.put(entry.getKey(), new TariffRevoke(me, spec));
        }
      }
    }

    List<TariffMessage> tariffActionsToExecute = selectBestActions(
        currentTimeslot, sortedTariffActions, tariffSubscriptions.keySet());

    return tariffActionsToExecute;
  }

  private List<TariffMessage> selectBestActions(
      int currentTimeslot,
      TreeMap<Double, TariffMessage> sortedTariffActions, 
      Set<TariffSpecification> myExistingTariffs) {
    Double bestUtil = sortedTariffActions.lastEntry().getKey();
    TariffMessage tariffAction = sortedTariffActions.lastEntry().getValue();
    if (currentTimeslot < 360 + 6) {
      if (null == tariffAction) {
        log.warn("Force publishing before ts 366");
        Entry<Double, TariffMessage> lowerEntry = sortedTariffActions.lowerEntry(sortedTariffActions.lastEntry().getKey());
        while (lowerEntry.getValue().getClass() != TariffSpecification.class) {
          lowerEntry = sortedTariffActions.lowerEntry(lowerEntry.getKey());
        }
        bestUtil = lowerEntry.getKey();
        tariffAction = lowerEntry.getValue();
      }
    }
    
    // currently returning just one - the estimated best
    List<TariffMessage> tariffActions = new ArrayList<TariffMessage>();
    if( tariffAction != null ) { // null is no-op
      tariffActions.add(tariffAction);
    }
    return tariffActions;
  }

  
  private TariffSpecification tieBreakForLowerRates(
      TreeMap<Double, TariffSpecification> sortedTariffs, 
      Double bestUtil,
      TariffSpecification specToAdd, 
      Set<TariffSpecification> myExistingTariffs) {
    log.info("specToAdd before tie-break: util = " + bestUtil + " spec = "+ specToAdd);
    final double UTILITY_TOLERANCE = 0.97; 
    double bestAvgRate = avgRate(specToAdd, myExistingTariffs);
    double currentUtil = bestUtil; // used just for printing
    for( Entry<Double, TariffSpecification> candidateEntry = sortedTariffs.lowerEntry(bestUtil); // initialize to next
         null != candidateEntry && candidateEntry.getKey() > bestUtil * UTILITY_TOLERANCE; // currently within 97% of best
         candidateEntry = sortedTariffs.lowerEntry(candidateEntry.getKey())) {  // advance to lower
      double currentAvgRate = avgRate(candidateEntry.getValue(), myExistingTariffs);
      if (currentAvgRate > bestAvgRate) { // i.e. more value for customer (since rates are negative)
        bestAvgRate = currentAvgRate;
        specToAdd = candidateEntry.getValue();
        currentUtil = candidateEntry.getKey(); // used just for printing
      }      
    }
    log.info("specToAdd after tie-break: util = " + currentUtil + " spec = " + specToAdd);
    return specToAdd;
  }

  private double avgRate(TariffSpecification specToAdd, Set<TariffSpecification> myExistingTariffs) {
    if (null == specToAdd) {
      return findMinConsumptionAvgRate(myExistingTariffs);
    }
    else {
      return computeAvgRate(specToAdd);
    }
  }

  private double findMinConsumptionAvgRate(Set<TariffSpecification> myExistingTariffs) {
    double minAvgRate = -Double.MAX_VALUE;
    for (TariffSpecification spec : myExistingTariffs) {
      if (spec.getPowerType() == PowerType.CONSUMPTION) {
        double avgRate = computeAvgRate(spec);
        if (avgRate > minAvgRate) { // note signs
          minAvgRate = avgRate;
        }
      }
    }    
    return minAvgRate;
  }

  private double computeAvgRate(TariffSpecification spec) {
    double total = 0;
    for (Rate r : spec.getRates()) {
      total += r.getValue();
    }
    return total / spec.getRates().size();
  }

}
