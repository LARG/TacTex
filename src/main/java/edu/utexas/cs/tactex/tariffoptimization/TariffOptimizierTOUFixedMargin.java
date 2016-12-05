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
package edu.utexas.cs.tactex.tariffoptimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;


import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.interfaces.ChargeEstimator;
import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.TariffOptimizer;
import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.interfaces.WithdrawFeesOptimizer;
import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

/**
 * @author urieli
 *
 */
public class TariffOptimizierTOUFixedMargin extends TariffOptimizerBase {
  
  static private Logger log = Logger.getLogger(TariffOptimizierTOUFixedMargin.class);

  
  private static final int NUM_RATES = 24;


  private TariffOptimizer tariffOptimizerFixedRate;

  
  /**
   * @param withdrawFeesOptimizer
   * @param tariffRepoMgr
   * @param chargeEstimator
   * @param shiftingPredictor
   * @param tariffOptimizerFixedRate 
   */
  public TariffOptimizierTOUFixedMargin(
      WithdrawFeesOptimizer withdrawFeesOptimizer, 
      TariffRepoMgr tariffRepoMgr,
      ChargeEstimator chargeEstimator, 
      ShiftingPredictor shiftingPredictor, 
      TariffOptimizer tariffOptimizerFixedRate) {
    super(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator,
        shiftingPredictor);
    this.tariffOptimizerFixedRate = tariffOptimizerFixedRate;
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.TariffOptimizer#optimizeTariffs(java.util.HashMap, java.util.HashMap, java.util.List, edu.utexas.cs.tactex.interfaces.MarketManager, edu.utexas.cs.tactex.interfaces.ContextManager, edu.utexas.cs.tactex.interfaces.CostCurvesPredictor, int, org.powertac.common.Broker)
   */
  @Override
  public TreeMap<Double, TariffSpecification> optimizeTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      List<TariffSpecification> competingTariffs, MarketManager marketManager,
      ContextManager contextManager, CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot, Broker me) {

    // seed will be the best fixed-rate tariff
    TreeMap<Double, TariffSpecification> sortedTariffs =
        tariffOptimizerFixedRate.optimizeTariffs(tariffSubscriptions, 
            customer2estimatedEnergy, competingTariffs, marketManager, 
            contextManager, costCurvesPredictor, currentTimeslot, me);
    TariffSpecification fixedRateSeed = extractBestTariffSpec(sortedTariffs);
    
    TotalEnergyRecords energyRecords = sumTotalEnergy(customer2estimatedEnergy, tariffSubscriptions, currentTimeslot);
    ArrayRealVector energyUnitCosts = computeEnergyUnitCosts(costCurvesPredictor, energyRecords.getMyCustomersEnergy(), energyRecords.getCompetitorCustomersEnergy(), currentTimeslot);
    double avgMargin = computeAvgMargin(energyUnitCosts, fixedRateSeed);
    TariffSpecification touSpec = createTOUFixedMargin(energyUnitCosts, avgMargin, currentTimeslot, me);
    
    // create a result map with 1 tariff
    TreeMap<Double, TariffSpecification> eval2spec = new TreeMap<Double, TariffSpecification>();
    eval2spec.put(0.0, touSpec);
    return eval2spec;
  }


  private TotalEnergyRecords sumTotalEnergy(
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions, 
      int currentTimeslot) {
    
    List<TariffSpecification> allSpecs = new ArrayList<TariffSpecification>();
    allSpecs.addAll(tariffSubscriptions.keySet());
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        customer2ShiftedEnergy = 
            estimateShiftedPredictions(customer2estimatedEnergy, allSpecs, currentTimeslot);
    
    int predictionRecordLength = customer2estimatedEnergy.values().iterator().next().getDimension();
    ArrayRealVector predictedMyCustomersEnergy = new ArrayRealVector(predictionRecordLength);
    
    // we currently predict cost by total amount of energy
    //
    for (Entry<TariffSpecification, HashMap<CustomerInfo, Integer>> entry  : tariffSubscriptions.entrySet()) {
      TariffSpecification spec = entry.getKey();
      for (Entry<CustomerInfo, Integer> e : entry.getValue().entrySet()) {
        CustomerInfo customer = e.getKey();
        int subs = e.getValue();
        RealVector customerEnergy = customer2ShiftedEnergy.get(customer).get(spec).getShiftedEnergy().mapMultiply(subs);
        predictedMyCustomersEnergy = predictedMyCustomersEnergy.add(customerEnergy);
      }
    }
    // competitor energy prediction
    ArrayRealVector predictedCompetitorsEnergyRecord = new ArrayRealVector(predictionRecordLength);
    HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> predictedCustomerSubscriptions = BrokerUtils.revertKeyMapping(tariffSubscriptions);
    for (CustomerInfo cust : predictedCustomerSubscriptions.keySet()) {
      double subsToOthers = cust.getPopulation() - BrokerUtils.sumMapValues(predictedCustomerSubscriptions.get(cust));
      RealVector customerNonShiftedEnergy = customer2estimatedEnergy.get(cust).mapMultiply(subsToOthers);
      predictedCompetitorsEnergyRecord = predictedCompetitorsEnergyRecord.add(customerNonShiftedEnergy);
    }
    
    log.debug("predictedMyCustomersEnergy =" + predictedMyCustomersEnergy.toString());
    log.debug("predictedCompetitorsEnergyRecord =" + predictedCompetitorsEnergyRecord.toString());
    return new TotalEnergyRecords(predictedMyCustomersEnergy, predictedCompetitorsEnergyRecord);
  }


  private ArrayRealVector computeEnergyUnitCosts(
      CostCurvesPredictor costCurvesPredictor, 
      ArrayRealVector predictedMyCustomersEnergy,
      ArrayRealVector predictedCompetitorsEnergy,
      int currentTimeslot) {
    ArrayRealVector energyUnitCosts = new ArrayRealVector(predictedMyCustomersEnergy.getDimension());
    for (int i = 0; i < energyUnitCosts.getDimension(); ++i) {
      int futureTimeslot = currentTimeslot + i + 1; // +1 since energy record starts next timeslot
      energyUnitCosts.setEntry(i, costCurvesPredictor.predictUnitCostKwh(currentTimeslot, futureTimeslot, predictedMyCustomersEnergy.getEntry(i), predictedCompetitorsEnergy.getEntry(i))
                                  + costCurvesPredictor.getFudgeFactorKwh(currentTimeslot));
    }
    //log.debug("energyUnitCosts =" + energyUnitCosts.toString());
    return energyUnitCosts;
  }


  /**
   * returns positive margin between selling unit-price and buying unit-cost 
   * @param energyUnitCosts
   * @param fixedRateSeed
   * @return
   */
  private double computeAvgMargin(ArrayRealVector energyUnitCosts,
      TariffSpecification fixedRateSeed) {
    double totalMargin = 0;
    // next: '-' to adjust sign to broker perspective (i.e. make it positive)
    double sellingPricePerKwh = -(fixedRateSeed.getRates().get(0).getValue());
    for (int i = 0; i < energyUnitCosts.getDimension(); ++i ) {
      double buyingPricePerKwh = energyUnitCosts.getEntry(i);
      // next: '+' since buyingPricePerKwh is signed (i.e. negative)
      double margin = sellingPricePerKwh + buyingPricePerKwh; 
      totalMargin += margin; 
      log.debug("computeAvgMargin(): sellingPricePerKwh=" + sellingPricePerKwh + " buyingPricePerKwh=" + buyingPricePerKwh + "margin =" + margin + " totalMargin=" + totalMargin);
    }
    double avgMargin = totalMargin / energyUnitCosts.getDimension();
    log.debug("avgMargin=" + avgMargin);
    return avgMargin;
  }


  private TariffSpecification createTOUFixedMargin(
      ArrayRealVector energyUnitCosts, double avgMargin, int currentTimeslot, Broker me) {
    
    int currentHourOfDay = (currentTimeslot - 360) % 24;
    
    TariffSpecification spec = new TariffSpecification(me, PowerType.CONSUMPTION);

    int firstHourOfPrediction = currentHourOfDay + 1;
    for (int i = 0; i < NUM_RATES; ++i) {
      // +/- confusion: energyUnitCosts contains negative values and margin is positive
      double rateValue = energyUnitCosts.getEntry(i) - avgMargin;
      int hour = (firstHourOfPrediction + i) % 24;
      log.debug("adding rate, hour=" + hour + " rate=" + rateValue);
      Rate rate = new Rate().withValue(rateValue).withDailyBegin(hour).withDailyEnd(hour);
      spec.addRate(rate);
    }
    
    return spec;     
  }


  public class TotalEnergyRecords {

    private ArrayRealVector myCustomersEnergy;
    private ArrayRealVector competitorCustomersEnergy;

    public TotalEnergyRecords(ArrayRealVector myCustomersEnergy,
        ArrayRealVector competitorCustomersEnergy) {
      this.myCustomersEnergy = myCustomersEnergy;
      this.competitorCustomersEnergy = competitorCustomersEnergy;
    }
    
    public ArrayRealVector getMyCustomersEnergy() {
      return myCustomersEnergy;
    }
    
    public ArrayRealVector getCompetitorCustomersEnergy() {
      return competitorCustomersEnergy;
    }
  
  }

}
