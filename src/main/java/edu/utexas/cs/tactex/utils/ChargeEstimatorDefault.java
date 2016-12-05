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
package edu.utexas.cs.tactex.utils; 
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;
import org.powertac.common.TariffEvaluationHelper;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.ChargeEstimator;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;

public class ChargeEstimatorDefault implements ChargeEstimator {

  static private Logger log = Logger.getLogger(ChargeEstimatorDefault.class);

  
  private TariffRepoMgr tariffRepoMgr;


  public ChargeEstimatorDefault(TariffRepoMgr tariffRepoMgr) {
    super();
    
    // @Autowired replacement
    this.tariffRepoMgr = tariffRepoMgr;
  }
  
  
  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.ChargeEstimator#estimateTariffCharges(java.util.List, java.util.Collection, java.util.HashMap)
   */
  @Override
  public HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> estimateRelevantTariffCharges(List<TariffSpecification> tariffSpecs, HashMap<CustomerInfo, HashMap<TariffSpecification, BrokerUtils.ShiftedEnergyData>> customer2ShiftedEnergy) {
  
    // Assumption: ignoring regulation charges in tariff evaluation helper
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> estimatedCharges = 
        new HashMap<CustomerInfo, HashMap<TariffSpecification, Double>>();
    for(CustomerInfo customerInfo : customer2ShiftedEnergy.keySet()) {
      // create entry for customer
      HashMap<TariffSpecification, Double> tariffEvaluations = 
          new HashMap<TariffSpecification, Double>();
      // scan tariffs and evaluate them 
      for (TariffSpecification spec : tariffSpecs) {
        if (customerInfo.getPowerType().canUse(spec.getPowerType())) {
          double charge = estimateCharge(
              customer2ShiftedEnergy.get(customerInfo).get(spec).getShiftedEnergy(),
              spec);
          Double inconvenienceFactor = customer2ShiftedEnergy.get(customerInfo).get(spec).getInconvenienceFactor();
          double evaluation = charge + inconvenienceFactor;
          log.debug("inconv charge=" + charge + " inconvenienceFactor=" + inconvenienceFactor + " evaluation=" + evaluation /*+ " ratio=" + evaluation/charge*/);
          tariffEvaluations.put(spec, evaluation);
        }
      }
  
      if (tariffEvaluations.size() > 0) {
        estimatedCharges.put(customerInfo, tariffEvaluations);
      }
    }
    return estimatedCharges;
  }
  
  
  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.ChargeEstimator#estimateCharge(org.apache.commons.math3.linear.ArrayRealVector, org.powertac.common.TariffSpecification)
   */
  @Override
  public double estimateCharge(ArrayRealVector customerEnergy, TariffSpecification spec) {

    TariffEvaluationHelper helper = new TariffEvaluationHelper();
    helper.init(); // init with no parameters since we don't know the customer's parameters(?)

    Tariff tariff = tariffRepoMgr.findTariffById(spec.getId());
    if (null == tariff) {
      log.error("failed to find spec in repo, spec-id: " + spec.getId());
      return -Double.MAX_VALUE;
    }
    
    // evaluate
    double evaluation = helper.estimateCost(tariff, 
                                            customerEnergy.toArray(), 
                                            true);
    
    return evaluation;
  }

}
