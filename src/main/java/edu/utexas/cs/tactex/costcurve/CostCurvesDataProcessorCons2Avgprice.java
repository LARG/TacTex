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
package edu.utexas.cs.tactex.costcurve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import edu.utexas.cs.tactex.interfaces.CostCurvesDataProcessor;
import edu.utexas.cs.tactex.utils.RegressionUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.PriceMwhPair;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaXYLambdas;
import edu.utexas.cs.tactex.utils.RegressionUtils.XYForRegression;
import weka.core.Instance;
import weka.core.Instances;

/**
 * @author urieli
 *
 */
public class CostCurvesDataProcessorCons2Avgprice implements
    CostCurvesDataProcessor {
  
  static private Logger log = Logger.getLogger(CostCurvesDataProcessorCons2Avgprice.class);

  // raw x-attributes assumed/used by this class
  private final ArrayList<String> RAW_ATTR_NAMES = new ArrayList<String>(Arrays.asList("mycons", "totalcons"));
  // candidate regularization parameters
  private final ArrayList<Double> candidateLambdas = createCandidateLambdas(); 


  @Override
  public ArrayList<Double> extractFeatures(double neededMwh,
      double competitorMwh) {
    double[] x = new double[] {neededMwh, neededMwh + competitorMwh}; 
    return myExtractFeatures(x); 
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.CostCurvesDataProcessor#getFeatureNames()
   */
  @Override
  public ArrayList<String> getFeatureNames() {
    return RAW_ATTR_NAMES;
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.CostCurvesDataProcessor#createWekaRegressionInput(int)
   */
  @Override
  public WekaXYLambdas createWekaRegressionInput(
      int currentTimeslot,
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2boot,
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2mtx,
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2trade,
      HashMap<Integer, Double> ts2mycons,
      HashMap<Integer, Double> ts2totalcons 
    ) {
    // step 1: convert observations to matrix
    RegressionUtils.XYForRegression xy = createXYForCons2Avgprice(
      currentTimeslot,
      ts2boot,
      ts2mtx,
      ts2trade,
      ts2mycons,
      ts2totalcons);
    double[][] Xorig = xy.getX();
    
    // sanity check that there is 1 column
    double [] firstRow = Xorig[0];
    if (firstRow.length != 2) {
      log.error("How come X does not have 2 columns?");
      return null;
    }

    // step 2: convert observation-matrix to feature matrix (as weka-instances)
    Instances Xinst = convertRawXToFeatureWekaInstances(Xorig);

    return new WekaXYLambdas(Xinst, xy.getY(), candidateLambdas);
  }


  // ---------------- private methods -----------------
  private XYForRegression createXYForCons2Avgprice(
      int currentTimeslot, 
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2boot, 
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2mtx, 
      HashMap<Integer,ArrayList<PriceMwhPair>> ts2trade, 
      HashMap<Integer, Double> ts2mycons, 
      HashMap<Integer, Double> ts2totalcons) {
    
    // will hold x_1
    ArrayList<Double> xMycons = new ArrayList<Double>();
    // will hold x_2
    ArrayList<Double> xTotalcons = new ArrayList<Double>();
    // will hold y
    ArrayList<Double> yvals = new ArrayList<Double>();
    //
    
    // 1. add boot data
    //
    for( Entry<Integer, ArrayList<PriceMwhPair>> e : ts2boot.entrySet()) {
      int timeslot = e.getKey();
      ArrayList<PriceMwhPair> transactions = e.getValue();
      // use only past timeslots (in the coming 24 timeslots 
      // has only partial trade data since trading is still ongoing)
      if (timeslot < currentTimeslot) {
        // add x_2, y
        createAndAddTrainingExample(xTotalcons, yvals, transactions);
        // add x_1 (missing data => 0) TODO: modeling assumption - works?
        xMycons.add(0.0); 
      }
    }
    //
    // 2. add game data
    //
    // create intersection of all keys
    Set<Integer> commonTxTimeslots = new HashSet<Integer>(ts2mtx.keySet()); // use the copy constructor
    //
    // for using <my-pred-cons, total-pred-cons> => avg-mtx 
    commonTxTimeslots.retainAll(ts2mycons.keySet());
    commonTxTimeslots.retainAll(ts2totalcons.keySet());
    //
    // for using <my-mtx,trade> => avg-mtx
    //commonTxTimeslots.retainAll(ts2trade.keySet());
    //
    for( Integer timeslot : commonTxTimeslots) {
      if (timeslot < currentTimeslot) {
        // x_1
        //xMycons.add(computeTotalMwh(ts2mtx.get(timeslot))); 
        xMycons.add(ts2mycons.get(timeslot));
        //
        // x_2
        //xTotalcons.add(computeTotalMwh(ts2trade.get(timeslot)));
        xTotalcons.add(ts2totalcons.get(timeslot));
        //
        // y
        yvals.add(computeAvgPrice(ts2mtx.get(timeslot)));
      } 
    }
  
    int numInst = yvals.size();
    int numAttr = RAW_ATTR_NAMES.size(); 
    if (numAttr != 2) {
      log.error("Unexpected number of attributes");
    }
    
    double[][] X = new double[numInst][numAttr];
    Double[] Y = new Double[numInst];
    for (int i = 0; i < numInst; ++i) {
      X[i][0] = xMycons.get(i);
      X[i][1] = xTotalcons.get(i);
      Y[i] = yvals.get(i);
    }
    
    return new RegressionUtils.XYForRegression(X, Y, RAW_ATTR_NAMES);
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
      // extract features
      ArrayList<Double> features = myExtractFeatures(Xorig[i]);
      // create weka instance
      Instance inst = RegressionUtils.createInstance(features);
      instArr.add(inst);
    }
    Instances Xinst = RegressionUtils.createInstances(instArr, getFeatureNames());
    return Xinst;
  }


  private ArrayList<Double> myExtractFeatures(double[] x) {
    return RegressionUtils.createRawFeatures(x); 
  }


  private void createAndAddTrainingExample(ArrayList<Double> xvals,
      ArrayList<Double> yvals, ArrayList<PriceMwhPair> transactions) {
    xvals.add(computeTotalMwh(transactions));
    yvals.add(computeAvgPrice(transactions));
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


  private ArrayList<Double> createCandidateLambdas() {
    ArrayList<Double> candidateLambdas = new ArrayList<Double>(); 
    candidateLambdas.add(0.00001);
    candidateLambdas.add(0.00003);
    candidateLambdas.add(0.0001);
    candidateLambdas.add(0.0003);
    candidateLambdas.add(0.001);
    candidateLambdas.add(0.003);
    candidateLambdas.add(0.01);
    candidateLambdas.add(0.03);
    candidateLambdas.add(0.1);
    candidateLambdas.add(0.3);
    candidateLambdas.add(1.0);
    candidateLambdas.add(3.0);
    candidateLambdas.add(10.0);
    candidateLambdas.add(30.0);
    candidateLambdas.add(100.0);
    return candidateLambdas;
  }
}
