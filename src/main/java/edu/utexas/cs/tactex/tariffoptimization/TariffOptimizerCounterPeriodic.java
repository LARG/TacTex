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
import edu.utexas.cs.tactex.utils.BrokerUtils;

/**
 * Implements a composite pattern that uses a default
 * tariff optimizer, unless competitor uses periodic
 * fees, and then it uses the TariffOptimizerOneShot
 * @author urieli
 */
public class TariffOptimizerCounterPeriodic implements TariffOptimizer {

  private TariffOptimizer tariffOptimizerBackup;
  private TariffOptimizer tariffOptimizerDefault;


  public TariffOptimizerCounterPeriodic(
      // parameters for composite
      TariffOptimizer tariffOptimizerBackup,
      TariffOptimizer tariffOptimizerDefault) {
    this.tariffOptimizerBackup = tariffOptimizerBackup;
    this.tariffOptimizerDefault = tariffOptimizerDefault;
  }


  @Override
    public TreeMap<Double, TariffSpecification> optimizeTariffs(
        HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
        HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy, List<TariffSpecification> competingTariffs,
        MarketManager marketManager, ContextManager contextManager, CostCurvesPredictor costCurvesPredictor,
        int currentTimeslot, Broker me) {
      // in 2 broker case, if opponent uses periodic payments
      // don't do binary tariff search due to non-convexity
      if (BrokerUtils.getNumberOfBrokers() == 2) {
        for (TariffSpecification spec : competingTariffs) {
          if (spec.getPeriodicPayment() != 0.0) {
            // competitor's tariff contains periodic payment - 
            // use backup (useful to avoid non-convexity of utility in binary
            // tariff search using TariffOptimizerBinaryOneShot)
            return this.tariffOptimizerBackup.optimizeTariffs(
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
      return this.tariffOptimizerDefault.optimizeTariffs(
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
