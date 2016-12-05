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
package edu.utexas.cs.tactex.utilityestimation;

import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;

import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TimeService;

import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.WithdrawFeesOptimizer;
import edu.utexas.cs.tactex.utils.BrokerUtils;

public class WithdrawFeesOptimizerHalfAvgCharge implements WithdrawFeesOptimizer {

  static private Logger log = Logger.getLogger(WithdrawFeesOptimizer.class);


  public WithdrawFeesOptimizerHalfAvgCharge() {
    super();
  }


  @Override
  public void addWithdrawFeeAndMinDuration(List<TariffSpecification> suggestedSpecs, HashMap<CustomerInfo, 
            HashMap<TariffSpecification, Double>> customer2estimatedTariffCharges) {
    
    HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> 
        tariff2estimatedCustomerCharges = 
            BrokerUtils.revertKeyMapping(customer2estimatedTariffCharges);
    
    for (TariffSpecification spec : suggestedSpecs){
      HashMap<CustomerInfo, Double> cust2estimatedCharge = tariff2estimatedCustomerCharges.get(spec);
      double totalCharge = 0;
      double totalPopulation = 0;
      for (CustomerInfo cust : cust2estimatedCharge.keySet()) {
        int custPopulation = cust.getPopulation();
        Double charge = cust2estimatedCharge.get(cust);
        totalCharge += charge * custPopulation;
        totalPopulation += custPopulation;
      }  
      double avgCharge = totalCharge / totalPopulation;
      // adding a fee here should be fine, since the pointer
      // to tariff in the map should be to the actual candidate
      // tariff.
      spec.withMinDuration(TimeService.WEEK * 1) 
          // TODO: here we could optimize withdraw fees differently
          .withEarlyWithdrawPayment(avgCharge * 0.5); 
    }    
  }

}
