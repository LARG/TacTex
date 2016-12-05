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
package edu.utexas.cs.tactex.subscriptionspredictors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;

import edu.utexas.cs.tactex.interfaces.CandidateTariffSubsPredictor;
import edu.utexas.cs.tactex.utils.RegressionUtils;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaLinRegData;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaXYLambdas;
import weka.classifiers.functions.LinearRegression;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.Standardize;

/**
 * @author urieli
 *
 */
public class PolyRegCust 
implements CandidateTariffSubsPredictor {

  static private Logger log = Logger.getLogger(PolyRegCust.class);
  
  private HashMap<CustomerInfo, WekaLinRegData> customer2wekadata;
  

  public PolyRegCust() {
    customer2wekadata = new HashMap<CustomerInfo, WekaLinRegData>();
  }


  @Override
  public Double predictNumSubs(double candidateEval,
      TreeMap<Double, Double> e2n, CustomerInfo customer, int timeslot) {
    
    int maxDegree = 8;
    
    
    WekaLinRegData wekaData = retrieveOrCreateWekaData(e2n, maxDegree, customer, timeslot);
    if (null == wekaData) {
      return null; // errors should have been printed inside
    }
 
    // predict
    Double[] x0 = new Double[] {candidateEval};
    Instances x0_poly = RegressionUtils.polyFeatures1D(x0, maxDegree);
    log.info("x0_poly " + x0_poly);
    Instances x0_polynorm = RegressionUtils.featureNormalize(x0_poly, wekaData.getStandardize());
    log.info("x0_polynorm " + x0_polynorm);
    Instances x0_final = RegressionUtils.addYforWeka(x0_polynorm); // no yvals, missing values 
    log.info("x0_final "  + x0_final);
    double prediction = 0;
    try {
      prediction = wekaData.getLinearRegression().classifyInstance(x0_final.instance(0));
      log.info("prediction " + prediction + " => " + Math.max(0, (int)Math.round(prediction)));
    } catch (Exception e) {
      log.error("PolyReg passed CV but cannot predict on new point. falling back to interpolateOrNN()");
      log.error("Exception is", e);
      log.error("e2n: " + e2n.toString());
      log.error("candidateEval " + candidateEval);
      return null;
    }
    log.info("PolyReg succeeded");
    // cast to int, and cannot be negative
    return Math.max(0.0, Math.round(prediction));
  }
        

  private WekaLinRegData retrieveOrCreateWekaData(TreeMap<Double,Double> e2n,
      int maxDegree, CustomerInfo customer, int timeslot) {
    
    // try to retrieve
    WekaLinRegData result = customer2wekadata.get(customer);
    
    // create if needed
    if (null == result || result.getTimeslot() != timeslot) {

      WekaXYLambdas xylambdas = createWekaRegressionInput(e2n, maxDegree);
      
      try {

        result = RegressionUtils.createWekaLinRegData(timeslot, xylambdas.getX(), xylambdas.getY(), xylambdas.getLambdas());
        // don't forget to put into map
        customer2wekadata.put(customer, result);

      } catch (Exception e) {
        log.error("Exception while trying to get Weka Data ", e);
        result = null;
      }
      
    }
    return result;
  }
    

  private WekaXYLambdas createWekaRegressionInput(TreeMap<Double, Double> e2n,
      int maxDegree) {
    Double[] xvals = new Double[e2n.size()];
    Double[] yvals = new Double[e2n.size()];
    int i = 0;
    for (Entry<Double, Double> entry : e2n.entrySet()) {
      xvals[i] = entry.getKey();
      yvals[i] = entry.getValue();
      log.info("xvals["+i+"]="+xvals[i] + " yvals["+i+"]="+yvals[i]);
      ++i;
    }
    
    // polynomial features
    Instances X = RegressionUtils.polyFeatures1D(xvals, maxDegree);
    log.info("X " + X);

    ArrayList<Double> candidateLambdas = createCandidateLambdas();

    WekaXYLambdas xylambdas = new WekaXYLambdas(X, yvals, candidateLambdas);
    return xylambdas;
  }


  /**
   * This method generates the candidate regularization
   * parameters
   * @return
   */
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
