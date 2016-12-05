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

public class TariffOptimizerOneShot extends TariffOptimizerBase {
  
  private static Logger log = Logger.getLogger(TariffOptimizerOneShot.class);


  private TariffSuggestionMaker tariffSuggestionMaker;

  private UtilityEstimator utilityEstimator;

  private MarketPredictionManager marketPredictionManager;

  //@Autowired   - doesn't work
  private ConfiguratorFactoryService configuratorFactoryService;



  public TariffOptimizerOneShot(
      WithdrawFeesOptimizer withdrawFeesOptimizer,
      TariffRepoMgr tariffRepoMgr,
      TariffSuggestionMaker tariffSuggestionMaker,
      UtilityEstimator utilityEstimator, MarketPredictionManager marketPredictionManager,
      ChargeEstimator chargeEstimator,
      ShiftingPredictor shiftingPredictor,
      // We typically don't send ConfiguratorFactoryService in the
      // constructor, however autowiring doesn't work here (since it's not a
      // service)
      ConfiguratorFactoryService configuratorFactoryService) {
    super(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor);
    this.tariffSuggestionMaker = tariffSuggestionMaker;
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
        tariffSuggestionMaker.suggestTariffs(tariffSubscriptions,
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
                allSpecs ,
                currentTimeslot);

    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> 
    customer2RelevantTariffCharges = 
        estimateRelevantTariffCharges(
            suggestedSpecs, tariffSubscriptions, competingTariffs,
            customer2ShiftedEnergy);

    // add withdraw fees based on the estimates charges per customer
    if (configuratorFactoryService.isUseFees()) {
      withdrawFeesOptimizer.addWithdrawFeeAndMinDuration(suggestedSpecs, customer2RelevantTariffCharges);
    }

    // all possible tariff actions: {suggestedSpeces} U {no-op}
    // a value of null means no-op
    List<TariffSpecification> consideredTariffActions = new ArrayList<TariffSpecification>();
    consideredTariffActions.addAll(suggestedSpecs); 
    consideredTariffActions.add(null);
    
    TreeMap<Double, TariffSpecification> sortedTariffs = 
        utilityEstimator.estimateUtilities(consideredTariffActions,
                                           tariffSubscriptions,
                                           competingTariffs,
                                           customer2RelevantTariffCharges,
                                           customer2ShiftedEnergy,
                                           customer2estimatedEnergy,
                                           marketPredictionManager, 
                                           costCurvesPredictor, 
                                           currentTimeslot,
                                           me);
    return sortedTariffs;
  }
}
