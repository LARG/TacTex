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
import java.util.Arrays;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.Activatable;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.interfaces.OpponentPredictor;
import edu.utexas.cs.tactex.utils.RegressionUtils;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaLinRegData;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaXYLambdas;
import edu.utexas.cs.tactex.utils.RegressionUtils.XYForRegression;
import weka.core.Instance;
import weka.core.Instances;

/**
 * predicting opponent actions, focusing only on consumption
 * @author urieli
 *
 */
@Service
public class OpponentPredictorService 
implements OpponentPredictor, Initializable, Activatable {

  static private Logger log = Logger.getLogger(OpponentPredictorService.class);


  private static final int FIRST_SIMULATION_TIMESLOT = 360;
  private static final int FIRST_TARIFF_NOTIFICATION_TS = 360 + 1;
  private final ArrayList<String> RAW_ATTR_NAMES = new ArrayList<String>(Arrays.asList("bestRatesRatio"));
  private final ArrayList<Double> candidateLambdas = createCandidateLambdas(); 

  
  // starts as null
  private WekaLinRegData wekaData;
  
  
  // initialized in initialize()
  private BrokerContext brokerContext;
  private TreeMap<Integer, TariffSpecification> myTs2tariff;
  private TreeMap<Integer, TariffSpecification> opponentTs2tariff;

  private int lastTimeslot;


  @Override
  public void initialize(BrokerContext brokerContext) {
    
    // This fields should be tested in test_initialize()
    wekaData = null;
    
    this.brokerContext = brokerContext;
    myTs2tariff = new TreeMap<Integer, TariffSpecification>(); 
    opponentTs2tariff = new TreeMap<Integer, TariffSpecification>();
    
    lastTimeslot = FIRST_SIMULATION_TIMESLOT; 
  }


  @Override
  public void activate(int timeslot) {
    lastTimeslot = timeslot; // track time
  }
  

  // --------------------- message handling -------------------

  public synchronized void handleMessage (TariffSpecification spec)
  {
    log.debug("handleMessage: " + spec);
    // Assumption: currently only focusing on CONSUMPTION, ignoring storage, EV and so on
    if ( spec.getPowerType() != PowerType.CONSUMPTION ) {
      return;
    }

    if (specPublishedByMe(spec)) {
      addOwnTariff(spec); 
    }
    else {
      addCompetingTariff(spec);
    }
  }
  

  // --------------------- subroutines ------------------------
  
  private void addOwnTariff(TariffSpecification spec) {
    int currentTimeslot = fixSyncIssues(lastTimeslot);
    log.debug("addOwnTariff " + spec);
    myTs2tariff.put(currentTimeslot, spec);
  }


  private void addCompetingTariff(TariffSpecification spec) {
    int currentTimeslot = fixSyncIssues(lastTimeslot);
    log.debug("addCompetingTariff " + spec);
    opponentTs2tariff.put(currentTimeslot, spec);
  }


  /**
   * if there are some sync delays, we round it to the 
   * upper 361 + 6 x i timeslot. (Upper since our clock is typically delayed
   * compared with the server)
   * @param lastTimeslot2
   * @return
   */
  private int fixSyncIssues(int lastTimeslot) {
    return FIRST_TARIFF_NOTIFICATION_TS + (int)Math.ceil((lastTimeslot - FIRST_TARIFF_NOTIFICATION_TS) / 6.0) * 6;
  }


  private boolean specPublishedByMe(TariffSpecification spec) {
    return spec.getBroker().getUsername().equals(brokerContext.getBrokerUsername());
  }
  

  /**
   * Main method
   */ 
  @Override
  public ArrayList<Double> predictOpponentRates(double mySuggestedRate, int currentTimeslot) {
    
    log.debug("predictOpponentRates(" + mySuggestedRate + ", " + currentTimeslot + ")");

    ArrayList<Double> result = new ArrayList<Double>();

    try {
      
      if (myTs2tariff.size() < 4 && opponentTs2tariff.size() < 4) {
        log.error("not enough data to predict");
        return result;  
      }

      WekaLinRegData wekaData = retrieveOrCreateWekaData(currentTimeslot);
      if (null == wekaData) {
        log.error("Failed to get wekaData, falling back to avg data from array");
        return result;
      }
 
      double opponentBestRate = getBestRate(opponentTs2tariff);
      // predict opponent current action
      double myBestRate = getBestRate(myTs2tariff);
      double state = createStateFeature(myBestRate, opponentBestRate);
      result.addAll(makeRatesPrediction(wekaData, state, myBestRate, opponentBestRate));
      //log.info("opponentPrediction for timeslot=" + currentTimeslot + ": " + result.toString());
      // predict opponent response action
      myBestRate = bestRate(mySuggestedRate, myBestRate);
      state = createStateFeature(myBestRate, opponentBestRate);
      result.addAll(makeRatesPrediction(wekaData, state, myBestRate, opponentBestRate));
      
    } catch (Throwable e) {
      log.error("caught exception from predictOpponentRates ", e);
    }
    
    return result;
  }
  
  
  private WekaLinRegData retrieveOrCreateWekaData(int currentTimeslot) {
    if (null == wekaData || wekaData.getTimeslot() != currentTimeslot) {
      
      // remove old examples from training set
      //forgetData(currentTimeslot);
      
      WekaXYLambdas xylambdas = createWekaRegressionInput();
      
      try {

        // the new Double[0] is just a needed parameter for convertion Object[] => Double[]
        wekaData = RegressionUtils.createWekaLinRegData(currentTimeslot, xylambdas.getX(), xylambdas.getY(), xylambdas.getLambdas());

      } catch (Exception e) {
        log.error("Exception while trying to get Weka Data ", e);
        wekaData = null;
      }
    }
    
    return wekaData; 
  }

  
  private WekaXYLambdas createWekaRegressionInput() {
    // find boundaries
    int minTimeslot = Math.min(myTs2tariff.firstKey(), opponentTs2tariff.firstKey());
    int maxTimeslot = Math.max(myTs2tariff.lastKey(), opponentTs2tariff.lastKey());
    
    double myBestRate = -1;  // normal range is [-0.500,0]
    double oppBestRate = -1; // normal range is [-0.500,0]
    ArrayList<Double> xvals = new ArrayList<Double>();
    ArrayList<Double> yvals = new ArrayList<Double>();
    for (int timeslot = minTimeslot; timeslot < maxTimeslot; timeslot += 6) {
      // update best rates
      myBestRate = extractBestRate(myBestRate, myTs2tariff.get(timeslot));
      oppBestRate = extractBestRate(oppBestRate, opponentTs2tariff.get(timeslot));
      // create x=>y (i.e. state=>action)      
      int actionTimeslot = timeslot + 6;
      double state = createStateFeature(myBestRate, oppBestRate); // X
      double action = createActionFeature(myBestRate, oppBestRate, actionTimeslot);
      xvals.add(state);
      yvals.add(action);
    }

    // convert to matrices
    int numInst = xvals.size();
    double[][] X = new double[numInst][1];
    Double[] Y = new Double[numInst];
    for (int i = 0; i < numInst; ++i) {
      X[i][0] = xvals.get(i);
      Y[i] = yvals.get(i);
    }
    RegressionUtils.XYForRegression xy = new XYForRegression(X, Y, RAW_ATTR_NAMES);
    // step 2: convert observation-matrix to feature matrix (as weka-instances)
    Instances Xinst = convertRawXToFeatureWekaInstances(xy.getX());
    return new WekaXYLambdas(Xinst, xy.getY(), candidateLambdas);
  }


  private double createStateFeature(double myBestRate, double oppBestRate) {
    return oppBestRate / myBestRate;
  }


  private double createActionFeature(double myBestRate, double oppBestRate,
      int actionTimeslot) {
    double bestRate = bestRate(myBestRate, oppBestRate);
    TariffSpecification opponentAction = opponentTs2tariff.get(actionTimeslot);
    if (null == opponentAction) {
      return oppBestRate; // Assumption: no action => repeat.
    }
    double actionFeature = getRateValue(opponentAction) / bestRate;
    log.debug("action feature: " + actionFeature);
    return actionFeature;
  }


  /**
   * skipping the stage of creating a 'features' matrix
   * and directly creating weka instances
   * @param Xorig
   * @return
   */
  private Instances convertRawXToFeatureWekaInstances(double[][] Xorig) {
    int numInst = Xorig.length;    
    ArrayList<Instance> instArr = new ArrayList<Instance>();
    for (int i = 0; i < numInst ; ++i) {
      // if we are here, we have 1-column, which becomes our x value
      double x = Xorig[i][0];
      // extract features
      ArrayList<Double> features = myExtractFeatures(x);
      // create weka instance
      Instance inst = RegressionUtils.createInstance(features);
      instArr.add(inst);
    }
    Instances Xinst = RegressionUtils.createInstances(instArr, RAW_ATTR_NAMES);
    return Xinst;
  }
  

  private ArrayList<Double> myExtractFeatures(double x) {
    int maxDegree = 1;
    return RegressionUtils.create1DPolyFeatures(x, maxDegree);
  }
  
  
  private double getBestRate(TreeMap<Integer, TariffSpecification> ts2tariff) {
    double bestRate = -Double.MAX_VALUE;
    for (TariffSpecification spec : ts2tariff.values()) {
      bestRate = extractBestRate(bestRate, spec);
    }
    return bestRate;
  }


  private double extractBestRate(double bestRate,
      TariffSpecification tariffSpecification) {
    if (null == tariffSpecification)
      return bestRate;
    return bestRate(bestRate, getRateValue(tariffSpecification)); 
  }


  private double bestRate(double rate1, double rate2) {
    return Math.max(rate1, rate2);
  }


  private double getRateValue(TariffSpecification tariffSpecification) {
    double numRates = 0.000000001; //<= that's 0 (avoid division by zero)
    double total = 0;
    for (Rate r : tariffSpecification.getRates()) {
      //                        hours-per-day                    x             days-per-week    // in case of one rate they will be 1 (=-1 - -1 + 1)
      double weight = (r.getDailyEnd() - r.getDailyBegin() + 1) * (r.getWeeklyEnd() - r.getWeeklyBegin() + 1);
      numRates += weight;
      total += r.getValue() * weight; 
    }
    return total / numRates;
  }
  

  private ArrayList<Double> makeRatesPrediction(WekaLinRegData wekaData,
      double state, double myBestRate, double opponentBestRate) {
    
    ArrayList<Double> predictedActions = new ArrayList<Double>();
    
    // predict
    ArrayList<Double> features = new ArrayList<Double>();
    features.add(state);
    //
    Instance inst = RegressionUtils.createInstance(features);
    ArrayList<Instance> instArr = new ArrayList<Instance>();
    instArr.add(inst);
    ArrayList<String> attrNames = RAW_ATTR_NAMES;
    Instances x0_features = RegressionUtils.createInstances(instArr, attrNames);
    
    Instances x0_featuresNorm = RegressionUtils.featureNormalize(x0_features, wekaData.getStandardize());
    Instances x0_final = RegressionUtils.addYforWeka(x0_featuresNorm); // no yvals, missing values 
    double prediction = 0;
    try {
      prediction = wekaData.getLinearRegression().classifyInstance(x0_final.instance(0));
      predictedActions.add(prediction);
    } catch (Exception e) {
      log.error("passed CV but cannot predict on new point. falling back to array");
      log.error("Exception is", e);
    }
    
    return convertActionsToRates(predictedActions, myBestRate, opponentBestRate);
  }
  

  private ArrayList<Double> convertActionsToRates(
      ArrayList<Double> predictedActions, double myBestRate, double oppBestRate) {
    double referenceRate = bestRate(myBestRate, oppBestRate);
    ArrayList<Double> result = new ArrayList<Double>();
    for (Double a : predictedActions) {
      result.add(a * referenceRate);
    }
    return result;
  }

  private ArrayList<Double> createCandidateLambdas() {
    ArrayList<Double> candidateLambdas = new ArrayList<Double>(); 
    candidateLambdas.add(0.0); // no regularization
    //    candidateLambdas.add(0.00003);
    //    candidateLambdas.add(0.0001);
    //    candidateLambdas.add(0.0003);
    //    candidateLambdas.add(0.001);
    //    candidateLambdas.add(0.003);
    //    candidateLambdas.add(0.01);
    //    candidateLambdas.add(0.03);
    //    candidateLambdas.add(0.1);
    //    candidateLambdas.add(0.3);
    //    candidateLambdas.add(1.0);
    //    candidateLambdas.add(3.0);
    //    candidateLambdas.add(10.0);
    //    candidateLambdas.add(30.0);
    //    candidateLambdas.add(100.0);
    return candidateLambdas;
  }

  
}
