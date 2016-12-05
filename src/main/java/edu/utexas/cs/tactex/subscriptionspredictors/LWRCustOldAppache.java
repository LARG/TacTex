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
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;

import edu.utexas.cs.tactex.interfaces.CandidateTariffSubsPredictor;

/**
 * @author urieli
 *
 */
public class LWRCustOldAppache 
implements CandidateTariffSubsPredictor {
  static private Logger log = Logger.getLogger(LWRCustOldAppache.class);

  private static final double SQUEEZE = 0.8;
  private static final double OFFSET = 0.1;

  /**
   * @param candidateEval
   * @param e2n
   * @return
   */
  @Override
  public Double predictNumSubs(double candidateEval, TreeMap<Double, Double> e2n, CustomerInfo customer, int timeslot) {
    // tree map guarantees that keys are unique
    // so we are suppose to be able to run LWR
    // if there are at least 3 entries (even 2)
    
    
    // LWR, run n-fold cross validation with different bandwidth
    
    double min = e2n.firstKey();
    double max = e2n.lastKey();
    ArrayRealVector xVec = createNormalizedXVector(e2n.keySet(), min, max); 
    ArrayRealVector yVec = createYVector(e2n.values()); 
    
    double bestTau = Double.MAX_VALUE;
    double bestMSE = Double.MAX_VALUE;

    ArrayList<Double> candidateTaus = new ArrayList<Double>(); 
    //candidateTaus.add(0.025 * SQUEEZE);
    candidateTaus.add(0.05);// * SQUEEZE);
    candidateTaus.add(0.1);// * SQUEEZE);
    candidateTaus.add(0.2);// * SQUEEZE);
    candidateTaus.add(0.3);// * SQUEEZE);
    candidateTaus.add(0.4);// * SQUEEZE);
    candidateTaus.add(0.5);// * SQUEEZE);
    candidateTaus.add(0.6);// * SQUEEZE);
    candidateTaus.add(0.7);// * SQUEEZE);
    candidateTaus.add(0.8);// * SQUEEZE);
    candidateTaus.add(0.9);// * SQUEEZE);
    candidateTaus.add(1.0);// * SQUEEZE);
    for (Double tau : candidateTaus) {
      Double mse = CrossValidationError(tau, xVec, yVec);
      if (null == mse) {
        log.error(" cp cross-validation failed, return null");
        return null;
      }
      if (mse < bestMSE) {
        bestMSE = mse;
        bestTau = tau;
      }
    }
    log.info(" cp LWR bestTau " + bestTau);
    double x0 = candidateEval;
    Double prediction = LWRPredict(xVec, yVec, normalizeX(x0, min, max), bestTau);
    if (null == prediction) {
      log.error("LWR passed CV but cannot predict on new point. falling back to interpolateOrNN()");
      log.error("e2n: " + e2n.toString());
      log.error("candidateEval " + candidateEval);
      return null;
    }
    // cast to int, and cannot be negative
    return Math.max(0, (double)(int)(double)prediction);
  }


   /**
    * Compute the n-fold Cross validation error with LWR and a given Tau
    * 
    * @param tau
    * @param x
    * @param y
    * @return
    */
  private Double CrossValidationError(Double tau, ArrayRealVector x,
      ArrayRealVector y) {
    int n = x.getDimension();
    double totalError = 0.0;
    for (int i = 0; i < n; ++i) {
      // CV fold for i
      double x_i = x.getEntry(i);
      double y_i = y.getEntry(i);
      ArrayRealVector Xcv = new ArrayRealVector( (ArrayRealVector)x.getSubVector(0, i), x.getSubVector(i+1, n - (i+1)) );
      ArrayRealVector Ycv = new ArrayRealVector( (ArrayRealVector)y.getSubVector(0, i), y.getSubVector(i+1, n - (i+1)) );
      Double y_predicted = LWRPredict(Xcv, Ycv, x_i, tau);
      if (null == y_predicted) {
        log.error(" cp LWR cannot predict - returning NULL");
        return null;
      }
      double predictionError = y_predicted - y_i;
      totalError += predictionError * predictionError;
    }  
    return totalError;
  }


  /**
    * LWR prediction
    * 
    * @param X
    * @param Y
    * @param x0
    * @param tau
    * @return
    */
  public Double LWRPredict(ArrayRealVector X, ArrayRealVector Y,
      double x0, final double tau) {
    ArrayRealVector X0 = new ArrayRealVector(X.getDimension(), x0);
    ArrayRealVector delta = X.subtract(X0);
    ArrayRealVector sqDists = delta.ebeMultiply(delta);
    UnivariateFunction expTau = new UnivariateFunction() {      
      @Override
      public double value(double arg0) {        
        //log.info(" cp univariate tau " + tau);
        return Math.pow(Math.E, -arg0 / (2*tau));
      }
    };
    ArrayRealVector W = sqDists.map(expTau);
    double Xt_W_X = X.dotProduct(W.ebeMultiply(X));
    if (Xt_W_X == 0.0) {
      log.error(" cp LWR cannot predict - 0 denominator returning NULL");
      log.error("Xcv is " + X.toString());
      log.error("Ycv is " + Y.toString());
      log.error("x0 is " + x0);
      return null; // <==== NOTE: a caller must be prepared for it
    }
    double theta = ( 1.0 / Xt_W_X ) * X.ebeMultiply(W).dotProduct(Y) ;  
    
    return theta * x0;    
  }


  private ArrayRealVector createNormalizedXVector(Set<Double> xValues, double min, double max) {
     Double[] dummy1 = new Double[1];  // needed to determine the type of toArray?     
     ArrayRealVector xVector = new ArrayRealVector(xValues.toArray(dummy1));
     xVector.mapSubtractToSelf(min);
     xVector.mapDivideToSelf(max - min);
     // translating [0,1]=>[0.1,0.9]
     xVector.mapMultiplyToSelf(SQUEEZE);
     xVector.mapAddToSelf(OFFSET);
     return xVector;
  }


  private ArrayRealVector createYVector(Collection<Double> yValues) {
    // couldn't create ArrayRealVector from Integer collection
    // so just manually copying values    
    double[] doubleArray = new double[yValues.size()];
    int i = 0;       
    for (Iterator<Double> it = yValues.iterator(); it.hasNext(); ++i) {
      doubleArray[i] = it.next();
    }
    return new ArrayRealVector(doubleArray);
  }


  /**
   * NOTE: be careful not to call it with max == min 
   */
  private double normalizeX(double xVal, double min, double max) {
    return ( (xVal - min) / (max - min) * SQUEEZE) + OFFSET;    
  }
}
