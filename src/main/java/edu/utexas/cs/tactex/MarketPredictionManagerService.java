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

import org.apache.commons.math3.linear.ArrayRealVector;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.utils.BrokerUtils;

/**
 * @author urieli
 *
 */
@Service
public class MarketPredictionManagerService 
implements Initializable, MarketPredictionManager {

  @Autowired
  private MarketManager marketManager;
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  
  
  public MarketPredictionManagerService() {
    super();
    // TODO Auto-generated constructor stub
  }

  @Override
  public void initialize(BrokerContext broker) {
    
    // NEVER CALL ANY SERVICE METHOD FROM HERE, SINCE THEY ARE NOT GUARANTEED
    // TO BE initalize()'d. 
    // Exception: it is OK to call configuratorFactory's public
    // (application-wide) constants

   
  }

  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.MarketPredictionManager#getPredictionForAbout7Days()
   */
  @Override
  public ArrayRealVector getPricePerKwhPredictionForAbout7Days() {
    ArrayRealVector record = marketManager.getMarketAvgPricesArrayKwh();
    int currentTimeslot = timeslotRepo.currentSerialNumber();
    return BrokerUtils.rotateWeeklyRecordAndAppendTillEndOfDay(record, currentTimeslot);
  }
}
