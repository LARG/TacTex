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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;


import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.interfaces.ChargeEstimator;
import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.interfaces.OptimizerWrapper;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.TariffOptimizer;
import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.interfaces.TariffUtilityEstimate;
import edu.utexas.cs.tactex.interfaces.UtilityEstimator;
import edu.utexas.cs.tactex.interfaces.WithdrawFeesOptimizer;
import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;


/**
 * A base class for incremental tariff optimization - used for gradient-ascent,
 * Amoeba, BOBYQA, etc. 
 *
 * @author urieli
 *
 */
public class TariffOptimizerIncremental extends TariffOptimizerBase {

  static private Logger log = Logger.getLogger(TariffOptimizerIncremental.class);

  
  private static final int NUM_RATES = 24;
  private static final int NUM_EVAL = 1000;

  
  private TariffOptimizer tariffOptimizerOneShot;

  private OptimizerWrapper optimizerWrapper;
  
  private UtilityEstimator utilityEstimator;

  private MarketPredictionManager marketPredictionManager;

  //@Autowired   - doesn't work
  private ConfiguratorFactoryService configuratorFactoryService;


  public TariffOptimizerIncremental(
      WithdrawFeesOptimizer withdrawFeesOptimizer,
      TariffRepoMgr tariffRepoMgr, 
      ChargeEstimator chargeEstimator, 
      ShiftingPredictor shiftingPredictor,
      TariffOptimizer tariffOptimizerOneShot,
      OptimizerWrapper optimizerWrapper,
      UtilityEstimator utilityEstimator,
      MarketPredictionManager marketPredictionManager, 
      ConfiguratorFactoryService configuratorFactoryService) {
    super(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor);
    this.tariffOptimizerOneShot = tariffOptimizerOneShot;
    this.optimizerWrapper = optimizerWrapper;
    this.utilityEstimator = utilityEstimator;
    this.marketPredictionManager = marketPredictionManager;
    this.configuratorFactoryService = configuratorFactoryService;
  }


  @Override
  public TreeMap<Double, TariffSpecification> optimizeTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy, List<TariffSpecification> competingTariffs,
      MarketManager marketManager, ContextManager contextManager,
      CostCurvesPredictor costCurvesPredictor, int currentTimeslot,
      Broker me) {
    
    // seed will be the best fixed-rate tariff
    TreeMap<Double, TariffSpecification> sortedTariffs =
        tariffOptimizerOneShot.optimizeTariffs(tariffSubscriptions, 
            customer2estimatedEnergy, competingTariffs, marketManager, 
            contextManager, costCurvesPredictor, currentTimeslot, me);
    TariffSpecification fixedRateSeed = extractBestTariffSpec(sortedTariffs);

    // this provides objective function evaluations 
    TariffUtilityEstimateImpl tariffUtilityEstimate = new TariffUtilityEstimateImpl(utilityEstimator, NUM_RATES, fixedRateSeed,
        withdrawFeesOptimizer, tariffSubscriptions,
        competingTariffs,
        customer2estimatedEnergy,
        marketPredictionManager,
        costCurvesPredictor,
        configuratorFactoryService, currentTimeslot,
        me);

    try {

      TreeMap<Double, TariffSpecification> touTariffs = optimizerWrapper.findOptimum(tariffUtilityEstimate, NUM_RATES, NUM_EVAL );

      Entry<Double, TariffSpecification> optimum = touTariffs.lastEntry();
      sortedTariffs.putAll(touTariffs); // adding all tariffs
      
      // just printing 
      TariffSpecification delete = optimum.getValue();
      log.info("Daniel TOU BEST Candidate " + delete + " util " + optimum.getKey()); 
      for (Rate r : delete.getRates()) {
        log.info(r);
      }
      
    } catch (Exception e) {
       log.error("caught exception from incremental tariff optimization, falling back to fixed-rate ", e);
    }
    return sortedTariffs;
  }

    
  /**
   * This is the implementation of the objective function, which maps a TOU
   * tariff to it's expected utility value.  The tariff is represented as
   * offsets from the fixedRateSeed tariff
   *  
   * @author urieli
   */
  public class TariffUtilityEstimateImpl implements TariffUtilityEstimate {

    //static private Logger log = Logger.getLogger(TariffUtilityEstimateImpl.class);
    
    /**
     * 
     */
    // fields sent in constructor
    private UtilityEstimator utilityEstimator;
    private final int NUM_RATES;
    private TariffSpecification fixedRateSeed;
    private WithdrawFeesOptimizer withdrawFeesOptimizer;
    private HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions;
    private List<TariffSpecification> competingTariffs;
    private HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy;
    private MarketPredictionManager marketPredictionManager;
    private CostCurvesPredictor costCurvesPredictor;
    ConfiguratorFactoryService configuratorFactoryService;
    private int currentTimeslot;
    private Broker me;
    
    // fields initialized in constructor 
    private HashMap<double[], TariffSpecification> point2spec;
    private HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2ShiftedEnergy;
    private HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> customer2RelevantTariffCharges;
    private List<TariffSpecification> emptyList;
    private HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> emptyHash;
    
    public TariffUtilityEstimateImpl(
        UtilityEstimator utilityEstimator,
        int NUM_RATES,
        TariffSpecification fixedRateSeed, 
        WithdrawFeesOptimizer withdrawFeesOptimizer,
        HashMap<TariffSpecification,HashMap<CustomerInfo,Integer>> tariffSubscriptions, 
        List<TariffSpecification> competingTariffs, 
        HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy, 
        MarketPredictionManager marketPredictionManager, 
        CostCurvesPredictor costCurvesPredictor,
        ConfiguratorFactoryService configuratorFactoryService,
        int currentTimeslot, 
        Broker me) {
      this.utilityEstimator = utilityEstimator;
      this.NUM_RATES = NUM_RATES;
      this.fixedRateSeed = fixedRateSeed;
      this.withdrawFeesOptimizer = withdrawFeesOptimizer;
      this.tariffSubscriptions = tariffSubscriptions;
      this.competingTariffs = competingTariffs;
      this.customer2estimatedEnergy = customer2estimatedEnergy;
      this.marketPredictionManager = marketPredictionManager;
      this.costCurvesPredictor = costCurvesPredictor;
      this.configuratorFactoryService = configuratorFactoryService;
      this.currentTimeslot = currentTimeslot;
      this.me = me;
      
      // holds a reference (pointer) to array as the key
      point2spec = new HashMap<double[], TariffSpecification>();
      
      // create and save shifted predictions and tariff charges under these
      // predictions running once with existing tariffs and empty suggested
      // specs, which would be added one at a time, later
      List<TariffSpecification> allSpecs = new ArrayList<TariffSpecification>();
      allSpecs.addAll(tariffSubscriptions.keySet());
      allSpecs.addAll(competingTariffs);
      customer2ShiftedEnergy = 
          estimateShiftedPredictions(
              customer2estimatedEnergy, 
              allSpecs,
              currentTimeslot); 
      List<TariffSpecification> emptySpecList = new ArrayList<TariffSpecification>();
      customer2RelevantTariffCharges = estimateRelevantTariffCharges(
          emptySpecList, tariffSubscriptions, competingTariffs,
          customer2ShiftedEnergy);
      //
      emptyList = new ArrayList<TariffSpecification>();
      emptyHash = new HashMap<TariffSpecification, HashMap<CustomerInfo,Integer>>();
    }

    @Override
    public double value(double[] point) {
      if ( ! verifyNumRates(point) ) {
        log.error("wrong number of TOU rates");
        return -Double.MAX_VALUE;
      }
      
      TariffSpecification candidate = convertPointToSpec(point);
      List<TariffSpecification> suggestedSpec = new ArrayList<TariffSpecification>();
      suggestedSpec.add(candidate);

      ///////////////////////////////////////////////////////////////////
      // This part is similar to TariffOptimizerOneShot (but incremental)
      //
      // add shifted predictions under suggestedSpec for all customers to existing container
      computeAndRecordCustomersShiftedEnergyUnderNewSpec(suggestedSpec);
      //
      // add estimated charges under suggestedSpec for all customers to existing container
      computeAndRecordCustomersShiftBasedChargesUnderNewSpec(suggestedSpec);
      //
      // add withdraw fees based on the estimates charges per customer
      if (configuratorFactoryService.isUseFees()) {
        withdrawFeesOptimizer.addWithdrawFeeAndMinDuration(suggestedSpec, customer2RelevantTariffCharges);
      }
      //////////////////////////////////////////////////////////
      
      // just renaming, should contain only 1, non-null tariff
      TreeMap<Double, TariffSpecification> result = 
          utilityEstimator.estimateUtilities(suggestedSpec,
                                             tariffSubscriptions,
                                             competingTariffs,
                                             customer2RelevantTariffCharges,
                                             customer2ShiftedEnergy,
                                             customer2estimatedEnergy, 
                                             marketPredictionManager, 
                                             costCurvesPredictor, 
                                             currentTimeslot,
                                             me);
      if (result.size() != 1) {
        log.error("How come sortedTariffs.size() != 1?");        
      }
      
      log.info("Daniel TOU Candidate " + candidate + " util " + result.lastKey()); 
      for (Rate r : candidate.getRates()) {
        log.info(r);
      }

      return result.lastKey();
    }


    private void computeAndRecordCustomersShiftBasedChargesUnderNewSpec(
        List<TariffSpecification> suggestedSpec) {
      HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> 
          chargeOfNewTariff = 
              estimateRelevantTariffCharges(
                  suggestedSpec, emptyHash, emptyList, 
                  customer2ShiftedEnergy);
      for ( Entry<CustomerInfo, HashMap<TariffSpecification, Double>> entry  : chargeOfNewTariff.entrySet()) {
        CustomerInfo customer = entry.getKey();
        HashMap<TariffSpecification, Double> tariff2eval = entry.getValue();
        if (tariff2eval.size() != 1) {
          log.error("How come tariff2eval.size is not 1?");
        }
        customer2RelevantTariffCharges.get(customer).putAll(tariff2eval);
      }
    }


    private void computeAndRecordCustomersShiftedEnergyUnderNewSpec(
        List<TariffSpecification> suggestedSpec) {
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> shiftedPredOfNewTariff = 
          estimateShiftedPredictions(
              customer2estimatedEnergy, 
              suggestedSpec,
              currentTimeslot); 
      for ( Entry<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> entry : shiftedPredOfNewTariff.entrySet()) {
        CustomerInfo customer = entry.getKey();
        HashMap<TariffSpecification, ShiftedEnergyData> tariff2energy = entry.getValue();
        if (tariff2energy.size() != 1) {
          log.error("How come tariff2energy.size is not 1?");
        }
        customer2ShiftedEnergy.get(customer).putAll(tariff2energy);
      }
    }


    @Override
    public TariffSpecification getCorrespondingSpec(double[] queryPoint) {
      
      TariffSpecification result = point2spec.get(queryPoint);
      
      if (null == result) {
        // try to find key by content rather than by pointer
        List<Double> bestPointAsList = Arrays.asList(ArrayUtils.toObject(queryPoint));
        for (double[] point : point2spec.keySet()) {
          List<Double> pointAsList = Arrays.asList(ArrayUtils.toObject(point));
          if (bestPointAsList.equals(pointAsList))
            return point2spec.get(point);
        }
      }

      // if here didn't find, try to create
      result = convertPointToSpec(queryPoint); 
      
      return result;
    }


    private TariffSpecification convertPointToSpec(double[] point) {
      TariffSpecification spec = new TariffSpecification(me, PowerType.CONSUMPTION);
      if (point.length != NUM_RATES) {
        log.error("How come point length != NUM_RATES, i.e. " + NUM_RATES);
        return fixedRateSeed;
      } 
      double fixedRate = fixedRateSeed.getRates().get(0).getValue(); 
      for (int i=0; i < point.length; ++i){
        Rate rate = new Rate().withValue(fixedRate + point[i]).withDailyBegin(i).withDailyEnd(i);
        spec.addRate(rate);
      } 
      // add mapping
      point2spec.put(point, spec); 
      return spec;     
    }


    private boolean verifyNumRates(double[] point) {
      return point.length == NUM_RATES;
    }
    
  }
  
}
