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
package edu.utexas.cs.tactex.interfaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

/**
 * 
 * @author urieli
 *
 */
public abstract class TariffOptimizerBase 
implements TariffOptimizer {

  private static Logger log = Logger.getLogger(TariffOptimizerBase.class);

  private ShiftingPredictor shiftingPredictor;
  
  protected WithdrawFeesOptimizer withdrawFeesOptimizer;
  protected TariffRepoMgr tariffRepoMgr;
  protected ChargeEstimator chargeEstimator;

  public TariffOptimizerBase(WithdrawFeesOptimizer withdrawFeesOptimizer, TariffRepoMgr tariffRepoMgr, ChargeEstimator chargeEstimator, ShiftingPredictor shiftingPredictor) {
    this.shiftingPredictor = shiftingPredictor;
    this.withdrawFeesOptimizer = withdrawFeesOptimizer;
    this.tariffRepoMgr = tariffRepoMgr;
    this.chargeEstimator = chargeEstimator;
  }

  @Override
  abstract public TreeMap<Double, TariffSpecification> optimizeTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy, List<TariffSpecification> competingTariffs,
      MarketManager marketManager, ContextManager contextManager,
      CostCurvesPredictor costCurvesPredictor, int currentTimeslot,
      Broker me);

  
  protected HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> estimateShiftedPredictions(
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      //List<TariffSpecification> suggestedSpecs,
      //HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      //List<TariffSpecification> competingTariffs, 
      List<TariffSpecification> specs,
      int currentTimeslot) {
    
    

    HashMap<TariffSpecification,HashMap<CustomerInfo,Double>> 
        dummySubscriptions = 
            new HashMap<TariffSpecification, HashMap<CustomerInfo,Double>>();
    
    for (TariffSpecification spec : specs) {
      dummySubscriptions.put(spec, new HashMap<CustomerInfo, Double>());
      Set<CustomerInfo> customers = customer2estimatedEnergy.keySet();
      for (CustomerInfo customer : customers) {
        if (customer.getPowerType().canUse(spec.getPowerType())) {
          dummySubscriptions.get(spec).put(customer, 1.);
        }
      }
    }


    // predict shifting effects, or no shifting, depending on shiftingPredictor
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        customer2ShiftedEnergy = shiftingPredictor.updateEstimatedEnergyWithShifting(
            customer2estimatedEnergy, 
            dummySubscriptions,
            currentTimeslot);
    
    return customer2ShiftedEnergy;
  }


  
  
  /**
   * compute a mapping customer=>(tariff=>charge) for all relevant tariffs
   * Used by derived classes.
   * 
   * @param suggestedSpecs
   * @param tariffSubscriptions
   * @param competingTariffs
   * @param customer2ShiftedEnergy
   * @return
   */
  protected HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> estimateRelevantTariffCharges(List<TariffSpecification> suggestedSpecs, HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs, HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy) {
        addSuggestedSpecsToRepo(suggestedSpecs);
      
        List<TariffSpecification> relevantTariffs = 
            new ArrayList<TariffSpecification>(tariffSubscriptions.keySet());
        relevantTariffs.addAll(competingTariffs);
        relevantTariffs.addAll(suggestedSpecs);
        HashMap<CustomerInfo,HashMap<TariffSpecification, Double >>
          customer2estimatedTariffCharges = 
              chargeEstimator.estimateRelevantTariffCharges(
                  relevantTariffs, customer2ShiftedEnergy);
        
        removeTmpSpecsFromRepo(suggestedSpecs);
        
        return customer2estimatedTariffCharges;
      }

  /**
   * 
   */
  private void addSuggestedSpecsToRepo(List<TariffSpecification> suggestedSpecs) {
  
    log.info("temporarily adding suggested specs to repo");
  
    for (TariffSpecification spec : suggestedSpecs) {
      boolean success = tariffRepoMgr.addToRepo(spec);
      if ( ! success ) {
        log.error("failed to temporarily add spec to repo, spec-id: " + spec.getId());
      }
    }
  }

  /**
   *  
   * @param suggestedSpecs
   */
  private void removeTmpSpecsFromRepo(List<TariffSpecification> suggestedSpecs) {
  
    log.info("removing temporary specs from repo");
  
    HashSet<TariffSpecification> specsToRemove = new HashSet<TariffSpecification>();
    for (TariffSpecification spec : suggestedSpecs) {
      specsToRemove.add(spec);
    }    
    tariffRepoMgr.removeTmpSpecsFromRepo(specsToRemove);
  }

  protected TariffSpecification extractBestTariffSpec(TreeMap<Double, TariffSpecification> sortedTariffs) {
    Entry<Double, TariffSpecification> bestEntry = sortedTariffs.lastEntry();
    while (bestEntry != null && bestEntry.getValue() == null) {
      bestEntry = sortedTariffs.lowerEntry(bestEntry.getKey());  
    }
    if (bestEntry != null) {
      return bestEntry.getValue();
    }
    else {
      return null; 
    }
  }

}
