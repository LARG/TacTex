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
/**
 * 
 */
package edu.utexas.cs.tactex.shiftingpredictors;

import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

/**
 * This is a 'no-op' object that doesn't predict any shifts, 
 * and just return the same prediction per customer, for any
 * subscription
 * @author urieli
 *
 */
public class ShiftingPredictorNoShifts implements ShiftingPredictor {

  /**
   * 
   */
  public ShiftingPredictorNoShifts() {
    // TODO Auto-generated constructor stub
  }

  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.ShiftingPredictor#updateEstimatedEnergyWithShifting(java.util.HashMap, java.util.HashMap)
   */
  @Override
  public HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> updateEstimatedEnergyWithShifting(
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedCustomerSubscriptions,
      int currentTimeslot) {
    
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        result = 
            new HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>>();
    
    // add same customer=>energy mapping for all possible tariff-specs
    // to make a customer=>[spec]=>energy structure
    for ( Entry<TariffSpecification, HashMap<CustomerInfo, Double>> entry : predictedCustomerSubscriptions.entrySet()) {

      TariffSpecification spec = entry.getKey();

      for (CustomerInfo cust : entry.getValue().keySet()) {

        ArrayRealVector energy = customer2estimatedEnergy.get(cust);
        
        // get, or create if doesn't exist
        HashMap<TariffSpecification, ShiftedEnergyData> spec2energy = result.get(cust);
        if (null == spec2energy) {
          spec2energy = new HashMap<TariffSpecification, ShiftedEnergyData>();
          result.put(cust, spec2energy);
        }

        spec2energy.put(spec, new ShiftedEnergyData(energy, 0.0)); // no inconvenience
      }
      
    }
    
    return result;
  }


}
