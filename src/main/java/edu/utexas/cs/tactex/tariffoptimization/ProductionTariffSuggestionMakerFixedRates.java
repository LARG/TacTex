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
package edu.utexas.cs.tactex.tariffoptimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.TariffSuggestionMaker;

/**
 * @author urieli
 *
 */
public class ProductionTariffSuggestionMakerFixedRates implements TariffSuggestionMaker {

  static private Logger log = Logger.getLogger(ProductionTariffSuggestionMakerFixedRates.class);

  // number of generated candidates
  private static final int NUM_TARIFFS = 80;

  // TODO assume default-brokers spec is 0.015 (which was always true so far)
  private static final double PRODUCTION_DEFAULT_RATE = 0.015;


  @Override
  public List<TariffSpecification> suggestTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs, 
      MarketManager marketManager,
      ContextManager contextManager, 
      Broker me) {

    // '-' converts to positive
    double marketBasedBound = -computeMarketBasedBound(marketManager, contextManager);
    
    double competingBestRateValue = getCompetingBestRateValue(competingTariffs, marketBasedBound, me);
    double myBestRateValue = getMyBestRateValue(tariffSubscriptions, me);

    List<TariffSpecification> suggestedTariffs = new ArrayList<TariffSpecification>();
    // Since rates are from customer perspective, if competing-rate is higher then it's
    // better for the customer
    double lowRate = Math.min(competingBestRateValue, myBestRateValue);
    double highRate = Math.max(competingBestRateValue, myBestRateValue);
    double bestSuggestRate = marketBasedBound + 0.8 * (highRate - marketBasedBound); 
    double worstSuggestedRate = marketBasedBound + 1.2 * (lowRate - marketBasedBound);
    worstSuggestedRate = Math.max(worstSuggestedRate, PRODUCTION_DEFAULT_RATE - 0.002);
    log.info("competingBestRateValue " + competingBestRateValue + " myBestRateValue " + myBestRateValue + 
    " lowRate " + lowRate + " highRate " + highRate + " bestSuggestRate " + bestSuggestRate + " worstSuggestedRate " + worstSuggestedRate + " marketBasedBound " + marketBasedBound);
    int numTariffs = NUM_TARIFFS;
    double delta = (worstSuggestedRate - bestSuggestRate) / (numTariffs - 1);
    for (int i = 0; i < numTariffs; ++i) {
      double rateValue = bestSuggestRate + i * delta;
      Rate rate = new Rate().withValue(rateValue);
      TariffSpecification spec = new TariffSpecification(me, PowerType.SOLAR_PRODUCTION);
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
    double marketBasedBound = meanMarketPrice + distributionFee - 1.96 * stdMarketPrice;
    return marketBasedBound;
  }


  private double getMyBestRateValue(
      HashMap<TariffSpecification, HashMap<CustomerInfo,Integer>> tariffSubscriptions, 
      Broker me) {
    // rates are positive (i.e. from customer's perspective) so we want the
    // highest one, which means the highest from customer perspective
    double myBestRateValue = -Double.MAX_VALUE;
    for(TariffSpecification spec : tariffSubscriptions.keySet()) {

      if ( ! spec.getPowerType().isProduction() ) {
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

          if(value > myBestRateValue) {
            myBestRateValue = value;
          }
        }
      }
    }
    if (myBestRateValue == -Double.MAX_VALUE) {
      log.error("There must be at least one consumption tariff I suggested");
    }
    return myBestRateValue;
  }


  private double getCompetingBestRateValue(
      List<TariffSpecification> competingTariffs, 
      double marketBasedBound, Broker me) {
      
    // rates are positive (i.e. from customer's perspective) so I want
    // the highest one, which the best for the customer
    double competingBestRateValue = -Double.MAX_VALUE;
    for (TariffSpecification competingTariff : competingTariffs){

      if ( ! competingTariff.getPowerType().isProduction() ) {
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
          
          // we ignore prices below market; marketBasedBound should be positive here 
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
