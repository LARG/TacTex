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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.powertac.common.ClearedTrade;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Competition;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TariffTransaction.Type;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.Activatable;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.utils.RegressionUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.PriceMwhPair;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaLinRegData;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaXYLambdas;
import edu.utexas.cs.tactex.utils.RegressionUtils.XYForRegression;
import weka.core.Instance;
import weka.core.Instances;

/**
* Handles cost curves estimation
* @author urieli
*/
@Service
public class CostCurvesPredictorService 
implements CostCurvesPredictor, Initializable, Activatable
{
  static private Logger log = Logger.getLogger(CostCurvesPredictorService.class);

  private BrokerContext brokerContext; // broker 


  // Autowired
  
  @Autowired  
  private ConfiguratorFactoryService configuratorFactoryService;

  @Autowired
  private TimeslotRepo timeslotRepo;


  // local state

  // should start as null
  private WekaLinRegData wekaData;

  // constants, for polynomial regression
  
  // Length of memory horizon. The '+24' is to avoid MDP trades for future to
  // eliminate the tail of 2-weeks' data. 
  private static final int MEMORY_LENGTH = 1 * 7*24 + 24 /*+ 24*/; 

  private static final int FUDGE_MEMORY_LENGTH = 24;




  // ///////////////////////////////////////////////////
  // FIELDS THAT NEED TO BE INITIALIZED IN initialize()
  // EACH FIELD SHOULD BE ADDED TO test_initialize() 
  // ///////////////////////////////////////////////////

  private double[] bootstrapMWh;
  private double[] bootstrapPayments;
//  private ArrayList<HashMap<Integer,ArrayList<PriceMwhPair>>> transactionsData;
  private HashMap<Integer, ArrayList<PriceMwhPair>> ts2boot;
  private HashMap<Integer, ArrayList<PriceMwhPair>> ts2mtx;
  private HashMap<Integer, ArrayList<PriceMwhPair>> ts2trade;
  private HashMap<Integer, Double> ts2mycons;
  private HashMap<Integer, Double> ts2totalcons;
  private HashMap<Integer, Double> ts2wholesaleBasedCostPrediction;
  private HashMap<Integer, Double> ts2consumptionBasedCostPrediction;
  private HashMap<Integer, Double> ts2actualCost;
  

  public CostCurvesPredictorService ()
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
    
    int usageRecordLength = configuratorFactoryService.CONSTANTS.USAGE_RECORD_LENGTH();
    this.bootstrapMWh = new double[usageRecordLength];
    this.bootstrapPayments = new double[usageRecordLength];
    this.ts2boot = new HashMap<Integer, ArrayList<PriceMwhPair>>();
    this.ts2mtx = new HashMap<Integer, ArrayList<PriceMwhPair>>();
    this.ts2trade = new HashMap<Integer, ArrayList<PriceMwhPair>>();
    this.ts2mycons = new HashMap<Integer, Double>();
    this.ts2totalcons = new HashMap<Integer, Double>();
    this.ts2wholesaleBasedCostPrediction = new HashMap<Integer, Double>();
    this.ts2consumptionBasedCostPrediction = new HashMap<Integer, Double>();
    this.ts2actualCost = new HashMap<Integer, Double>();
  }

  /**
   * This function currently exists just for 
   * tracking purposes: predicted vs actual. 
   */
  @Override
  public synchronized void activate(int currentTimeslot) {
    try {

      log.info("activate, ts " + currentTimeslot);

      // use most updated wekaData to get predicted vs actual
      // for the recent timeslot

      int predictionTimeslot = currentTimeslot - 1;
      ArrayList<PriceMwhPair> mtx = ts2mtx.get(predictionTimeslot);
      ArrayList<PriceMwhPair> trades = ts2trade.get(predictionTimeslot);
      Double myConsMwh = ts2mycons.get(predictionTimeslot);
      Double totalConsMwh = ts2totalcons.get(predictionTimeslot);

      if (wekaData != null && mtx != null && trades != null && myConsMwh != null && totalConsMwh != null) {
        double myWholesaleMwh = computeTotalMwh(mtx);
        double totalTradeMwh = computeTotalMwh(trades); 
        // Needs improvement since 
        // 1) trade is not 'total energy baught' 
        // 2) assumes that using <my-mtx, trades>  as x-before-features
        double competitorMwh = totalTradeMwh - myWholesaleMwh; 
        double wholesaleBasedPrediction = makeMwhPrediction(wekaData, predictionTimeslot, myWholesaleMwh, competitorMwh);
        double competitorConsumptionMwh = totalConsMwh - myConsMwh;
        double consumptionBasedPrediction = makeMwhPrediction(wekaData, predictionTimeslot, myConsMwh, competitorConsumptionMwh);
        double actual = computeAvgPrice(mtx);
        log.info("cost-curve wholesaleBasedPrediction: " + wholesaleBasedPrediction + " actual: " + actual);
        log.info("cost-curve consumptionBasedPrediction: " + consumptionBasedPrediction + " actual: " + actual);
        // record for predicted vs. actual
        ts2wholesaleBasedCostPrediction.put(predictionTimeslot, wholesaleBasedPrediction);
        ts2consumptionBasedCostPrediction.put(predictionTimeslot, consumptionBasedPrediction);
        ts2actualCost.put(predictionTimeslot, actual);
      }

      log.info("done-activate"); 

    } catch (Throwable e) {
      log.error("caught exception from activate(): ", e);
    } 
  }

  
  // ------------- message handling -----------------

  
  /**
   * Receives a MarketBootstrapData message, reporting usage and prices
   * for the bootstrap period. We record the overall weighted mean price,
   * as well as the mean price and usage for a week.
   */
  public synchronized void handleMessage (MarketBootstrapData data)
  {
    int discardedTimeslots = Competition.currentCompetition().getBootstrapDiscardedTimeslots(); 
    for (int i = 0; i < data.getMwh().length; i++) {
      int timeslot = i + discardedTimeslots;
      double mwh = data.getMwh()[i];
      double price = data.getMarketPrice()[i]; 
      recordWholesaleTx(ts2boot, timeslot, mwh, price);
    }
  }


  /**
   * Receives a new MarketTransaction. 
   */
  public synchronized void handleMessage (MarketTransaction tx)
  {
    // safety check
    if (tx.getMWh() * tx.getPrice() > 0) {
      log.error("How come mwh and price have the same sign in market transaction");
      return; // do nothing
    }

    recordWholesaleTx(ts2mtx, 
                      tx.getTimeslotIndex(), 
                      tx.getMWh(), 
                      tx.getPrice());
  }
  

  /**
   * Receives a new DistributionReport. 
   */
  public synchronized void handleMessage (DistributionReport rep)
  {
    recordDistributionReport(rep);
  }  

  
  /**
   * Receives a new consumption tx. 
   */
  public synchronized void handleMessage (TariffTransaction tx)
  {
    if (tx.getTxType() == Type.CONSUME) {
      recordConsumptionTx(tx);
    }
  }
  

  /**
   * Receives a new ClearedTrade. 
   */
  public synchronized void handleMessage (ClearedTrade trade)
  {
    recordWholesaleTx(ts2trade, 
                      trade.getTimeslotIndex(),
                      trade.getExecutionMWh(), 
                      -Math.abs(trade.getExecutionPrice())); //assuming buy, make price negative to conform with signed boot data
  }



  // ------------- interface methods -----------------
  
  @Override
  public double predictUnitCostMwh(int currentTimeslot, int futureTimeslot, double myMwh, double competitorMwh) {
    WekaLinRegData wekaData = retrieveOrCreateWekaData(currentTimeslot);
    if (null == wekaData) {
      log.error("Failed to get wekaData, falling back to avg data from array");
      return getAvgBootstrapPricePerMwh(futureTimeslot);
    }
    return makeMwhPrediction(wekaData, futureTimeslot, myMwh, competitorMwh); 
  }


  @Override
  public double predictUnitCostKwh(int currentTimeslot, int futureTimeslot, double myKwh, double competitorKwh) {
    double myMwh = myKwh / 1000.0;
    double competitorMwh = competitorKwh / 1000.0;
    double unitCostPerMwh = predictUnitCostMwh(currentTimeslot, futureTimeslot, myMwh, competitorMwh);
    double unitCostPerKwh = unitCostPerMwh / 1000.0;
    return unitCostPerKwh;
  }


  @Override
  public double getFudgeFactorKwh(int currentTimeslot) {
    // compute avg correction
    int numPoints = 0;
    double totalCorrectionsToPricePerMwh = 0;
    int prevTimeslot = currentTimeslot - 1; // this should be last ts for which there's data
    for (int timeslot = prevTimeslot - FUDGE_MEMORY_LENGTH; timeslot < prevTimeslot; ++timeslot) {
      Double predPricePerMwh = ts2consumptionBasedCostPrediction.get(timeslot);
      Double actualPricePerMwh = ts2actualCost.get(timeslot);
      if (predPricePerMwh != null && actualPricePerMwh != null) {
        log.debug("Fudge: adding point for " + timeslot);
        double correctionToPricePerMwh = actualPricePerMwh - predPricePerMwh;
        totalCorrectionsToPricePerMwh  += correctionToPricePerMwh;
        ++numPoints;
      }
    }
    
    double fudgeFactor;
    if (numPoints == FUDGE_MEMORY_LENGTH) {
      // epsilon to avoid 0 division
      double epsilon = 1e-6;
      double avgCorrectionPerMwh = totalCorrectionsToPricePerMwh / (numPoints + epsilon);
      double avgCorrectionPerKwh = avgCorrectionPerMwh / 1000.0;
      fudgeFactor = avgCorrectionPerKwh;
      log.debug("FudgeFactor for cost-curve: " + fudgeFactor);
    }
    else {
      fudgeFactor = 0;
    }
    return fudgeFactor;
  }



  // ------------- subroutines ------------------

  void recordWholesaleTx(HashMap<Integer, ArrayList<PriceMwhPair>> container, 
                         int txTimeslot, 
                         double executionMwh, 
                         double executionPrice) {
    
    // we record everything as positive (they must have opposite sign)
    double price = /*Math.abs*/(executionPrice);
    double mwh = /*Math.abs*/(executionMwh);
    
    // actual code

    // record cost curve
    getWholesaleTransactions(container, txTimeslot).add(new PriceMwhPair(price, mwh));

    // record average as well
    updateAvgsArray(txTimeslot, mwh, price);
  }


  void recordDistributionReport(DistributionReport rep) {

    // TODO: should get timeslot information from DistributionReport (once it 
    // has this information in a future version of the simulator), not from
    // timeslotRepo, since this would be wrong in case of network sync issues.
    
    int distReportTimeslot = timeslotRepo.currentSerialNumber();   
    double totalConsumptionMwh = rep.getTotalConsumption() / 1000.0;
    ts2totalcons.put(distReportTimeslot, totalConsumptionMwh);
    //ts2totalcons.remove(timeslotToForget(distReportTimeslot));
  }


  void recordConsumptionTx(TariffTransaction tx) {
    if (! (tx.getKWh() <= 0) )  { // <= and not <, since some customers report 0 consumption.
      log.error("How come kwh > 0 in a consumption-tx? ignoring transaction...");
    }

    int txTimeslot = tx.getPostedTimeslotIndex();
    // make it positive, like distribution report amount
    double mwh = Math.abs(tx.getKWh()) / 1000.0;
    
    // put (if new) or add to existing
    Double timeslotMwh = ts2mycons.get(txTimeslot);
    if (null == timeslotMwh) {
      timeslotMwh = mwh;      
    }
    else {
      timeslotMwh += mwh;
    }
    ts2mycons.put(txTimeslot, timeslotMwh);
    //ts2mycons.remove(timeslotToForget(txTimeslot));
  }


  /**
   * Calling this function assumes that avg-array price is negative
   * (ie buying more than selling for each slot). 
   */ 
  double getAvgBootstrapPricePerMwh(int futureTimeslot) {
    int index = indexIntoRecord(futureTimeslot);
    double totalMWh = bootstrapMWh[index];
    double totalCharges = bootstrapPayments[index];
    if (totalMWh == 0) {
      log.warn("marketTotalMwh should not be 0");
      totalMWh = 1e-9;
    }
    return totalCharges / Math.abs(totalMWh); 
  }


  private double makeMwhPrediction(WekaLinRegData wekaData, int futureTimeslot,
      double myMwh, double competitorMwh) {
    // predict
    ArrayList<Double> features = configuratorFactoryService.getCostCurvesDataProcessor().extractFeatures(myMwh, competitorMwh);
    Instance inst = RegressionUtils.createInstance(features);
    ArrayList<Instance> instArr = new ArrayList<Instance>();
    instArr.add(inst);
    ArrayList<String> attrNames = configuratorFactoryService.getCostCurvesDataProcessor().getFeatureNames();
    Instances x0_features = RegressionUtils.createInstances(instArr, attrNames);
    
    Instances x0_featuresNorm = RegressionUtils.featureNormalize(x0_features, wekaData.getStandardize());
    Instances x0_final = RegressionUtils.addYforWeka(x0_featuresNorm); // no yvals, missing values 
    double prediction = 0;
    try {
      prediction = wekaData.getLinearRegression().classifyInstance(x0_final.instance(0));
    } catch (Exception e) {
      log.error("passed CV but cannot predict on new point. falling back to array");
      log.error("Exception is", e);
      return getAvgBootstrapPricePerMwh(futureTimeslot);
    }
    return prediction;
  }


  private WekaLinRegData retrieveOrCreateWekaData(int currentTimeslot) {
    if (null == wekaData || wekaData.getTimeslot() != currentTimeslot) {
      // remove old examples from training set
      //forgetData(currentTimeslot);
      
      WekaXYLambdas xylambdas = configuratorFactoryService.getCostCurvesDataProcessor().
          createWekaRegressionInput(
            currentTimeslot,
            ts2boot,
            ts2mtx,
            ts2trade,
            ts2mycons,
            ts2totalcons
          );
      
      try {
        wekaData = RegressionUtils.createWekaLinRegData(currentTimeslot, xylambdas.getX(), xylambdas.getY(), xylambdas.getLambdas());
      } catch (Exception e) {
        log.error("Exception while trying to get Weka Data ", e);
        wekaData = null;
      }
    }
    return wekaData; 
  }


  /**
   * clean containers from old data NOTE: any container
   * that collects data for regression should be here
   */
  private void forgetData(int currentTimeslot) {
    forgetDataFromContainer(currentTimeslot, ts2boot); // comment out when use boot-curve
    forgetDataFromContainer(currentTimeslot, ts2mtx);
    forgetDataFromContainer(currentTimeslot, ts2trade);
    forgetDataFromContainer(currentTimeslot, ts2mycons);
    forgetDataFromContainer(currentTimeslot, ts2totalcons);
  }


  /**
   * remove data older than MEMORY_LENGTH
   * @param currentTimeslot
   * @param container
   */
  private <T> void forgetDataFromContainer(int currentTimeslot, HashMap<Integer, T> container) {
    int oldesetTimeslot = currentTimeslot - MEMORY_LENGTH;
    for(Iterator<Map.Entry<Integer, T>> it = container.entrySet().iterator(); it.hasNext(); ) {
      Entry<Integer, T> entry = it.next();
      if(entry.getKey().compareTo(oldesetTimeslot) < 0) {
        it.remove();
      }
    }
  }


  /**
   * @param timeslot
   * @return
   */
  private int indexIntoRecord(int timeslot) {
    int discardedTimeslots = Competition.currentCompetition().getBootstrapDiscardedTimeslots(); 
    return (timeslot - discardedTimeslots) % configuratorFactoryService.CONSTANTS.USAGE_RECORD_LENGTH();
  }


  private void updateAvgsArray(int timeslot, double executionMwh,
      double executionPrice) {
    int index = indexIntoRecord(timeslot);
    bootstrapMWh[index] += executionMwh;
    bootstrapPayments[index] += executionPrice * Math.abs(executionMwh);
  }


  private ArrayList<PriceMwhPair> getWholesaleTransactions(
      HashMap<Integer, ArrayList<PriceMwhPair>> container, 
      int timeslot) {
    ArrayList<PriceMwhPair> result = container.get(timeslot);
    if (null == result) {
      result = new ArrayList<PriceMwhPair>();
      container.put(timeslot, result);
    }
    return result;
  }


  private int timeslotToForget(int incomingTxTimeslot) {
    return incomingTxTimeslot - MEMORY_LENGTH;
  }
  

  private double computeAvgPrice(ArrayList<PriceMwhPair> transactions) {
    double totalMwh = 0;
    double totalCharges = 0;
    for (PriceMwhPair p : transactions) {
      totalMwh += p.getMwh();
      totalCharges += Math.abs(p.getMwh()) * p.getPricePerMwh();// without abs product always < 0
    } 
    double avgPrice = totalCharges / Math.abs(totalMwh);// without abs division always < 0
    return avgPrice;
  }


  private Double computeTotalMwh(ArrayList<PriceMwhPair> transactions) {
    double totalMwh = 0;
    for (PriceMwhPair p : transactions) {
      totalMwh += p.getMwh();
    } 
    return totalMwh;
  }

}
