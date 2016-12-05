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

import java.util.HashMap;
import java.util.List;

import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.utils.BrokerUtils;

public class NoopMigrationPredictor extends SingleCustomerMigrationPredictor {

  @Override
  protected HashMap<TariffSpecification, Double> doPredictMigrationForSingleCustomer(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competingTariffs,
      int timeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions,
      CustomerInfo customer, TariffSpecification defaultSpec) {
    
    // get customer subscriptions
    HashMap<TariffSpecification, Integer> tariff2subscriptions =
        customer2tariffSubscriptions.get(customer);
    // convert to double
    HashMap<TariffSpecification, Double> result =
        BrokerUtils.initializeDoubleSubsMap(tariff2subscriptions);
    // remove competitor tariffs    
    for (TariffSpecification spec : competingTariffs) {
      result.remove(spec);
    }
    
    return result;
  }

}
