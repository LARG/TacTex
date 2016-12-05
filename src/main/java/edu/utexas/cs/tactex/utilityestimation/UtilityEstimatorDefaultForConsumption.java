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
package edu.utexas.cs.tactex.utilityestimation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.repo.CustomerRepo;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.CustomerPredictionManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.UtilityEstimator;
import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

/**
 * @author urieli
 *
 */
public class UtilityEstimatorDefaultForConsumption implements UtilityEstimator {

  static private Logger log = Logger.getLogger(UtilityEstimatorDefaultForConsumption.class);

  private ContextManager contextManager;
  private CustomerPredictionManager customerPredictionManager;


  //@Autowired   - doesn't work
  private ConfiguratorFactoryService configuratorFactoryService;
  
  
  // this is just for printing predicted vs. actual customer subscriptions
  public HashMap<TariffSpecification, HashMap<TariffSpecification, HashMap<CustomerInfo, Double>>> predictions = new HashMap<TariffSpecification, HashMap<TariffSpecification,HashMap<CustomerInfo,Double>>>();



  /**
   * @param tariffRepoMgr 
   *
   */  
  public UtilityEstimatorDefaultForConsumption(
      ContextManager contextManager,
      CustomerPredictionManager customerPredictionManager,
      // Sending ConfiguratorFactoryService in the constructor to make
      // autowired objects available here, since autowiring doesn't 
      // work in non-service classes
      ConfiguratorFactoryService configuratorFactoryService) {
    
    this.contextManager = contextManager;
    this.customerPredictionManager = customerPredictionManager;
    this.configuratorFactoryService = configuratorFactoryService;
  }


  /**
   * @param defaultSpec 
   * @param customer2RelevantTariffCharges 
   */
  @Override
  public TreeMap<Double, TariffSpecification> estimateUtilities(
      List<TariffSpecification> consideredTariffActions,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy,
      MarketPredictionManager marketPredictionManager, 
      CostCurvesPredictor costCurvesPredictor,
      int currentTimeslot,
      Broker me) {
    
    // this is just for printing predicted vs. actual customer subscriptions
    predictions.clear();

    TreeMap<Double, TariffSpecification> 
        utility2spec = 
            new TreeMap<Double, TariffSpecification>();
    
    // all possible tariff actions: {suggestedSpeces} U {no-op}
    // a value of null means no-op
    for (TariffSpecification spec : consideredTariffActions) {
      double utility = predictUtility(spec, customer2RelevantTariffCharges,
          tariffSubscriptions, competingTariffs, currentTimeslot,
          customer2ShiftedEnergy,
          customer2NonShiftedEnergy,
          marketPredictionManager,
          costCurvesPredictor);
      utility2spec.put(utility + publicationFee(spec), spec); 
    }
    
    return utility2spec;
  }


  @Override
  public TreeMap<Double, TariffSpecification> estimateRevokeUtilities(
      List<TariffSpecification> consideredTariffActions,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy,
      MarketPredictionManager marketPredictionManager,
      CostCurvesPredictor costCurvesPredictor, int currentTimeslot, Broker me) {

    TreeMap<Double, TariffSpecification> 
        utility2spec = 
            new TreeMap<Double, TariffSpecification>();
    
    // all possible tariff actions: {suggestedSpeces} U {no-op}
    // a value of null means no-op
    for (TariffSpecification spec : consideredTariffActions) {
      
      double utility = predictRevokeUtility(spec, customer2RelevantTariffCharges,
          tariffSubscriptions, competingTariffs, currentTimeslot,
          customer2ShiftedEnergy,
          customer2NonShiftedEnergy,
          marketPredictionManager,
          costCurvesPredictor);
      utility2spec.put(utility + revokeFee(spec), spec); 
    }
    return utility2spec;
  }


  /**
   * use it to compute utility for a candidate tariff:
   * 1. compute customer migration
   * 2. compute utility after customer migration
   * 
   * @param spec
   * @param customer2estimatedTariffCharges
   * @param tariffSubscriptions
   * @param competingTariffs
   * @param currentTimeslot
   * @param customer2NonShiftedEnergy 
   * @param customer2ShiftedEnergy2
   * @param marketPredictionManager
   * @param costCurvesPredictor
   * @return
   */
  private double predictUtility(
      TariffSpecification spec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2estimatedTariffCharges,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      int currentTimeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy, 
      MarketPredictionManager marketPredictionManager,
      CostCurvesPredictor costCurvesPredictor) {

    HashMap<TariffSpecification, HashMap<CustomerInfo, Double>>
        predictedCustomerSubscriptions = customerPredictionManager.predictCustomerMigration(spec, 
            customer2estimatedTariffCharges, 
            tariffSubscriptions, 
            competingTariffs, currentTimeslot);

    // This is just for printing predicted vs. actual customer subscriptions
    predictions.put(spec, predictedCustomerSubscriptions);
    
    // estimate utility with predicted migration
    log.info("estimating utility of adding spec: " + spec);
    double utility = estimateUtility(tariffSubscriptions, predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customer2ShiftedEnergy, //customer2estimatedEnergy, 
        customer2NonShiftedEnergy,
        marketPredictionManager, 
        costCurvesPredictor, 
        currentTimeslot/*, customer2estimatedEnergy*/); // <= for print purposes
    return utility;
  }


  private double predictRevokeUtility(
      TariffSpecification spec,
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2estimatedTariffCharges,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs,
      int currentTimeslot,
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy,
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy,
      MarketPredictionManager marketPredictionManager,
      CostCurvesPredictor costCurvesPredictor) {
    
    HashMap<TariffSpecification, HashMap<CustomerInfo, Double>>
        predictedCustomerSubscriptions = customerPredictionManager.predictCustomerMigrationForRevoke(spec, 
            customer2estimatedTariffCharges, 
            tariffSubscriptions, 
            competingTariffs, currentTimeslot);
    
    log.info("estimating utility of adding spec: " + spec);
    double utility = estimateUtility(tariffSubscriptions, predictedCustomerSubscriptions, 
        customer2estimatedTariffCharges, 
        customer2ShiftedEnergy, //customer2estimatedEnergy, 
        customer2NonShiftedEnergy,
        marketPredictionManager, 
        costCurvesPredictor, 
        currentTimeslot/*, customer2estimatedEnergy*/); // <= for print purposes

    return utility;
  }


  private int numTotalSubscriptions(
		HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedCustomerSubscriptions) {
	int total = 0;
    for (HashMap<CustomerInfo, Double> cust2subs : predictedCustomerSubscriptions.values()) {
      for (Double subs : cust2subs.values()) {
    	  total += subs;
      }
    }
    return total;
  }


  /**
   * Core method for estimating utility
   */
  public double estimateUtility(
      HashMap<TariffSpecification,HashMap<CustomerInfo,Integer>> tariffSubscriptions,
      HashMap<TariffSpecification,HashMap<CustomerInfo,Double>> predictedCustomerSubscriptions,
      HashMap<CustomerInfo,HashMap<TariffSpecification,Double>> customer2estimatedTariffCharges, 
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customerTariff2ShiftedEnergy, 
      HashMap<CustomerInfo, ArrayRealVector> customer2NonShiftedEnergy, 
      MarketPredictionManager marketPredictionManager,
      CostCurvesPredictor costCurvesPredictor, 
      int currentTimeslot/*, HashMap<CustomerInfo,ArrayRealVector> customer2estimatedEnergy*/) { // <= for print purposes

    log.debug("estimateUtility(): currently assuming competing tariffs are not new and that my subscriptions are not going to change as a result of them");
     
    // accumulate the final results    
    //int expectedRecordLength = 7 * 24; // TODO use Timeservice here correctly
    double estTariffIncome = 0;
    double estTariffCosts = 0;
    double wholesaleCosts = 0;
    double balancingCosts = 0;
    double distributionCosts = 0;
    double withdrawCosts = 0;
    //double totalConsumption = 0; 
    //double totalProduction = 0; 
    final int predictionRecordLength = BrokerUtils.extractPredictionRecordLength(customerTariff2ShiftedEnergy);
    RealVector predictedEnergyRecord = new ArrayRealVector(predictionRecordLength);
    RealVector totalConsumptionEnergyRecord = new ArrayRealVector(predictionRecordLength);
    RealVector totalProductionEnergyRecord = new ArrayRealVector(predictionRecordLength);
    // for each customer get his usage prediction
    for (Entry<TariffSpecification, 
            HashMap<CustomerInfo, Double>> entry : predictedCustomerSubscriptions.entrySet()) {

      TariffSpecification spec = entry.getKey();
      HashMap<CustomerInfo,Double> subscriptions = entry.getValue();

      for (Entry<CustomerInfo, Double> ce : subscriptions.entrySet()) {

        CustomerInfo customerInfo = ce.getKey();
        Double subscribedPopulation = ce.getValue();
        
        // Predicted total tariff cash flow. Sign is inverted since
        // evaluatedTariffs was computed from customers' perspective
        if (spec.getPowerType().isConsumption()){
          estTariffIncome += -customer2estimatedTariffCharges.get(customerInfo).get(spec) * subscribedPopulation; 
        }
        else if (spec.getPowerType().isProduction()){
          estTariffCosts += -customer2estimatedTariffCharges.get(customerInfo).get(spec) * subscribedPopulation; 
        }
        else {
          log.warn("Ignoring unknown powertype when computing tariffs income/costs: " + spec.getPowerType());
        }
         
        // Predicted total energy
        RealVector energyPrediction = customerTariff2ShiftedEnergy.get(customerInfo).get(spec).getShiftedEnergy().mapMultiply(subscribedPopulation);
        predictedEnergyRecord = predictedEnergyRecord.add(energyPrediction);

        // Predicted balancing cost 
        balancingCosts += 0;
        log.debug("Ignoring balancing costs - assuming they are 0");
        
        // Predicted withdraw costs (currently assuming everyone will pay).
        // sign is inverted since withdraw payment is from customer's perspective
        HashMap<CustomerInfo, Integer> cust2subs = tariffSubscriptions.get(spec);
        if (cust2subs != null) {
          Integer currentSubs = cust2subs.get(customerInfo);
          if (currentSubs != null && subscribedPopulation < currentSubs) {
            double withdraws = currentSubs - subscribedPopulation;            
            withdrawCosts += -(withdraws * spec.getEarlyWithdrawPayment());
          }
        }

        // Predicted total consumption and total production
        if (spec.getPowerType().isConsumption()){
          totalConsumptionEnergyRecord = totalConsumptionEnergyRecord.add(energyPrediction);
        }
        else if (spec.getPowerType().isProduction()){
          totalProductionEnergyRecord = totalProductionEnergyRecord.add(energyPrediction);
        }
        else {
          log.warn("Ignoring unknown powertype when computing distribution costs");
        }
      }
    }

    // Predicted distribution costs
    log.debug("Ignoring balancing orders and curtailment when computing distribution costs");
    distributionCosts = 0;
    double distributionFee = contextManager.getDistributionFee();
    for (int i = 0; i < totalConsumptionEnergyRecord.getDimension(); ++i) {
      double totalTimeslotConsumption = Math.abs(totalConsumptionEnergyRecord.getEntry(i));
      double totalTimeslotProduction = Math.abs(totalProductionEnergyRecord.getEntry(i));
      distributionCosts += Math.max(totalTimeslotConsumption, totalTimeslotProduction) * distributionFee;
    }

    // Predicted wholesale costs (in one of the following two methods:)
    if (configuratorFactoryService.isUseCostCurves()) {
      // TODO: might not work for non-fixed rate competitor tariffs - 
      // better to send a mapping of competitor tariffs inside
      // predictedCustomerSubscriptions, compute shifted predictions for them,
      // and use these predictions here.
   
      // compute energy of customers I don't have 
      RealVector predictedCompetitorsEnergyRecord = new ArrayRealVector(predictionRecordLength);
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> map = BrokerUtils.revertKeyMapping(predictedCustomerSubscriptions);
      for (CustomerInfo cust : map.keySet()) {
        double subsToOthers = cust.getPopulation() - BrokerUtils.sumMapValues(map.get(cust));
        RealVector customerNonShiftedEnergy = customer2NonShiftedEnergy.get(cust).mapMultiply(subsToOthers);
        predictedCompetitorsEnergyRecord = predictedCompetitorsEnergyRecord.add(customerNonShiftedEnergy);
      }
    
      for (int i = 0; i < predictedEnergyRecord.getDimension(); ++i) {
        int futureTimeslot = currentTimeslot + i;
        double neededKwh = predictedEnergyRecord.getEntry(i);
        double competitorKwh = predictedCompetitorsEnergyRecord.getEntry(i);
        double unitCost = costCurvesPredictor.predictUnitCostKwh(currentTimeslot, futureTimeslot, neededKwh, competitorKwh);
        // NOTE: unitCost is signed (typically negative)
        wholesaleCosts += (unitCost + costCurvesPredictor.getFudgeFactorKwh(currentTimeslot)) * neededKwh;
        log.debug("cost-curve prediction: current " + currentTimeslot + " futureTimeslot " + futureTimeslot + " neededKwh " + neededKwh + " neededKwh + competitorKwh " + (neededKwh + competitorKwh) + " unitCost " + unitCost);
      }
    } 
    else {
      // compute wholesale costs using this consumption and market manager marketprices prediction
      ArrayRealVector 
      estimatedMarketPrices = 
      marketPredictionManager.
      getPricePerKwhPredictionForAbout7Days();
      // sanity check
      if (predictedEnergyRecord.getDimension() != estimatedMarketPrices.getDimension()) {
        log.error("Cannot compute utility - prediction periods of market and energy differ);");
        return 0;
      }
      wholesaleCosts = -predictedEnergyRecord.dotProduct(estimatedMarketPrices);
    }

    log.info("estTariffIncome " + estTariffIncome); 
    log.info("estTariffCosts " + estTariffCosts); 
    log.info("wholesaleCosts " + wholesaleCosts); 
    log.info("balancingCosts " + balancingCosts); 
    log.info("distributionCosts " + distributionCosts); 
    //log.info("old distributionCosts " +  Math.max(totalProduction, totalConsumption) * contextManager.getDistributionFee());
    log.info("withdrawCosts " + withdrawCosts); 

    return estTariffIncome + estTariffCosts + wholesaleCosts + balancingCosts + distributionCosts + withdrawCosts;
  }


  private double publicationFee(TariffSpecification spec) {
    // null is no-op => no-fees
    return (spec != null) ? contextManager.getPublicationFee() : 0; 
  }


  private double revokeFee(TariffSpecification spec) {
    // null is no-op => no-fees
    //log.debug("REVOCATION FEE " + contextManager.getRevocationFee());
    return (spec != null) ? contextManager.getRevocationFee() : 0; 
  } 
}
