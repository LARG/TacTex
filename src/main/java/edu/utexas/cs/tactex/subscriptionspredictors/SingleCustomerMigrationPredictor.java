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

import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

/**
 * This class implements the chain-of-responsibility pattern.
 * Typically we will have 
 * server-based predictior => 
 * regression-based predictor =>
 * interpolateOrNN predictor
 * This pattern supports future additions of customer models, 
 * such that is server-based predictor won't work, we could
 * fall-back to regression-based which is generic, and so on 
 * @author urieli
 *
 */
public abstract class SingleCustomerMigrationPredictor {

  static private Logger log = Logger.getLogger(SingleCustomerMigrationPredictor.class);
  
  
  private SingleCustomerMigrationPredictor next;

  /**
   * set next item in the chain
   * @param predictor
   */
  public void setNext(SingleCustomerMigrationPredictor predictor) {
      next = predictor;
  }

  public HashMap<TariffSpecification, Double> predictMigrationForSingleCustomer(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competingTariffs,
      int timeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions,
      CustomerInfo customer, TariffSpecification defaultSpec) {

    HashMap<TariffSpecification, Double> result = null;
    
    try {
      
      result = doPredictMigrationForSingleCustomer(
          candidateSpec,
          customer2tariffEvaluations,
          competingTariffs, 
          timeslot,
          customer2tariffSubscriptions,
          customer, 
          defaultSpec);
      
    } catch (Throwable e) {
      log.error("except-recovery: migration-prediction", e);
    }

    if (null == result) {
      log.debug("Chain-of-responsibility: migration-prediction handler failed, forwarding...");
      result = next.predictMigrationForSingleCustomer(
          candidateSpec,
          customer2tariffEvaluations,
          competingTariffs, 
          timeslot,
          customer2tariffSubscriptions, 
          customer, 
          defaultSpec);
    }
    
    return result;    
  }

  /**
   * template-method like - subclasses should implement it
   */
  abstract protected HashMap<TariffSpecification, Double> doPredictMigrationForSingleCustomer(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      List<TariffSpecification> competingTariffs,
      int timeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions,
      CustomerInfo customer, TariffSpecification defaultSpec);

}
