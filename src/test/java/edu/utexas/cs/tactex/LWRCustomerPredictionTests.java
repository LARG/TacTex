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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.ejml.simple.SimpleMatrix;
import org.junit.Before;
import org.junit.Test;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.subscriptionspredictors.LWRCustNewEjml;
import edu.utexas.cs.tactex.subscriptionspredictors.LWRCustOldAppache;

public class LWRCustomerPredictionTests {

  private ConfiguratorFactoryService configuratorFactoryService;

  private LWRCustNewEjml lwrCustNewEjml;
  private LWRCustOldAppache lwrCustOldAppache;


  @Before
  public void setUp () throws Exception
  {
    
    configuratorFactoryService = mock(ConfiguratorFactoryService.class);
    when(configuratorFactoryService.isUseIcpt()).thenReturn(true);

    lwrCustNewEjml = new LWRCustNewEjml(configuratorFactoryService);
    lwrCustOldAppache = new LWRCustOldAppache();
  }

  /**
   * testing LWR predictions - 
   * based on matlab code for LWR
   *  
   */
  @Test
  public void testLWR () {
    
    // Ejml, with intercept

    SimpleMatrix X;
    SimpleMatrix y;
    SimpleMatrix x0;
    X = new SimpleMatrix(3, 2);
    y = new SimpleMatrix(3, 1);
    x0 = new SimpleMatrix(2, 1); // (with intercept)
    // initialize examples
    X.setColumn(0, 0,
                // X-column 1: (intercept)
                1,
                1,
                1);
    X.setColumn(1, 0, 
                // X-column 2:
               -1, 
                0, 
                1);
    y.setColumn(0, 0, 
                // Y-column 
                3,
                2,
                4);
    x0.set(1); // initialize to 1s (will override non-intercept terms later)
    double tau = 0.5;
    
    // testing based on matlab results
    x0.set(1, 0, -1.5);
    double expected = 3.43855279053977;
    double actual = lwrCustNewEjml.LWRPredict(X, y, x0, tau);
    assertEquals("Matlab based expected value 1", expected, actual, 1e-6);
    
    x0.set(1, 0, -0.5);
    expected = 2.62107459906727;
    actual = lwrCustNewEjml.LWRPredict(X, y, x0, tau);
    assertEquals("Matlab based expected value 2", expected, actual, 1e-6);
    
    x0.set(1, 0, 0);
    expected = 2.63582467285126;
    actual = lwrCustNewEjml.LWRPredict(X, y, x0, tau);
    assertEquals("Matlab based expected value 3", expected, actual, 1e-6);
    
    x0.set(1, 0, 0.5);
    expected = 3.12107459906727;
    actual = lwrCustNewEjml.LWRPredict(X, y, x0, tau);
    assertEquals("Matlab based expected value 4", expected, actual, 1e-6);
    
    x0.set(1, 0, 1.5);
    expected = 4.93855279053977;
    actual = lwrCustNewEjml.LWRPredict(X, y, x0, tau);
    assertEquals("Matlab based expected value 5", expected, actual, 1e-6);
    
    
    // No intercept - should be same pred. between Ejml and Appache
    
    // build Ejml matrix with no intercept
    SimpleMatrix Xejml = X.extractMatrix(0, X.numRows(), 1, X.numCols());
    // build appache matrix with no intercept
    ArrayRealVector Xappache = new ArrayRealVector(Xejml.numRows());
    for (int i = 0; i < Xejml.numRows(); ++i) {
      Xappache.setEntry(i, Xejml.get(i, 0));
    }
    ArrayRealVector yappache = new ArrayRealVector(y.numRows());
    for (int i = 0; i < y.numRows(); ++i) {
      yappache.setEntry(i, y.get(i, 0));
    }
    // initialize x0
    x0 = new SimpleMatrix(1,1);
    double x0val;
    double actualEjml;
    double actualAppache;
    
    x0val = -1.5;
    x0.set(0,0,x0val);
    expected = 4.47403745685534;
    actualEjml = lwrCustNewEjml.LWRPredict(Xejml, y, x0, tau);
    actualAppache = lwrCustOldAppache.LWRPredict(Xappache, yappache, x0val, tau);
    assertEquals("NoIcpt - Matlab based - value 1, Ejml", expected, actualEjml, 1e-6);
    assertEquals("NoIcpt - Matlab based - value 1, Appache", expected, actualAppache, 1e-6);
    
    x0val = -0.5;
    x0.set(0,0,x0val);
    expected = 1.08278977292259;
    actualEjml = lwrCustNewEjml.LWRPredict(Xejml, y, x0, tau);
    actualAppache = lwrCustOldAppache.LWRPredict(Xappache, yappache, x0val, tau);
    assertEquals("NoIcpt - Matlab based - value 2, Ejml", expected, actualEjml, 1e-6);
    assertEquals("NoIcpt - Matlab based - value 2, Appache", expected, actualAppache, 1e-6);
    
    x0val = 0;
    x0.set(0,0,x0val);
    expected = 0;
    actualEjml = lwrCustNewEjml.LWRPredict(Xejml, y, x0, tau);
    actualAppache = lwrCustOldAppache.LWRPredict(Xappache, yappache, x0val, tau);
    assertEquals("NoIcpt - Matlab based - value 3, Ejml", expected, actualEjml, 1e-6);
    assertEquals("NoIcpt - Matlab based - value 3, Appache", expected, actualAppache, 1e-6);
    
    x0val = 0.5;
    x0.set(0,0,x0val);
    expected =1.58278977292259; 
    actualEjml = lwrCustNewEjml.LWRPredict(Xejml, y, x0, tau);
    actualAppache = lwrCustOldAppache.LWRPredict(Xappache, yappache, x0val, tau);
    assertEquals("NoIcpt - Matlab based - value 4, Ejml", expected, actualEjml, 1e-6);
    assertEquals("NoIcpt - Matlab based - value 4, Appache", expected, actualAppache, 1e-6);

    x0val = 1.5;
    x0.set(0,0,x0val);
    expected = 5.97403745685534;
    actualEjml = lwrCustNewEjml.LWRPredict(Xejml, y, x0, tau);
    actualAppache = lwrCustOldAppache.LWRPredict(Xappache, yappache, x0val, tau);
    assertEquals("NoIcpt - Matlab based - value 5, Ejml", expected, actualEjml, 1e-6);
    assertEquals("NoIcpt - Matlab based - value 5, Appache", expected, actualAppache, 1e-6);

  }
  
  @Test
  /*
   * currently tests only for Ejml based customer prediction 
   */
  public void testCreateXMatrixAndYVector() {
    
    boolean origValUseIcpt = configuratorFactoryService.isUseIcpt();

    // first tests with intercept
    when(configuratorFactoryService.isUseIcpt()).thenReturn(true);

    // Test X
    
    Set<Double> xVals = new TreeSet<Double>();
    double min = 1.0;
    double max = 3.0;
    xVals.add(min);
    xVals.add(2.0);
    xVals.add(max);
       
    SimpleMatrix X = lwrCustNewEjml.createXMatrix(xVals, min, max);
    
    // test matrix size
    assertEquals("X.numRows()", 3, X.numRows());
    assertEquals("X.numCols()", 2, X.numCols());

    // first column should be intercept
    double expected = 1;
    double actual = X.get(0,0);
    assertEquals("Matrix(0,0)", expected, actual, 1e-6);

    expected = 1;
    actual = X.get(1,0);
    assertEquals("Matrix(1,0)", expected, actual, 1e-6);
    
    expected = 1;
    actual = X.get(2,0);
    assertEquals("Matrix(2,0)", expected, actual, 1e-6);

    // normalized values with offset and squeeze of 0.1 and 0.8
    expected = 0.1;
    actual = X.get(0,1);
    assertEquals("Matrix(0,1)", expected, actual, 1e-6);

    expected = 0.5;
    actual = X.get(1,1);
    assertEquals("Matrix(1,1)", expected, actual, 1e-6);
    
    expected = 0.9;
    actual = X.get(2,1);
    assertEquals("Matrix(2,1)", expected, actual, 1e-6);
    
    
    
    // Test Y
    
    Set<Double> yVals = new TreeSet<Double>();
    yVals.add(1000.);
    yVals.add(2000.);
    yVals.add(3000.);
    SimpleMatrix Y = lwrCustNewEjml.createYVector(yVals);
    
    // matrix size
    assertEquals("Y.numRows()", 3, Y.numRows());
    assertEquals("Y.numCols()", 1, Y.numCols());
    
    // matrix values
    assertEquals("Y(1)", 1000, Y.get(0,0), 1e-6);
    assertEquals("Y(2)", 2000, Y.get(1,0), 1e-6);
    assertEquals("Y(3)", 3000, Y.get(2,0), 1e-6);
    
    

    // Tests with no intercept
    
    when(configuratorFactoryService.isUseIcpt()).thenReturn(false);
    xVals = new TreeSet<Double>();
    min = 1.0;
    max = 3.0;
    xVals.add(min);
    xVals.add(2.0);
    xVals.add(max);
       
    X = lwrCustNewEjml.createXMatrix(xVals, min, max);
    
    // test matrix size
    assertEquals("X.numRows()", 3, X.numRows());
    assertEquals("X.numCols()", 1, X.numCols()); // <= no intercept this time

    // normalized values with offset and squeeze of 0.1 and 0.8
    expected = 0.1;
    actual = X.get(0,0);
    assertEquals("Matrix(0,0)", expected, actual, 1e-6);

    expected = 0.5;
    actual = X.get(1,0);
    assertEquals("Matrix(1,0)", expected, actual, 1e-6);
    
    expected = 0.9;
    actual = X.get(2,0);
    assertEquals("Matrix(2,0)", expected, actual, 1e-6);



    // restore original value
    when(configuratorFactoryService.isUseIcpt()).thenReturn(origValUseIcpt);

  }
  
  /**
   * currently tests only for Ejml based customer prediction 
   */
  @Test
  public void testCrossValidation () {
    // building a linear data so CV error should be 0 for any tau
    SimpleMatrix X = new SimpleMatrix(3, 2);
    SimpleMatrix y = new SimpleMatrix(3, 1);
    // intercept
    X.set(0,0,1);
    X.set(1,0,1);
    X.set(2,0,1);
    // x values
    X.set(0,1,1);
    X.set(1,1,2);
    X.set(2,1,3);
    // y values
    y.set(0,0,2);
    y.set(1,0,3);
    y.set(2,0,4);
    // any tau should work
    double tau = 0.1;
    double actual = lwrCustNewEjml.CrossValidationError(tau, X, y);
    double expected = 0.0;
    assertEquals("Cross Validation on linear data", expected, actual, 1e-6);    
  }
}
