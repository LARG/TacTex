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
import java.util.Random;

import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.TariffSuggestionMaker;
import edu.utexas.cs.tactex.utils.BrokerUtils;

/**
 * @author urieli
 *
 */
public class ConsumptionTariffSuggestionMakerFixedRates implements TariffSuggestionMaker {

  static private Logger log = Logger.getLogger(ConsumptionTariffSuggestionMakerFixedRates.class);

  // number of generated candidates
  private static final int NUM_TARIFFS = 80;

  private int numCalls = 0;

  private Random randomGen = new Random();
  private double sample = randomGen.nextDouble();

  @Override
  public List<TariffSpecification> suggestTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs, 
      MarketManager marketManager,
      ContextManager contextManager, 
      Broker me) {

    ++numCalls;

    double marketBasedBound = computeMarketBasedBound(marketManager, contextManager);
    
    double competingBestRateValue = getCompetingBestRateValue(competingTariffs, marketBasedBound, me);
    double myBestRateValue = getMyBestRateValue(tariffSubscriptions, me);

    List<TariffSpecification> suggestedTariffs = new ArrayList<TariffSpecification>();
    // rates are from customer perspective, so if competing is higher then it's
    // better for the customer
    double lowRate = Math.max(competingBestRateValue, myBestRateValue);
    double highRate = Math.min(competingBestRateValue, myBestRateValue);
    boolean holdBackBestResponse = 
        numCalls != 1                              // o first time may need to undercut strongly
        && BrokerUtils.getNumberOfBrokers() == 2   // o only in 2-agent game
        && lowRate == myBestRateValue              // o only if my rate is lowest (marketBound can deceive here)
        && BrokerUtils.isGameInTransientPhase(competingTariffs, PowerType.CONSUMPTION) ; // o only in transient phase
    double bestResponseBound = //numCalls <= 3 ? 0.95 : 0.8;
                                holdBackBestResponse ? 0.96 : 0.8;
    log.info("bestResponseBound " + bestResponseBound);
    double bestSuggestRate = marketBasedBound + bestResponseBound * (lowRate - marketBasedBound); 
    double worstSuggestedRate = marketBasedBound + 1.2 * (highRate - marketBasedBound);
    log.info("competingBestRateValue " + competingBestRateValue + " myBestRateValue " + myBestRateValue + " lowRate " + lowRate + " highRate "
        + highRate + " bestSuggestRate " + bestSuggestRate + " worstSuggestedRate "
        + worstSuggestedRate + " marketBasedBound " + marketBasedBound);
    int numTariffs = NUM_TARIFFS;
    double delta = (worstSuggestedRate - bestSuggestRate) / (numTariffs - 1);
    for (int i = 0; i < numTariffs; ++i) {
      double rateValue = bestSuggestRate + i * delta;
      Rate rate = new Rate().withValue(rateValue);
      TariffSpecification spec = new TariffSpecification(me, PowerType.CONSUMPTION);
      spec.addRate(rate);
      suggestedTariffs.add(spec);
    }
    return suggestedTariffs;
  }


  private double computeMarketBasedBound(MarketManager marketManager,
      ContextManager contextManager) {
    double meanMarketPrice = -marketManager.getMeanMarketPricePerKWH(); // convert to negative, since we pay
    double stdMarketPrice = marketManager.getMarketPricePerKWHRecordStd();
    double distributionFee = contextManager.getDistributionFee();
    // meanMarketPrice and distributionFee are < 0, and stdMarketPrice is positive
    double marketBasedBound = meanMarketPrice + distributionFee;// - 1.96 * stdMarketPrice;
    if (BrokerUtils.getNumberOfBrokers() > 10) {
      // now it's the same case for all game-sizes, but 
      // in the future, can relax it to be smaller for smaller 
      // games (where less protection was needed, historically)
      marketBasedBound -= 0.7 * stdMarketPrice;
      //marketBasedBound -= sample * stdMarketPrice;
    }
    else {
      marketBasedBound -= 0.7 * stdMarketPrice;
    }
    return marketBasedBound;
  }


  private double getMyBestRateValue(
      HashMap<TariffSpecification, HashMap<CustomerInfo,Integer>> tariffSubscriptions, 
      Broker me) {
    // rates are negative (i.e. from customer's perspective) so we actually want
    // the highest one, which means the highest from customer perspective
    double myMinRateValue = -Double.MAX_VALUE;
    for(TariffSpecification spec : tariffSubscriptions.keySet()) {

      if ( ! spec.getPowerType().isConsumption() ) {
        continue;
      }
      
      if (spec.getBroker() == me) { // (should always be true)

        // TODO use the following variables as well
        //double periodicPayment = tariff.getPeriodicPayment() ;
        //double earlyWithdrawPayment = tariff.getEarlyWithdrawPayment();
        //double signupPayment = tariff.getSignupPayment();
        //long minDuration = tariff.getMinDuration(); // it seems that minDuration is computed in days
        for (Rate r : spec.getRates()) {
          double value;
          if (r.isFixed()) {
            value = r.getMinValue();
          }
          else {
            value = r.getExpectedMean();
          }

          if(value > myMinRateValue) {
            myMinRateValue = value;
          }
        }
      }
    }
    if (myMinRateValue == -Double.MAX_VALUE) {
      log.error("There must be at least one consumption tariff I suggested");
    }
    return myMinRateValue;
  }


  private double getCompetingBestRateValue(
      List<TariffSpecification> competingTariffs, 
      double marketBasedBound, Broker me) {
      
    // rates are negative (i.e. from customer's perspective) so we actually want
    // the highest one, which means cheapest for customer
    double competingBestRateValue = -Double.MAX_VALUE;
    for (TariffSpecification competingTariff : competingTariffs){

      if ( ! competingTariff.getPowerType().isConsumption() ) {
        continue;
      }

      Broker theBroker = competingTariff.getBroker();
      if ( ! me.getUsername().equals(theBroker.getUsername())) {
        
        // TODO use the following variables as well
        //double periodicPayment = competingTariff.getPeriodicPayment() ;
        //double earlyWithdrawPayment = competingTariff.getEarlyWithdrawPayment();
        //double signupPayment = competingTariff.getSignupPayment();
        //long minDuration = competingTariff.getMinDuration(); // it seems that minDuration is computed in days
                
        for (Rate r : competingTariff.getRates()) {
          double value;
          if (r.isFixed()) {
            value = r.getMinValue();
          }
          else {
            value = r.getExpectedMean();
          }
          // we ignore prices below market
          if(competingBestRateValue < value && value < marketBasedBound) {
            competingBestRateValue = value;
          }
        }
      }
      else {
        log.error("How come our tariff appears at competingTariffs?");
      }
    }
    if (competingBestRateValue == -Double.MAX_VALUE) {
      log.error("There must be at least one consumption tariff someone suggested");
    }
    return competingBestRateValue;
  }

}
