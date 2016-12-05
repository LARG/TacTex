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
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.EnergyPredictionManager;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.TariffRevoker;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerRevoke;

/**
 * This class is a modification of StochasticTariffGenerator - both
 * are entry points to utility-arch
 * @author urieli
 *
 */
public class UtilityArchitectureTariffRevoker 
implements TariffRevoker {

  static private Logger log = Logger.getLogger(UtilityArchitectureTariffRevoker.class);

  // @Autowired replacements
  private EnergyPredictionManager energyPredictionManager;

  private TariffOptimizerRevoke tariffOptimizerRevoke;
  

  
  /**
   * @param energyPredictionManager
   * @param utilityEstimator
   */
  public UtilityArchitectureTariffRevoker(
      EnergyPredictionManager energyPredictionManager,
      TariffOptimizerRevoke tariffOptimizerRevoke) {
    super();
    this.energyPredictionManager = energyPredictionManager;
    this.tariffOptimizerRevoke = tariffOptimizerRevoke;
  }


  /**
   * Could be modified into a template method 
   */
  @Override
  public List<TariffSpecification> selectTariffsToRevoke(
      boolean useCanUse,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      MarketManager marketManager, 
      ContextManager contextManager, 
      CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot, Broker me) {
    
    boolean customerPerspective = true;
    HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy = 
        energyPredictionManager.
            getAbout7dayPredictionForAllCustomers(customerPerspective, currentTimeslot, /*false*/ true); // false: don't use fixed-rate only

    TreeMap<Double, TariffSpecification> sortedTariffs = tariffOptimizerRevoke.optimizeTariffs(
        tariffSubscriptions, customer2estimatedEnergy, competingTariffs, marketManager,
        contextManager, costCurvesPredictor, currentTimeslot, me);

    log.info("(Revoke) Estimated Utilities: ");
    for (Entry<Double, TariffSpecification> e : sortedTariffs.entrySet()){
      log.info("u: " + e.getKey() + " spec: " + e.getValue());
    }
    
    List<TariffSpecification> specsToAdd = selectTariffsToRevoke(
        currentTimeslot, sortedTariffs, tariffSubscriptions.keySet());

    return specsToAdd;
  }


  private List<TariffSpecification> selectTariffsToRevoke(
      int currentTimeslot,
      TreeMap<Double, TariffSpecification> sortedTariffs, 
      Set<TariffSpecification> myExistingTariffs) {
    Double bestUtil = sortedTariffs.lastEntry().getKey();
    TariffSpecification specToRevoke = sortedTariffs.lastEntry().getValue();

    // currently returning just one - the estimated best
    List<TariffSpecification> specsToRevoke = new ArrayList<TariffSpecification>();
    if( specToRevoke != null ) { // null is no-op
      specsToRevoke.add(specToRevoke);
    }
    return specsToRevoke;
  }

}
