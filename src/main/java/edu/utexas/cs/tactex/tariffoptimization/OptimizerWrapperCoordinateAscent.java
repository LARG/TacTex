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

import java.util.Arrays;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.OptimizerWrapper;
import edu.utexas.cs.tactex.interfaces.TariffUtilityEstimate;

public class OptimizerWrapperCoordinateAscent implements OptimizerWrapper {
  
  static private Logger log = Logger.getLogger(OptimizerWrapperCoordinateAscent.class);


  //////////////////////////////////////////////////////////////////////
  //
  // params to tune coordinate-ascent's behavior
  //
  private static final int NUM_STEPS =    2; //4; // 10;    // 20;   // 100;
  private static final double STEP_SIZE = 0.005; // 0.002; // 0.005;// 0.001;
  private static final double UTILITY_CONVERGENCE_TOLERANCE = 10; 
  private static final boolean SHOULD_ITERATE = false; 
  //
  //////////////////////////////////////////////////////////////////////
  
  private int evaluations;


  @Override
  public TreeMap<Double, TariffSpecification> findOptimum(
      TariffUtilityEstimate tariffUtilityEstimate, int NUM_RATES, int numEval) {

    double[] startingVertex = new double[NUM_RATES]; // start from the fixed-rate tariff's offset
    Arrays.fill(startingVertex, 0.0);
    
    evaluations = 0;
    
    double[] previousBestPoint = startingVertex;
    double previousBestValue = tariffUtilityEstimate.value(previousBestPoint);
    BestPointData previousBestPointData = new BestPointData(previousBestPoint, previousBestValue);
    BestPointData bestPointData = new BestPointData(previousBestPoint, previousBestValue);
    boolean go = true;
    while ( go ) {
      for (int i = 0; i < NUM_RATES; ++i) {
        bestPointData = 
            optimizeCoordinate(i, 
                               tariffUtilityEstimate,
                               bestPointData.getBestPoint(),
                               bestPointData.getBestValue());
      }
      if (converged(previousBestPointData, bestPointData) || evaluations > numEval) {
        log.info("converged or passed numEval");
        break;
      } 
      previousBestPointData = bestPointData;
      go = SHOULD_ITERATE;
    }
    
    
    TreeMap<Double, TariffSpecification> eval2TOUTariff = new TreeMap<Double, TariffSpecification>();
    eval2TOUTariff.put(
        bestPointData.getBestValue(), 
        tariffUtilityEstimate.getCorrespondingSpec(bestPointData.getBestPoint()));
    return eval2TOUTariff; 
  }


  private boolean converged(BestPointData previousBestPointData,
      BestPointData bestPointData) {
    return Math.abs(bestPointData.getBestValue() - previousBestPointData.getBestValue()) < UTILITY_CONVERGENCE_TOLERANCE;
  }


  private BestPointData optimizeCoordinate(
    int currentlyOptimizedRate,
    TariffUtilityEstimate tariffUtilityEstimate, 
    double[] previousBestPoint, 
    double previousBestValue) {

    double[] bestPoint = previousBestPoint; 
    double bestValue = previousBestValue;
    for (int i = -NUM_STEPS/2; i <= NUM_STEPS/2; ++i) {
      double[] testPoint = new double[previousBestPoint.length];
      System.arraycopy(previousBestPoint, 0, testPoint, 0, previousBestPoint.length);
      double rateOffset = i * STEP_SIZE;
      testPoint[currentlyOptimizedRate] += rateOffset;
      double value = evaluatePoint(tariffUtilityEstimate, testPoint);
      if ( value > bestValue ) {
        bestValue = value;
        bestPoint = testPoint;
      }
    }
    return new BestPointData(bestPoint, bestValue);
  }


  private double evaluatePoint(TariffUtilityEstimate tariffUtilityEstimate,
      double[] testPoint) {
    double value = tariffUtilityEstimate.value(testPoint);
    evaluations += 1;
    return value;
  }

  
  public class BestPointData {

    private double[] bestPoint;
    private double bestValue;

    public BestPointData(double[] bestPoint, double bestValue) {
      this.bestPoint = bestPoint;
      this.bestValue = bestValue;
    }

    public double[] getBestPoint() {
      return bestPoint;
    }

    public double getBestValue() {
      return bestValue;
    }

  }
  
}
