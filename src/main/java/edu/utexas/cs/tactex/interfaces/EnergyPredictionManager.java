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

import org.apache.commons.math3.linear.ArrayRealVector;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

public interface EnergyPredictionManager {

  /**
   * 
   * @param customerInfo
   * @return 
   * Returns an energy prediction from the current time until the end of the 7th
   * day excluding the current day, so it could be a for more then 7 days but
   * no more than 8
   */
    ArrayRealVector getPredictionForAbout7Days(CustomerInfo customerInfo,
        boolean customerPerspective, int currentTimeslot, boolean fixed);  
  /**
   * Returns an energy prediction from the current time until the end of the 7th
   * day excluding the current day for *all* customers, so it could be for more then 
   * 7 days but for no more than 8
   * @param customerPerspective 
   * @return
   */
  HashMap<CustomerInfo, ArrayRealVector> getAbout7dayPredictionForAllCustomers(
      boolean customerPerspective, int currentTimeslot, boolean fixed);

  
  HashMap<CustomerInfo, ArrayRealVector> getAbout7dayPredictionForCustomersOfType(
      PowerType consumption, boolean useCanUse, boolean customerPerspective, int currentTimeslot, boolean fixed);
  
  /**
   * returned usage prediction for 'targetTimeslot', given
   * that we are in 'currentTimeslot', for a 'subscribedPopulation' 
   * of a given customer 'cust' under tariff 'spec'
   * @param spec
   * @param cust
   * @param subscribedPopulation
   * @param targetTimeslot
   * @param currentTimeslot
   * @param tariffSubscriptions
   * @return
   */
  double getShiftedUsageFromBrokerPerspective(
      TariffSpecification spec,
      CustomerInfo cust,
      int subscribedPopulation,
      int targetTimeslot,
      int currentTimeslot,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> tariffSubscriptions);
  


}
