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

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.TariffOptimizer;

/**
 * Implements a composite pattern that in the first time uses
 * tariffOptimizerFirstTime, and from then on uses
 * tariffOptimizerSecondToLastTime
 * @author urieli
 *
 */
public class TariffOptimizerFirstTimeDifferent implements TariffOptimizer {
  
  private boolean firstTime = true;

  private TariffOptimizer tariffOptimizerFirstTime;
  private TariffOptimizer tariffOptimizerSecondToLastTime;
  

  public TariffOptimizerFirstTimeDifferent(
      // parameters for composite
      TariffOptimizer tariffOptimizerFirstTime,
      TariffOptimizer tariffOptimizerSecondToLastTime) {

    this.tariffOptimizerFirstTime = tariffOptimizerFirstTime;
    this.tariffOptimizerSecondToLastTime = tariffOptimizerSecondToLastTime;
    this.firstTime = true;

  }

  @Override
  public TreeMap<Double, TariffSpecification> optimizeTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      List<TariffSpecification> competingTariffs, MarketManager marketManager,
      ContextManager contextManager, CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot, Broker me) {

    if (this.firstTime) {
      
      this.firstTime = false;

      return tariffOptimizerFirstTime.optimizeTariffs(
        tariffSubscriptions,
        customer2estimatedEnergy,
        competingTariffs, 
        marketManager,
        contextManager, 
        costCurvesPredictor,
        currentTimeslot, 
        me);

    }
    else {
      return tariffOptimizerSecondToLastTime.optimizeTariffs(
        tariffSubscriptions,
        customer2estimatedEnergy,
        competingTariffs, 
        marketManager,
        contextManager, 
        costCurvesPredictor,
        currentTimeslot, 
        me);
    }
  }
}
