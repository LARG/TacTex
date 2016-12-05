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
package edu.utexas.cs.tactex;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TariffTransaction.Type;
import org.powertac.common.msg.BalanceReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.BalancingManager;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.Initializable;

/**
* @author urieli
*/
@Service
public class BalancingManagerService
implements BalancingManager, Initializable 
{

  static private Logger log = Logger.getLogger(BalancingManagerService.class);

  private BrokerContext brokerContext; // broker 

  // Autowired
  
  @Autowired  
  private ConfiguratorFactoryService configuratorFactoryService;


  // local state

  private static final int MIN_DATA_SIZE = 24;
  private static final int MEMORY_LENGTH = 24; // 48;

  // just to make sure we print only once
  private boolean firstBalanceReport = true;


  // ///////////////////////////////////////////////////
  // FIELDS THAT NEED TO BE INITIALIZED IN initialize()
  // EACH FIELD SHOULD BE ADDED TO test_initialize()
  // ///////////////////////////////////////////////////

  private TreeMap<Integer, Double> timeslotConsumption;
  private TreeMap<Integer, Double> timeslotBalancing;
  private TreeMap<Integer, Double> timeslotPredictions;
  private ArrayList<BalanceReport> totalImbalanceReports;



  public BalancingManagerService ()
  {
    super();
  }

  @Override
  public void initialize(BrokerContext brokerContext) {


    // NEVER CALL ANY SERVICE METHOD FROM HERE, SINCE THEY ARE NOT GUARANTEED
    // TO BE initalize()'d. 
    // Exception: it is OK to call configuratorFactory's public
    // (application-wide) constants


    this.brokerContext = brokerContext;
    
    timeslotConsumption = new TreeMap<Integer, Double>();
    timeslotBalancing = new TreeMap<Integer, Double>();
    timeslotPredictions = new TreeMap<Integer, Double>();
    totalImbalanceReports = new ArrayList<BalanceReport>();
  }

  // ============= message handling =================

  public synchronized void handleMessage (TariffTransaction tx)
  {
    Type txType = tx.getTxType();
    if (TariffTransaction.Type.CONSUME == txType) {
      int postedTimeslot = tx.getPostedTimeslotIndex();
      double kwh = tx.getKWh();
      Double prevKwh = timeslotConsumption.get(postedTimeslot);
      if (null == prevKwh) 
        timeslotConsumption.put(postedTimeslot, kwh);
      else
        timeslotConsumption.put(postedTimeslot, prevKwh + kwh);
    } 
  }

  public synchronized void handleMessage (BalancingTransaction tx)
  {
    int postedTimeslot = tx.getPostedTimeslotIndex();
    double kwh = tx.getKWh();
    timeslotBalancing.put(postedTimeslot, kwh);
  }

  public synchronized void handleMessage (BalanceReport report) {
    totalImbalanceReports.add(report);
    if (firstBalanceReport) {
      log.warn("need to process BalanceReport");
      firstBalanceReport = false;
    }
  }


  // ============= subroutines =================

  @Override
  public double predictMeanInbalanceScale(int currentTimeslot) {
    
    // extract data for prediction
    //
    ArrayList<Double> balancingPercentOfConsumption = new ArrayList<Double>();
    for (Entry<Integer, Double> e : timeslotConsumption.descendingMap().entrySet()) {
      Integer timeslot = e.getKey();
      Double consumption = e.getValue();
      Double balancing = timeslotBalancing.get(timeslot);
      if (balancing != null) {
        // consumption is negative, balancing is negative if I am short and
        // positive I have surplus
        double myPrediction = consumption + (-balancing); 
        double percentage = balancing / myPrediction;
        balancingPercentOfConsumption.add(percentage);
        // stop when there are MEMORY_LENGTH elements
        if (balancingPercentOfConsumption.size() >= MEMORY_LENGTH)
          break;
      }
    }

    // compute prediction
    //
    double scalingFactor;
    if (balancingPercentOfConsumption.size() >= MIN_DATA_SIZE) {
      // compute mean fraction
      double total = 0;
      for (Double fraction : balancingPercentOfConsumption) {
        total += fraction;
      }
      double meanFraction = total / balancingPercentOfConsumption.size(); 
      scalingFactor = 1.0 + meanFraction;
    }
    else {
      log.debug("predictMeanInbalanceScale() doesn't have enough data, returning default");
      scalingFactor = 1.0;
    }
    log.debug("BalancingManager, scaling-factor=" + scalingFactor);
    return scalingFactor;
  }

  @Override
  public void updateFinalPrediction(int targetTimeslot, double kwh) {
    timeslotPredictions.put(targetTimeslot, kwh);
  }

  @Override
  public double getFudgeCorrection(int currentTimeslotIndex) {

    // extract data for prediction
    ArrayList<Double> pastErrors = new ArrayList<Double>();
    TreeSet<Integer> debugTimeslots = new TreeSet<Integer>();
    for (Entry<Integer, Double> e : timeslotConsumption.descendingMap().entrySet()) {
      Integer timeslot = e.getKey();
      Double actualConsumption = e.getValue();
      Double prediction = timeslotPredictions.get(timeslot);
      if (prediction != null) {
        pastErrors.add(actualConsumption - prediction);
        // debug:
        debugTimeslots.add(timeslot);
        // stop when there are MEMORY_LENGTH elements
        if (pastErrors.size() >= MEMORY_LENGTH)
          break;
      }
    }
    
    for (int i = currentTimeslotIndex - 24; i < currentTimeslotIndex; ++i) {
      if ( ! debugTimeslots.contains(i) )
        log.error("Timeslot " + i + " doesn't exist in fudge data for ts " + currentTimeslotIndex);
    }

    // compute prediction
    double fudgeFactor;
    if (pastErrors.size() >= MIN_DATA_SIZE) {
      // compute mean fraction
      double total = 0;
      for (Double error : pastErrors) {
        total += error;
        log.debug("fudge error " + error + " total " + total);
      }
      double meanError = total / pastErrors.size(); 
      fudgeFactor = -meanError; // - due to consumption being negative
    }
    else {
      log.debug("getFudgeCorrection() doesn't have enough data, returning default");
      fudgeFactor = 0.0;
    }
    log.debug("BalancingManager, fudge-factor=" + fudgeFactor);
    return fudgeFactor;
  } 
}
