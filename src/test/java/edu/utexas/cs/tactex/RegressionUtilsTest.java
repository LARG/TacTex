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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ejml.simple.SimpleMatrix;
import org.junit.Before;
import org.junit.Test;

import edu.utexas.cs.tactex.utils.RegressionUtils;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.Standardize;


public class RegressionUtilsTest {
  
  Double[] xVals = new Double[] {-2.0, 
                                 -1.0,
                                 -0.5,
                                  0.0,
                                  0.5,
                                  1.0,
                                  2.0};
  // raw matlab data
  Double[] X;
  double[] y;
  double[][] X_poly;
  double[][] X_polynorm;

  // matlab data converted to Instances
  Instances Xy;
  Instances Xy_poly;
  Instances Xy_polynorm;

  @Before
  public void setUp () throws Exception
  {
    
    // Using matlab based example (ex5)
    //  X =
    //    -15.9368
    //    -29.1530
    //     36.1895
    //     37.4922
    //    -48.0588
    //     -8.9415
    //     15.3078
    //    -34.7063
    //      1.3892
    //    -44.3838
    //      7.0135
    //     22.7627
    //
    //  y =
    //      2.1343
    //      1.1733
    //     34.3591
    //     36.8380
    //      2.8090
    //      2.1211
    //     14.7103
    //      2.6142
    //      3.7402
    //      3.7317
    //      7.6277
    //     22.7524
    //
    //  poly features 
    //  X_poly =
    //    -1.5937e+01   2.5398e+02  -4.0476e+03   6.4506e+04  -1.0280e+06   1.6383e+07  -2.6110e+08   4.1610e+09
    //    -2.9153e+01   8.4990e+02  -2.4777e+04   7.2232e+05  -2.1058e+07   6.1390e+08  -1.7897e+10   5.2175e+11
    //     3.6190e+01   1.3097e+03   4.7397e+04   1.7153e+06   6.2075e+07   2.2465e+09   8.1298e+10   2.9422e+12
    //     3.7492e+01   1.4057e+03   5.2701e+04   1.9759e+06   7.4080e+07   2.7774e+09   1.0413e+11   3.9041e+12
    //    -4.8059e+01   2.3097e+03  -1.1100e+05   5.3345e+06  -2.5637e+08   1.2321e+10  -5.9212e+11   2.8457e+13
    //    -8.9415e+00   7.9950e+01  -7.1487e+02   6.3919e+03  -5.7153e+04   5.1103e+05  -4.5694e+06   4.0857e+07
    //     1.5308e+01   2.3433e+02   3.5871e+03   5.4910e+04   8.4055e+05   1.2867e+07   1.9696e+08   3.0151e+09
    //    -3.4706e+01   1.2045e+03  -4.1805e+04   1.4509e+06  -5.0355e+07   1.7476e+09  -6.0653e+10   2.1051e+12
    //     1.3892e+00   1.9297e+00   2.6807e+00   3.7239e+00   5.1731e+00   7.1863e+00   9.9828e+00   1.3868e+01
    //    -4.4384e+01   1.9699e+03  -8.7432e+04   3.8806e+06  -1.7223e+08   7.6444e+09  -3.3929e+11   1.5059e+13
    //     7.0135e+00   4.9189e+01   3.4499e+02   2.4196e+03   1.6970e+04   1.1902e+05   8.3473e+05   5.8544e+06
    //     2.2763e+01   5.1814e+02   1.1794e+04   2.6847e+05   6.1112e+06   1.3911e+08   3.1665e+09   7.2077e+10
    //
    //  poly features normalized 
    //  X_polynorm =
    //    -0.3621408  -0.7550867   0.1822259  -0.7061899   0.3066179  -0.5908777   0.3445158  -0.5084812
    //    -0.8032048   0.0012583  -0.2479370  -0.3270234   0.0933963  -0.4358176   0.2554161  -0.4489125
    //     1.3774670   0.5848267   1.2497686   0.2453120   0.9783597  -0.0121557   0.7565685  -0.1703521
    //     1.4209399   0.7066468   1.3598456   0.3955340   1.1061618   0.1256371   0.8719291  -0.0596377
    //    -1.4341485   1.8539998  -2.0371631   2.3314313  -2.4115363   2.6022120  -2.6456745   2.7660853
    //    -0.1286871  -0.9759688   0.2513851  -0.7396869   0.3169529  -0.5949966   0.3458118  -0.5089553
    //     0.6805816  -0.7800290   0.3406557  -0.7117211   0.3265091  -0.5917902   0.3468300  -0.5086130
    //    -0.9885343   0.4513580  -0.6012819   0.0929171  -0.2184729  -0.1416085   0.0394035  -0.2666927
    //     0.2160758  -1.0749928   0.2662752  -0.7433690   0.3175614  -0.5951292   0.3458349  -0.5089601
    //    -1.3115007   1.4228060  -1.5481209   1.4933962  -1.5159077   1.3886548  -1.3683066   1.2241435
    //     0.4037767  -1.0150104   0.2733785  -0.7419765   0.3177420  -0.5950984   0.3458391  -0.5089594
    //     0.9293753  -0.4198079   0.5109684  -0.5886238   0.3826157  -0.5590300   0.3618324  -0.5006648

    X = new Double[] {-15.9368,
                      -29.1530,
                       36.1895,
                       37.4922,
                      -48.0588,
                       -8.9415,
                       15.3078,
                      -34.7063,
                        1.3892,
                      -44.3838,
                        7.0135,
                       22.7627
                     };
    
    y = new double[] {  2.1343,
                        1.1733,
                       34.3591,
                       36.8380,
                        2.8090,
                        2.1211,
                       14.7103,
                        2.6142,
                        3.7402,
                        3.7317,
                        7.6277,
                       22.7524
                     };

    X_poly = new double[][] {
        {-1.5937e+01, 2.5398e+02, -4.0476e+03,  6.4506e+04, -1.0280e+06,  1.6383e+07, -2.6110e+08, 4.1610e+09},
        {-2.9153e+01, 8.4990e+02, -2.4777e+04,  7.2232e+05, -2.1058e+07,  6.1390e+08, -1.7897e+10, 5.2175e+11},
        { 3.6190e+01, 1.3097e+03,  4.7397e+04,  1.7153e+06,  6.2075e+07,  2.2465e+09,  8.1298e+10, 2.9422e+12},
        { 3.7492e+01, 1.4057e+03,  5.2701e+04,  1.9759e+06,  7.4080e+07,  2.7774e+09,  1.0413e+11, 3.9041e+12},
        {-4.8059e+01, 2.3097e+03, -1.1100e+05,  5.3345e+06, -2.5637e+08,  1.2321e+10, -5.9212e+11, 2.8457e+13},
        {-8.9415e+00, 7.9950e+01, -7.1487e+02,  6.3919e+03, -5.7153e+04,  5.1103e+05, -4.5694e+06, 4.0857e+07},
        { 1.5308e+01, 2.3433e+02,  3.5871e+03,  5.4910e+04,  8.4055e+05,  1.2867e+07,  1.9696e+08, 3.0151e+09},
        {-3.4706e+01, 1.2045e+03, -4.1805e+04,  1.4509e+06, -5.0355e+07,  1.7476e+09, -6.0653e+10, 2.1051e+12},
        { 1.3892e+00, 1.9297e+00,  2.6807e+00,  3.7239e+00,  5.1731e+00,  7.1863e+00,  9.9828e+00, 1.3868e+01},
        {-4.4384e+01, 1.9699e+03, -8.7432e+04,  3.8806e+06, -1.7223e+08,  7.6444e+09, -3.3929e+11, 1.5059e+13},
        { 7.0135e+00, 4.9189e+01,  3.4499e+02,  2.4196e+03,  1.6970e+04,  1.1902e+05,  8.3473e+05, 5.8544e+06},
        { 2.2763e+01, 5.1814e+02,  1.1794e+04,  2.6847e+05,  6.1112e+06,  1.3911e+08,  3.1665e+09, 7.2077e+10}};

    X_polynorm = new double[][] {
      {-0.3621408, -0.7550867,  0.1822259, -0.7061899,  0.3066179, -0.5908777,  0.3445158, -0.5084812},
      {-0.8032048,  0.0012583, -0.2479370, -0.3270234,  0.0933963, -0.4358176,  0.2554161, -0.4489125},
      { 1.3774670,  0.5848267,  1.2497686,  0.2453120,  0.9783597, -0.0121557,  0.7565685, -0.1703521},
      { 1.4209399,  0.7066468,  1.3598456,  0.3955340,  1.1061618,  0.1256371,  0.8719291, -0.0596377},
      {-1.4341485,  1.8539998, -2.0371631,  2.3314313, -2.4115363,  2.6022120, -2.6456745,  2.7660853},
      {-0.1286871, -0.9759688,  0.2513851, -0.7396869,  0.3169529, -0.5949966,  0.3458118, -0.5089553},
      { 0.6805816, -0.7800290,  0.3406557, -0.7117211,  0.3265091, -0.5917902,  0.3468300, -0.5086130},
      {-0.9885343,  0.4513580, -0.6012819,  0.0929171, -0.2184729, -0.1416085,  0.0394035, -0.2666927},
      { 0.2160758, -1.0749928,  0.2662752, -0.7433690,  0.3175614, -0.5951292,  0.3458349, -0.5089601},
      {-1.3115007,  1.4228060, -1.5481209,  1.4933962, -1.5159077,  1.3886548, -1.3683066,  1.2241435},
      { 0.4037767, -1.0150104,  0.2733785, -0.7419765,  0.3177420, -0.5950984,  0.3458391, -0.5089594},
      { 0.9293753, -0.4198079,  0.5109684, -0.5886238,  0.3826157, -0.5590300,  0.3618324, -0.5006648}};



    
    init_Xy();
    init_Xy_poly();
    init_Xy_polynorm();
    
      
  }

  /**
   * polynomial feature creation for 1d regression
   */
  @Test
  public void testPolyFeatures1D () {
    
    // First test: hand made test
    //
    // call poly features
    int maxDegree = 8;
    Instances XinstSmall = RegressionUtils.polyFeatures1D(xVals, maxDegree);
    
    // a few random tests that we have 8 degree features
    // remember that here we are not supposed to have intercept
    int rows = XinstSmall.numInstances();
    assertEquals("XinstSmall.rows", 7, rows); // 7 training examples
    int cols = XinstSmall.numAttributes();
    assertEquals("XinstSmall.cols", maxDegree, cols); // degree of poly

    int i, j;
    // first row
    i = 0;
    j = 0;
    double X_00 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(0,0)", Math.pow(-2, j+1), X_00, 1e-6);
    i = 0;
    j = 1;
    double X_01 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(0,1)", Math.pow(-2, j+1), X_01, 1e-6);
    i = 0;
    j = 7;
    double X_07 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(0,7)", Math.pow(-2, j+1), X_07, 1e-6);
    // other rows
    i = 3;
    j = 0;
    double X_30 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(3,0)", Math.pow(0.0, j+1), X_30, 1e-6);
    i = 3;
    j = 7;
    double X_37 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(3,7)", Math.pow(0.0, j+1), X_37, 1e-6);
    i = 6;
    j = 0;
    double X_60 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(6,0)", Math.pow(2.0, j+1), X_60, 1e-6);
    i = 6;
    j = 7;
    double X_67 = XinstSmall.get(i).value(j);
    assertEquals("XinstSmall polyFeatures(6,7)", Math.pow(2.0, j+1), X_67, 1e-6);


    // Second test: matlab-based polynomial features
    
    //maxDegree = 8;
    //Instances XinstMatlab = RegressionUtils.polyFeatures1D(X, maxDegree);
    //assertEquals("XinstMatlab.numrows", X.length, XinstMatlab.numInstances());
    //assertEquals("XinstMatlab.numcols", maxDegree, XinstMatlab.numAttributes());
    //int numrows = X.length;
    //int numcols = maxDegree;
    //for (i = 0; i < numrows; ++i) {
    //  for (j = 0; j < numcols; ++j) {
    //    assertEquals("matlab-based poly-features, features["+i+"]["+j+"]", X_poly[i][j], XinstMatlab.instance(i).value(j), 1e-4);
    //  }
    //}
  } 

  /**
   * Feature normalization using mean and std.
   * Based on Matlab implementation
   */
  @Test
  public void testFeatureNormalize() {

    // First test: hand made test
    //
    // X = 
    //  1  -1
    //  3  -2
    //  5  -3
    //
    // should be normalized to:
    //
    // ans =
    // -1   1
    //  0   0
    //  1  -1
    
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    int numRows = 3;
    int numCols = 2;
    for (int i = 0; i < numCols; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Instances Xtrain = new Instances("Xtrain", attributes, numRows); 

    Instance row;
    row = new  DenseInstance(numCols);
    row.setValue(0,  1);
    row.setValue(1, -1);
    Xtrain.add(row);
    row = new  DenseInstance(numCols);
    row.setValue(0,  3);
    row.setValue(1, -2);
    Xtrain.add(row);
    row = new  DenseInstance(numCols);
    row.setValue(0,  5);
    row.setValue(1, -3);
    Xtrain.add(row);
   
    // initialize normalization filter
    Standardize standardize = new Standardize();
    try {
      standardize.setInputFormat(Xtrain);
    } catch (Exception e) {
      e.printStackTrace();
    }
		
    Instances Ntrain = RegressionUtils.featureNormalize(Xtrain, standardize);
    assertEquals("Normalized(0,0)", -1, Ntrain.get(0).value(0), 1e-6);
    assertEquals("Normalized(0,1)",  1, Ntrain.get(0).value(1), 1e-6);
    assertEquals("Normalized(1,0)",  0, Ntrain.get(1).value(0), 1e-6);
    assertEquals("Normalized(1,1)",  0, Ntrain.get(1).value(1), 1e-6);
    assertEquals("Normalized(2,0)",  1, Ntrain.get(2).value(0), 1e-6);
    assertEquals("Normalized(2,1)", -1, Ntrain.get(2).value(1), 1e-6);

    // normalize test set
    numRows = 2;// two test instances
    Instances Xtest = new Instances("Xtest", attributes, numRows); 
    // continuing the sequence from upper end
    row = new  DenseInstance(numCols);
    row.setValue(0,  -1);
    row.setValue(1,  0);
    Xtest.add(row);
    // continuing the sequence from lower end
    row = new  DenseInstance(numCols);
    row.setValue(0,  7);
    row.setValue(1, -4);
    Xtest.add(row);
    Instances Ntest = RegressionUtils.featureNormalize(Xtest, standardize);
    assertEquals("Ntest(0,0)", -2, Ntest.get(0).value(0), 1e-6);
    assertEquals("Ntest(0,1)",  2, Ntest.get(0).value(1), 1e-6);
    assertEquals("Ntest(1,0)",  2, Ntest.get(1).value(0), 1e-6);
    assertEquals("Ntest(1,1)", -2, Ntest.get(1).value(1), 1e-6);

    // Second test: matlab based 
    standardize = new Standardize();
    try {
      standardize.setInputFormat(Xy_poly);
    } catch (Exception e) {
      e.printStackTrace();
    }
    Instances N = RegressionUtils.featureNormalize(Xy_poly, standardize);
    for (int i = 0; i < X_polynorm.length; ++i) {
      for (int j = 0; j < X_polynorm[0].length; ++j) {
        assertEquals("matlab-based normalization, N["+i+"]["+j+"]", X_polynorm[i][j], N.instance(i).value(j), 1e-4);
      }
    }
    
  }
  
  /**
   * test inserting y to Instances
   */
  @Test
  public void testInsertY() {
    
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    int numRows = 3;
    int numCols = 1; // additional column for y
    for (int i = 0; i < numCols; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Instances xInsts = new Instances("xInsts", attributes, numRows);
    for (int i = 0; i < numRows; ++i) {
      Instance row = new DenseInstance(numCols);
      row.setValue(0, 2 * i);
      xInsts.add(row);
    }

    Double[] y = new Double[] {1.0, 10.0, 100.0}; // must be the length of numRows
    Instances xyInsts = RegressionUtils.addYforWeka(xInsts, y);
    // test matrix properties
    assertEquals("addYforWeka(): numrows", 3, xyInsts.numInstances());
    assertEquals("addYforWeka(): numcols", 2, xyInsts.numAttributes());
    assertEquals("addYforWeka(): class-attribute", 1, xyInsts.classIndex());
    // test actual values
    assertEquals("xyInsts["+0+"]["+0+"]", 2 * 0, xyInsts.instance(0).value(0), 1e-6);
    assertEquals("xyInsts["+1+"]["+0+"]", 2 * 1, xyInsts.instance(1).value(0), 1e-6);
    assertEquals("xyInsts["+2+"]["+0+"]", 2 * 2, xyInsts.instance(2).value(0), 1e-6);
    assertEquals("xyInsts["+0+"]["+1+"]", 1, xyInsts.instance(0).value(1), 1e-6);
    assertEquals("xyInsts["+1+"]["+1+"]", 10, xyInsts.instance(1).value(1), 1e-6);
    assertEquals("xyInsts["+2+"]["+1+"]", 100, xyInsts.instance(2).value(1), 1e-6);

  }

  /**
   * test linear regression works as expected (matlab implementation).
   */
  @Test
  public void testLinearRegression() {

    // test on Xy
    LinearRegression linreg = RegressionUtils.createLinearRegression();
    linreg.setRidge(0);
    try {
      linreg.buildClassifier(Xy);
    } catch (Exception e) {
      e.printStackTrace();
      assertTrue("FAILED: linear regression threw exception", false);
    } 
    double[] theta = linreg.coefficients(); 
    assertEquals("Xy, theta.length", 3, theta.length);
    assertEquals("Xy, theta[0] 1st attribute", 0.36778, theta[0], 1e-5);
    assertEquals("Xy, theta[1] 2nd attribute (y)", 0.0, theta[1], 1e-6);
    assertEquals("Xy, theta[2] intercept", 13.0879, theta[2], 1e-4);
    

    // test on Xy_polynorm, lambda = 0
    linreg = RegressionUtils.createLinearRegression();
    linreg.setRidge(0);
    try {
      linreg.buildClassifier(Xy_polynorm);
    } catch (Exception e) {
      e.printStackTrace();
      assertTrue("FAILED: linear regression threw exception", false);
    }
    double[] theta_polynorm = linreg.coefficients(); 
    assertEquals("theta.length", 8 + 1 + 1, theta_polynorm.length); // features + y + icpt
    assertEquals("Xy_polynorm, theta[0] attribute",  9.5373, theta_polynorm[0], 1e-4);
    assertEquals("Xy_polynorm, theta[1] attribute",  18.9855, theta_polynorm[1], 1e-4);
    //assertEquals("Xy_polynorm, theta[2] attribute",  30.2166, theta_polynorm[2], 1e-4); // matlab normal eq
    assertEquals("Xy_polynorm, theta[2] attribute",  30.2161, theta_polynorm[2], 1e-4);   // weka
    assertEquals("Xy_polynorm, theta[3] attribute", -28.4494, theta_polynorm[3], 1e-4);
    //assertEquals("Xy_polynorm, theta[4] attribute", -77.7822, theta_polynorm[4], 1e-4); // matlab normal eq
    assertEquals("Xy_polynorm, theta[4] attribute", -77.7818, theta_polynorm[4], 1e-4);   // weka
    assertEquals("Xy_polynorm, theta[5] attribute",  7.0729, theta_polynorm[5], 1e-4);
    //assertEquals("Xy_polynorm, theta[6] attribute",  63.3849, theta_polynorm[6], 1e-4); // matlab normal eq
    assertEquals("Xy_polynorm, theta[6] attribute",  63.3852, theta_polynorm[6], 1e-4);   // weka
    //assertEquals("Xy_polynorm, theta[7] attribute",  21.5561, theta_polynorm[7], 1e-4); // matlab normal eq
    assertEquals("Xy_polynorm, theta[7] attribute",  21.5565, theta_polynorm[7], 1e-4);   // weka
    assertEquals("Xy_polynorm, theta[8] y attribute", 0.0, theta_polynorm[8], 1e-4);
    assertEquals("Xy_polynorm, theta[9] intercept", 11.2176, theta_polynorm[9], 1e-4);
  

   
    // test on Xy_polynorm, lambda = 100

    linreg = RegressionUtils.createLinearRegression();
    linreg.setRidge(100);
    try {
      linreg.buildClassifier(Xy_polynorm);
    } catch (Exception e) {
      e.printStackTrace();
      assertTrue("FAILED: linear regression threw exception", false);
    }
    double[] theta_polynorm100 = linreg.coefficients(); 
    assertEquals("theta.length", 8 + 1 + 1, theta_polynorm100.length); // features + y + icpt
    assertEquals("Xy_polynorm, lambda=100, theta[0] attribute", 0.964209, theta_polynorm100[0], 1e-4);
    assertEquals("Xy_polynorm, lambda=100, theta[1] attribute", 0.322966, theta_polynorm100[1], 1e-4);
    assertEquals("Xy_polynorm, lambda=100, theta[2] attribute",  0.747428, theta_polynorm100[2], 1e-4);
    assertEquals("Xy_polynor, lambda=100m, theta[3] attribute", 0.144230, theta_polynorm100[3], 1e-4);
    assertEquals("Xy_polynor, lambda=100m, theta[4] attribute", 0.555808, theta_polynorm100[4], 1e-4); 
    assertEquals("Xy_polynor, lambda=100m, theta[5] attribute", 0.020283, theta_polynorm100[5], 1e-4);
    assertEquals("Xy_polynor, lambda=100m, theta[6] attribute", 0.418693, theta_polynorm100[6], 1e-4);
    assertEquals("Xy_polynor, lambda=100m, theta[7] attribute", -0.058004, theta_polynorm100[7], 1e-4);
    assertEquals("Xy_polynorm, lambda=100, theta[8] y attribute", 0.0, theta_polynorm100[8], 1e-4);
    assertEquals("Xy_polynorm, lambda=100, theta[9] intercept", 11.217589, theta_polynorm100[9], 1e-4); 

  }

  /**
   * test cross validation works as expected (based on LWR's?)
   */
  @Test
  public void testLeaveOneOutLinRegLambda() {
    double lambda; 
    double mse;
  
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    int numRows = 3;
    int numCols = 1 + 1; // additional column for y
    for (int i = 0; i < numCols; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Instances triangle = new Instances("triangle", attributes, numRows);
    triangle.setClassIndex(triangle.numAttributes() - 1);// last column is y

    Instance row;
    final int yInd = numCols - 1;
    row = new  DenseInstance(numCols);
    // the point (0,1)
    row.setValue(0, 0);
    row.setValue(yInd, 1);
    triangle.add(row);
    // the point (sqrt(2)/2, 1+ sqrt(2)/2)
    row.setValue(0, 0.7071067811865476);
    row.setValue(yInd, 1.7071067811865476);
    triangle.add(row);
    // the point (sqrt(2),1) 
    row.setValue(0, 1.4142135623730951);
    row.setValue(yInd, 1);
    triangle.add(row);
    lambda = 0;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, triangle);
    double expected = (2 + 0.5 + 2) / 3; // (sqrt(2)^2 + (sqrt(2)/2)^2 + sqrt(2)^2) / 3
    assertEquals("mse triangle " + lambda, expected, mse, 1e-6);

    // NOTE: the below is not really a test. The expected values
    // are just those I first got after running the cross validation
    // function, so really this is just a backwards compatibility check.
    Instances data = Xy_polynorm;
    lambda = 0;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 5.054484932007508, mse, 1e-6);
    lambda = 0.0001;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 2.8454327353554603, mse, 1e-6);
    lambda = 0.001;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 1.9697811584040954, mse, 1e-6);
    lambda = 0.01;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 2.969852269097448, mse, 1e-6);
    lambda = 0.1;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 2.7707247324198208, mse, 1e-6);
    lambda = 1;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 7.653858540922362, mse, 1e-6);
    lambda = 10;
    mse = RegressionUtils.leaveOneOutErrorLinRegLambda(lambda, data);
    assertEquals("mse lambda = " + lambda, 53.61040102608146, mse, 1e-6);

  }

  // ------------- initialization methods ---------------
  private void init_Xy() {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    int numRows = 12;
    int numCols = 1 + 1; // additional column for y
    for (int i = 0; i < numCols; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Xy = new Instances("Xy", attributes, numRows);
    Xy.setClassIndex(Xy.numAttributes() - 1);// last column is y
  
    Instance row;
    final int yInd = numCols - 1;
    for (int i = 0; i < X_poly.length; ++i) {
      row = new  DenseInstance(numCols);
      for (int j = 0; j < yInd; ++j) {
        row.setValue(j, X_poly[i][j]);
      }
      row.setValue(yInd, y[i]);
      Xy.add(row);
    }

  }

  private void init_Xy_poly() {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    int numRows = 12;
    int polyDegree = 8;
    int numCols = polyDegree + 1; // additional column for y
    for (int i = 0; i < numCols; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Xy_poly = new Instances("Xy_poly", attributes, numRows);
    Xy_poly.setClassIndex(Xy_poly.numAttributes() - 1);// last column is y
  
    Instance row;
    final int yInd = numCols - 1;
    for (int i = 0; i < X_poly.length; ++i) {
      row = new  DenseInstance(numCols);
      for (int j = 0; j < yInd; ++j) {
        row.setValue(j, X_poly[i][j]);
      }
      row.setValue(yInd, y[i]);
      Xy_poly.add(row);
    }
  } 

  private void init_Xy_polynorm() {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    int numRows = 12;
    int polyDegree = 8;
    int numCols = polyDegree + 1; // additional column for y
    for (int i = 0; i < numCols; ++i) {
      // attribute name is the polynomial power
      Attribute xattr = new Attribute(Integer.toString(i)); 
      attributes.add(xattr);
    }    
    Xy_polynorm = new Instances("Xy_polynorm", attributes, numRows);
    Xy_polynorm.setClassIndex(Xy_polynorm.numAttributes() - 1);// last column is y
  
    Instance row;
    final int yInd = numCols - 1;
    for (int i = 0; i < X_polynorm.length; ++i) {
      row = new  DenseInstance(numCols);
      for (int j = 0; j < yInd; ++j) {
        row.setValue(j, X_polynorm[i][j]);
      }
      row.setValue(yInd, y[i]);
      Xy_polynorm.add(row);
    }
  } 

}

