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
package edu.utexas.cs.tactex.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.ejml.simple.SimpleMatrix;

import edu.utexas.cs.tactex.utils.RegressionUtils.XYForRegression;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.matrix.Matrix;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Standardize;

public class RegressionUtils {

  static private Logger log = Logger.getLogger(RegressionUtils.class);

  /**
   * Note: shouldn't use this function in new code.
   * It is kept for backward compatibility since PolyRegCust uses it. 
   * @param xvals
   * @param maxDegree
   * @return
   */
  public static Instances polyFeatures1D(Double[] xvals, int maxDegree) {

    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    for (int i = 0; i < maxDegree; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Instances X = new Instances("X", attributes, xvals.length);

    for (int i = 0; i < xvals.length; ++i) {
      ArrayList<Double> features = create1DPolyFeatures(xvals[i], maxDegree);
      Instance row = createInstance(features);
      X.add(row);
    }

    return X;
  }


  public static Instances featureNormalize(Instances X, Standardize standartize) {
    Instances nX = null;
    try {

      nX = Filter.useFilter(X, standartize);    

    } catch (Exception e) {
      log.error("featureNormalize: caught exception ", e);
    }
    return nX;
  }


  /**
   * adding y attributes with values
   */
  public static Instances addYforWeka(Instances xInsts, Double[] y) {
    
    Instances xyInsts = addYforWeka(xInsts);
    
    if (y.length != xInsts.numInstances()) {
      log.error("cannot add y to instances since y.length != numInstances");
    }
    
    // initialize all y values
    int n = xInsts.numAttributes() - 1;
    for (int i = 0; i < y.length; ++i) {
      xInsts.get(i).setValue(n, y[i]);
    }
    
    return xyInsts;
  }


  /**
   * adding y attributes without giving it values
   */
  public static Instances addYforWeka(Instances xInsts) {
    
    // add another column for y
    int n = xInsts.numAttributes();
    xInsts.insertAttributeAt(new Attribute(Integer.toString(n)), n);
    
    // last attribute is y value, the class 'label'
    xInsts.setClassIndex(n);
    
    return xInsts;
  }


  public static Double leaveOneOutErrorLinRegLambda(double lambda, Instances data) {

    // MANUAL 
    
    // create a linear regression classifier with Xy_polynorm data
    LinearRegression linreg = createLinearRegression();
    linreg.setRidge(lambda);

    double mse = 0;
    for (int i = 0; i < data.numInstances(); ++i) {
      log.info("fold " + i);
      Instances train = data.trainCV(data.numInstances(), i);
      log.info("train");
      Instances test = data.testCV(data.numInstances(), i);
      log.info("test");
      double actualY = data.instance(i).classValue();
      log.info("actualY");
      try {
        linreg.buildClassifier(train);
        log.info("buildClassifier");
      } catch (Exception e) {
        log.error("failed to build classifier in cross validation", e);
        return null;

      }
      double predictedY = 0;
      try {
        predictedY = linreg.classifyInstance(test.instance(0));
        log.info("predictedY");
      } catch (Exception e) {
        log.error("failed to classify in cross validation", e);
        return null;
      }
      double error = predictedY - actualY;
      log.info("error " + error);
      mse += error * error; 
      log.info("mse " + mse);
    }
    if (data.numInstances() == 0) {
      log.error("no instances in leave-one-out data");
      return null;
    }
    mse /= data.numInstances();
    log.info("mse " + mse);
    return mse;

    //     // USING WEKA 
    // 
    //     // create evaluation object
    //     Evaluation eval = null;
    //     try {
    //       eval = new Evaluation(data);
    //     } catch (Exception e) {
    //       log.error("weka Evaluation() creation threw exception", e);      
    //       //e.printStackTrace();    
    //       return null;
    //     }
    //     
    //     // create a linear regression classifier with Xy_polynorm data
    //     LinearRegression linreg = createLinearRegression();
    //     linreg.setRidge(lambda);
    // //    try {
    // //      linreg.buildClassifier(data);
    // //    } catch (Exception e) {
    // //      log.error("FAILED: linear regression threw exception", e);
    // //      //e.printStackTrace();    
    // //      return null;
    // //    }
    //     
    //     // initialize the evaluation object
    //     Classifier classifier = linreg;
    //     int numFolds = data.numInstances();
    //     Random random = new Random(0);
    //     try {
    //       eval.crossValidateModel(classifier , data , numFolds , random);
    //     } catch (Exception e) {
    //       log.error("crossvalidation threw exception", e);
    //       //e.printStackTrace();    
    //       return null;
    //     }
    //     
    //     double mse = eval.errorRate();
    //     return mse;
  }


  public static ArrayList<Double> create1DPolyFeatures(double x, int maxDegree) {
    ArrayList<Double> result = new ArrayList<Double>();
    double f_i = x;
    for (int i = 0; i < maxDegree; ++i) {
      result.add(f_i);
      f_i *= x;
    }
    //System.out.println(result.toString());
    return result;
  }


  public static ArrayList<Double> createRawFeatures(double[] x) {
    ArrayList<Double> result = new ArrayList<Double>();
    for (double d : x) {
      result.add(d);
    }
    return result;
  }


  public static Instance createInstance(ArrayList<Double> features) {
    Instance result = new DenseInstance(features.size());
    int i = 0;
    for (Double feature : features) {
      result.setValue(i++, feature);
    }
    return result;
  }


  public static Instances createInstances(ArrayList<Instance> instArr,
      ArrayList<String> attrNames) {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    for (String attr : attrNames) {
      // attribute name is the polynomial power
      attributes.add(new Attribute(attr));
    }    
    Instances X = new Instances("X", attributes, instArr.size());
    for (Instance inst : instArr) {
      X.add(inst);
    }
    return X;
  }


  public static LinearRegression createLinearRegression() {
    LinearRegression linreg = new LinearRegression();
    linreg.setAttributeSelectionMethod(new SelectedTag(LinearRegression.SELECTION_NONE, LinearRegression.TAGS_SELECTION));
    linreg.setEliminateColinearAttributes(false);
    // if wants debug info
    //linreg.setDebug(true);
    return linreg;
  }
  

  public static WekaLinRegData createWekaLinRegData(int timeslot,
      Instances X, Double[] yvals, ArrayList<Double> candidateLambdas) throws Exception {
    WekaLinRegData result;

    // normalize
    Standardize standardize = new Standardize();
    try {
      standardize.setInputFormat(X);
    } catch (Exception e) {
      log.error("PolyRegCust.predictNumSubs() data standardizing exception", e);
      throw e;
    }
    Instances nrmFeatures = RegressionUtils.featureNormalize(X, standardize);
    log.info("normalized features " + nrmFeatures);

    // add y to X since this is what weka expects
    Instances Xy = RegressionUtils.addYforWeka(nrmFeatures, yvals);

    // run cross validation for lambda
    Double bestLambda = findBestRegularizationParameter(Xy, candidateLambdas);
    if (null == bestLambda) {
      String message = "best regularization parameter is null, cannot predict";
      log.error(message);
      throw new Exception(message);
    }

    // run linear regression
    LinearRegression linearRegression = RegressionUtils.createLinearRegression();
    linearRegression.setRidge(bestLambda);
    try {
      linearRegression.buildClassifier(Xy);
      log.info("theta " + Arrays.toString(linearRegression.coefficients()));
    } catch (Exception e) {
      log.error("PolyRegCust.predictNumSubs() buildClassifier exception", e);
      throw e;
    }

    result = new WekaLinRegData(standardize, linearRegression, timeslot);
    return result;
  }


  public static Instances createXInstances(XYForRegression xy) {
    // build attributes
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    for (String attrName : xy.getAttrNames() ) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(attrName); 
      attributes.add(xattr);
    }    

    double[][] Xarr = xy.getX();
    int numInstances = Xarr.length;
    int numAttr = Xarr[0].length;
    Instances Xinst = new Instances("X", attributes, numInstances);

    // fill instances 
    for (int i = 0; i < numInstances; ++i) {
      Instance row = new DenseInstance(numAttr);
      for (int j = 0; j < numAttr; ++j) {
        row.setValue(j, Xarr[i][j]);
      }
      Xinst.add(row);
    }
    return Xinst; 
  }


  public static Double findBestRegularizationParameter(Instances Xy, ArrayList<Double> candidateLambdas) {
    log.info("added lambdas");
    double bestLambda = Double.MAX_VALUE;
    double bestMSE = Double.MAX_VALUE;
    for (Double lambda : candidateLambdas) {
      Double mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, Xy);
      log.info("mse(" + lambda + ")=" + mse);
      if (null == mse) {
        log.error(" poly cross-validation failed, return null");
        return null;
      }
      if (mse < bestMSE) {
        bestMSE = mse;
        bestLambda = lambda;
      }
    }
    log.info("bestLambda is " + bestLambda);
    return bestLambda;
  }


  public static class WekaLinRegData {
  
    private Standardize standardize;
    private LinearRegression linearRegression;
    private int timeslot;
    
    public WekaLinRegData(Standardize standardize, LinearRegression linearRegression, int timeslot) {
      this.standardize = standardize;      
      this.linearRegression = linearRegression;
      this.timeslot = timeslot;      
    }
  
    public int getTimeslot() {
      return timeslot;
    }
  
    public LinearRegression getLinearRegression() {
      return linearRegression;
    }
  
    public Standardize getStandardize() {
      return standardize;
    }
  
  } 
  

  public static class WekaXYLambdas {
  
    private Instances X;
    private Double[] Y;
    private ArrayList<Double> candidateLambdas;
  
    public WekaXYLambdas(Instances X, Double[] Y,
        ArrayList<Double> candidateLambdas) {
      this.X = X;
      this.Y = Y;
      this.candidateLambdas = candidateLambdas;
    }
  
    public Instances getX() {
      return X;
    }
  
    public Double[] getY() {
      return Y;
    }
  
    public ArrayList<Double> getLambdas() {
      return candidateLambdas;
    }
  }


  public static class XYForRegression {
    
    private double[][] X;
    private Double[] Y;
    ArrayList<String> attrNames;
    
    public XYForRegression(double[][] X, Double[] Y, ArrayList<String> attrNames) {
      this.X = X;
      this.Y = Y;
      this.attrNames = attrNames;
    }
  
    public double[][] getX() {
      return X;
    }
  
    public Double[] getY() {
      return Y;
    }
    
    ArrayList<String> getAttrNames() {
      return attrNames;
    }
  }
}
