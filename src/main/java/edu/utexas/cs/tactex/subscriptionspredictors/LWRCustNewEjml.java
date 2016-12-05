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

import org.apache.log4j.Logger;
import org.ejml.alg.dense.linsol.LinearSolverSafe;
import org.ejml.data.D1Matrix64F;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolver;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.ops.CommonOps;
import org.ejml.simple.SimpleMatrix;
import org.powertac.common.CustomerInfo;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.interfaces.CandidateTariffSubsPredictor;

/**
 * @author urieli
 *
 */
public class LWRCustNewEjml 
implements CandidateTariffSubsPredictor {

  static private Logger log = Logger.getLogger(LWRCustNewEjml.class);

  private static final double SQUEEZE = 0.8;
  private static final double OFFSET = 0.1;

  private ConfiguratorFactoryService configuratorFactoryService;



  public LWRCustNewEjml(ConfiguratorFactoryService configuratorFactoryService) {
    this.configuratorFactoryService = configuratorFactoryService;
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.LWRCustomerPredictor#predictWithLWR(double, java.util.TreeMap)
   */
  @Override
  public Double predictNumSubs(double candidateEval, TreeMap<Double, Double> e2n, CustomerInfo customer, int timeslot) {
    // tree map guarantees that keys are unique
    // so we are suppose to be able to run LWR
    // if there are at least 3 entries (even 2)
    
    
    // LWR, run n-fold cross validation with different bandwidth
    
    double min = e2n.firstKey();
    double max = e2n.lastKey();
    SimpleMatrix xMat = createXMatrix(e2n.keySet(), min, max); 
    SimpleMatrix yVec = createYVector(e2n.values()); 
    
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
      Double mse = CrossValidationError(tau, xMat, yVec);
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
    Double prediction = LWRPredict(xMat, yVec, normalizeXAndAddIntercept(x0, min, max), bestTau);
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
   * @param xMat
   * @param y
   * @return
   */
  public Double CrossValidationError(Double tau, SimpleMatrix X,
      SimpleMatrix y) {
    int n = X.numRows();
    int m = X.numCols();
    SimpleMatrix Xcv = new SimpleMatrix(n - 1, m);
    SimpleMatrix Ycv = new SimpleMatrix(n - 1, 1);
    double totalError = 0.0;
    for (int i = 0; i < n; ++i) {
      // CV fold for i
      SimpleMatrix x_i = X.extractVector(true, i).transpose();
      double y_i = y.get(i); // TODO does it do what it's supposed to?
      // Note that extractMatrix() upper-bound params are exclusive.. 
      if (i > 0) { // first half of matrix 
        Xcv.insertIntoThis(0, 0, X.extractMatrix(  0, i, 0, m));
        Ycv.insertIntoThis(0, 0, y.extractMatrix(  0, i, 0, 1  ));
      }
      if (i < n - 1) {
        Xcv.insertIntoThis(i, 0, X.extractMatrix(i+1, n, 0, m));
        Ycv.insertIntoThis(i, 0, y.extractMatrix(i+1, n, 0, 1  ));
      }
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
   * @param y
   * @param x_0
   * @param tau
   * @return
   */
  public Double LWRPredict(SimpleMatrix X, SimpleMatrix y,
      SimpleMatrix x_0, final double tau) {
    // construct weights vector
    SimpleMatrix ones = new SimpleMatrix(X.numRows(), 1);
    ones.set(1);
    SimpleMatrix X0 = ones.mult(x_0.transpose());
    DenseMatrix64F delta = X.minus(X0).getMatrix();
    DenseMatrix64F squared = new DenseMatrix64F(delta.getNumRows(), delta.getNumCols());
    CommonOps.elementMult(delta, delta, squared);
    SimpleMatrix sqDists = sumMatrixRows(squared);
    SimpleMatrix weights = new SimpleMatrix(sqDists.numRows(), 1);
    for (int i = 0; i < sqDists.numRows(); ++i) {
      weights.set(i, Math.pow(Math.E, -sqDists.get(i) / (2*tau)));// should be Tau^2 
    }
    SimpleMatrix W = SimpleMatrix.diag(weights.getMatrix().getData());
    // pinv(X' W X) X' W y 
    SimpleMatrix X_T = X.transpose();
    
    // ============= using pseudo-inverse ==============
    //SimpleMatrix theta = (X_T.mult(W).mult(X)).pseudoInverse().mult(X_T).mult(W).mult(y);
    
    // ============= using inverse ============
    SimpleMatrix X_T_W_X = X_T.mult(W).mult(X);
    int numRows = X_T_W_X.numRows();
    int numCols = X_T_W_X.numCols();
    SimpleMatrix X_T_W_X_inv = new SimpleMatrix(numRows, numCols); // since should be initialized
    try {
      // using SimpleMatrix.invert()      
      //X_T_W_X_inv = X_T_W_X.invert();
      //
      // using linear solver - the recommendation of EJML
      LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(numRows, numCols);
      solver = new LinearSolverSafe<DenseMatrix64F>(solver);
      if( !solver.setA(X_T_W_X.getMatrix()) ) {
        throw new Exception("Singular matrix");
      }
      if( solver.quality() <= 1e-8 )
        throw new Exception("Nearly singular matrix");      
      solver.invert(X_T_W_X_inv.getMatrix());
    } 
    catch (Exception e) {
      log.error("LWRPredict: caught exception while inverting ", e);
      return null; 
    }     
    // compute theta
    SimpleMatrix theta = X_T_W_X_inv.mult(X_T).mult(W).mult(y);
    
    SimpleMatrix result = theta.transpose().mult(x_0);
    if (result.numRows() != 1 || result.numCols() != 1){
      log.error("LWR results is not a 1x1 matrix - how come?");
    }
    return result.get(0,0);
    
        
    //    UnivariateFunction expTau = new UnivariateFunction() {      
    //      @Override
    //      public double value(double arg0) {        
    //        //log.info(" cp univariate tau " + tau);
    //        return Math.pow(Math.E, -arg0 / (2*tau));
    //      }
    //    };
    //    ArrayRealVector W = sqDists.map(expTau);
    //    double Xt_W_X = X.dotProduct(W.ebeMultiply(X));
    //    if (Xt_W_X == 0.0) {
    //      log.error(" cp LWR cannot predict - 0 denominator returning NULL");
    //      log.error("Xcv is " + X.toString());
    //      log.error("Ycv is " + y.toString());
    //      log.error("x0 is " + x_0);
    //      return null; // <==== NOTE: a caller must be prepared for it
    //    }
    //    double theta = ( 1.0 / Xt_W_X ) * X.ebeMultiply(W).dotProduct(y) ;  
    //    
    //    return theta * x_0;    
  }


  public SimpleMatrix createXMatrix(Set<Double> xValues, final double min, final double max) {
    // n x 2 matrix, since adding an intercept column
    Double[] dummy1 = new Double[1];  // needed to determine the type of toArray?
    Double[] xVals = xValues.toArray(dummy1);
    // check how many columns we need
    int numCols = 
      normalizeXAndAddIntercept(xVals[0], min, max).getMatrix().getData().length;
    // allocate matrix with this number of columns
    SimpleMatrix X = new SimpleMatrix(xVals.length, numCols);
    for (int i = 0; i < xVals.length; ++i) {
      SimpleMatrix row = normalizeXAndAddIntercept(xVals[i], min, max);
      X.setRow(i, 0, row.getMatrix().getData());
    }
    return X;
  }


  public SimpleMatrix createYVector(Collection<Double> yValues) {
    // couldn't create ArrayRealVector from Integer collection
    // so just manually copying values    
    int numElems = yValues.size();
    double[] doubleArray = new double[numElems];
    int i = 0;       
    for (Iterator<Double> it = yValues.iterator(); it.hasNext(); ++i) {
      doubleArray[i] = it.next();
    }
    SimpleMatrix result = new SimpleMatrix(numElems, 1);
    result.setColumn(0, 0, doubleArray);
    return result;
  }


  /**
   * NOTE: be careful not to call it with max == min 
   */
  private SimpleMatrix normalizeXAndAddIntercept(double xVal, double min, double max) {
    double normVal = ((xVal - min) / (max - min) * SQUEEZE) + OFFSET;
    SimpleMatrix result;
    if ( configuratorFactoryService.isUseIcpt() ) {
      result = new SimpleMatrix(2, 1);
      result.set(0, 0, 1); // intercept
      result.set(1, 0, normVal);
    }
    // if not using intercept    
    else {
      result = new SimpleMatrix(1, 1);
      result.set(0, 0, normVal);
    }
    return result;
  }


  /**
   * @param mat
   * @return
   */
  private SimpleMatrix sumMatrixRows(D1Matrix64F mat) {
    SimpleMatrix result = new SimpleMatrix(mat.getNumRows(), 1);
    for (int i = 0; i < mat.getNumRows(); ++i) {
      double rowSum = 0;
      for (int j = 0; j < mat.getNumCols(); ++j) {
        rowSum += mat.get(i, j);
      }
      result.set(i, rowSum);      
    }
    return result;
  }
}
