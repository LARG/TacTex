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
package edu.utexas.cs.tactex.tariffoptimization;

import java.util.Arrays;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.OptimizerWrapper;
import edu.utexas.cs.tactex.interfaces.TariffUtilityEstimate;

/**
 * @author urieli
 *
 */
public class OptimizerWrapperGradientAscent implements OptimizerWrapper {

  static private Logger log = Logger.getLogger(OptimizerWrapperGradientAscent.class);

  //////////////////////////////////////////////////////////////////////
  //
  // params to tune gradient-ascent's behavior
  //
  //private static final int NUM_RATES =    2; //4; // 10;    // 20;   // 100;
  private static final double REFERENCE_STEP_SIZE = 0.005;
  private static final double REFERENCE_RATE = -0.100;
  private static        double STEP_SIZE = REFERENCE_STEP_SIZE; // 0.100; //0.005; // 0.160; // 0.080; // 0.040; // 0.020; // 0.005; // 0.002; // 0.005;// 0.001;
  private static final double UTILITY_CONVERGENCE_TOLERANCE = 0; // 100; 
  // total bound on evaluations to limit computation time
  private static final double MAX_EVALUATIONS = 70; 
  //
  //////////////////////////////////////////////////////////////////////
  
  private int evaluations;

  @Override
  public TreeMap<Double, TariffSpecification> findOptimum(
      TariffUtilityEstimate tariffUtilityEstimate, int NUM_RATES, int numEval) {

    double[] startingVertex = new double[NUM_RATES]; // start from the fixed-rate tariff's offset
    Arrays.fill(startingVertex, 0.0);
    
    // temporary solution - getting fixed rate tariff to determine step size and scaling STEP_SIZE proportionally
    TariffSpecification bestFixedRateSpec = tariffUtilityEstimate.getCorrespondingSpec(startingVertex);
    double bestFixedRate = bestFixedRateSpec.getRates().get(0).getValue();
    double rateRatio = bestFixedRate / REFERENCE_RATE;
    // Note: using rateRatio has not been good enough, so trying powers of it (remember it's > 1)
    //STEP_SIZE = Math.max(REFERENCE_STEP_SIZE, (rateRatio * rateRatio) * REFERENCE_STEP_SIZE);
    STEP_SIZE = Math.max(REFERENCE_STEP_SIZE, Math.abs(rateRatio) * REFERENCE_STEP_SIZE);
    log.debug("STEP_SIZE = " + STEP_SIZE + " REFERENCE_STEP_SIZE=" + REFERENCE_STEP_SIZE + " bestFixedRate=" + bestFixedRate + " REFERENCE_RATE=" + REFERENCE_RATE); 
    
    evaluations = 0;
    TreeMap<Double, TariffSpecification> eval2TOUTariff = new TreeMap<Double, TariffSpecification>();

    // OUTER LOOP
    //for( STEP_SIZE = 0.005; STEP_SIZE < 0.100; STEP_SIZE += 0.005) {
    //  log.info("STARTING A LOOP: STEP_SIZE=" + STEP_SIZE);
    
    // first compute numerical gradient
    RealVector gradient = new ArrayRealVector(NUM_RATES);
    for (int i = 0; i < NUM_RATES; ++i) {
      gradient.setEntry(i, computePartialDerivative(i, tariffUtilityEstimate, NUM_RATES, eval2TOUTariff));
    }
    gradient = gradient.unitVector();
    
    // taking steps in the gradient direction
    double previousPointValue = -Double.MAX_VALUE;
    final double alpha = STEP_SIZE;
    RealVector rateOffsets = new ArrayRealVector(NUM_RATES); // initializes with 0s?
    double currentPointValue = evaluatePoint(tariffUtilityEstimate, rateOffsets.toArray());
    eval2TOUTariff.put(currentPointValue, tariffUtilityEstimate.getCorrespondingSpec(rateOffsets.toArray()));
    while ( !converged(currentPointValue, previousPointValue) && evaluations < MAX_EVALUATIONS) {
      previousPointValue = currentPointValue;
      rateOffsets = rateOffsets.add(gradient.mapMultiply(alpha));
      currentPointValue = evaluatePoint(tariffUtilityEstimate, rateOffsets.toArray());
      eval2TOUTariff.put(currentPointValue, tariffUtilityEstimate.getCorrespondingSpec(rateOffsets.toArray()));
    }
    
    log.info("gradient ascent finished after " + evaluations + " evaluations");

    //}

    // return map
    return eval2TOUTariff; 
  }


  private boolean converged(double currentPointValue, double previousPointValue) {
    return (currentPointValue - previousPointValue) < UTILITY_CONVERGENCE_TOLERANCE;
  }


  private double computePartialDerivative(int i,
      TariffUtilityEstimate tariffUtilityEstimate,
      int NUM_RATES,
      TreeMap<Double, TariffSpecification> eval2touTariff) {
    double[] testPoint = new double[NUM_RATES];
    
    testPoint[i] = STEP_SIZE;
    double pprimePlus = evaluatePoint(tariffUtilityEstimate, testPoint);
    eval2touTariff.put(pprimePlus, tariffUtilityEstimate.getCorrespondingSpec(testPoint));
    
    testPoint[i] = -STEP_SIZE;
    double pprimeMinus = evaluatePoint(tariffUtilityEstimate, testPoint);
    eval2touTariff.put(pprimeMinus, tariffUtilityEstimate.getCorrespondingSpec(testPoint));
    
    return pprimePlus - pprimeMinus;
  }


  private double evaluatePoint(TariffUtilityEstimate tariffUtilityEstimate,
      double[] testPoint) {
    double value = tariffUtilityEstimate.value(testPoint);
    evaluations += 1;
    return value;
  }

}
