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
package edu.utexas.cs.tactex;

import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.CustomerPredictionManager;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;

/**
 * @author urieli
 *
 */
@Service
public class CustomerPredictionManagerService 
implements CustomerPredictionManager, Initializable
{
  static private Logger log = Logger.getLogger(CustomerPredictionManagerService.class);
  
  @Autowired
  private ConfiguratorFactoryService configuratorFactoryService; 

  @Override
  public void initialize(BrokerContext broker) {
    
    // NEVER CALL ANY SERVICE METHOD FROM HERE, SINCE THEY ARE NOT GUARANTEED
    // TO BE initalize()'d. 
    // Exception: it is OK to call configuratorFactory's public
    // (application-wide) constants
    
  }


  @Override
  public HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> 
  predictCustomerMigration(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariff2customerSubscriptions,
      List<TariffSpecification> competingTariffs, int currentTimeslot) {
  
    return configuratorFactoryService.getCustomerMigrationPredictor().
        predictMigration(
            candidateSpec, 
            customer2tariffEvaluations,
            tariff2customerSubscriptions, 
            competingTariffs, 
            configuratorFactoryService.getOpponentPredictorService(), 
            configuratorFactoryService.isUseOppPred(),
            currentTimeslot);
  }


  @Override
  public HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> 
  predictCustomerMigrationForRevoke(
      TariffSpecification candidateSpec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2tariffEvaluations,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariff2customerSubscriptions,
      List<TariffSpecification> competingTariffs, int currentTimeslot) {
  
    return configuratorFactoryService.getCustomerMigrationPredictor().
        predictMigrationForRevoke(
            candidateSpec, 
            customer2tariffEvaluations,
            tariff2customerSubscriptions, 
            competingTariffs, 
            configuratorFactoryService.getOpponentPredictorService(), 
            configuratorFactoryService.isUseOppPred(),
            currentTimeslot);
  }
}
