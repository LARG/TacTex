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

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

public interface UtilityEstimator {

  /**
   * estimates the utilities of {@link TariffSpecification}s and returns
   * sorted utilites mapped to the {@link TariffSpecification}s. There
   * would always be an entry with a null value - which represents the 
   * no-op: not publishing any tariff and staying with the status quo.
   * @param customer2NonShiftedEnergy 
   * @param pricePerKwhPredictionForAbout7Days TODO
   * @param suggestedSpecs: {@link TariffSpecification}s for which utilities estimated
   * @param tariffSubscriptions: customer subscriptions by tariff    
   * @param competingTariffs: a list of tariffs published by other brokers
   * @param customer2RelevantTariffCharges: computed charges for: my-existing-tariffs, competing tariffs, suggested tariffs
   * @param customer2estimatedEnergy: predicted energy consumption of each (single) customer
   * @param marketPredictionManager: market (flat) cost predictions for the coming week
   * @param costCurvesPredictor: a service class that predicts unit cost curves
   * @param currentTimeslot: down propagating the current time
   * @param me: my broker instance
   * @return
   */
  TreeMap<Double, TariffSpecification> estimateUtilities(
      List<TariffSpecification> consideredTariffActions,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy, 
      MarketPredictionManager marketPredictionManager,
      CostCurvesPredictor costCurvesPredictor, int currentTimeslot, Broker me);

  TreeMap<Double, TariffSpecification> estimateRevokeUtilities(
      List<TariffSpecification> consideredTariffActions,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      MarketPredictionManager marketPredictionManager,
      CostCurvesPredictor costCurvesPredictor, int currentTimeslot, Broker me);  
}
