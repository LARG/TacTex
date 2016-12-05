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

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.OptimizerWrapper;
import edu.utexas.cs.tactex.interfaces.TariffUtilityEstimate;

public class OptimizerWrapperApachePowell implements OptimizerWrapper {

  @Override
  public TreeMap<Double, TariffSpecification> findOptimum(TariffUtilityEstimate tariffUtilityEstimate,
      int NUM_RATES, int numEval) {
    
    double[] startingVertex = new double[NUM_RATES]; // start from the fixed-rate tariff's offset
    Arrays.fill(startingVertex, 0.0);
    
    PowellOptimizer optimizer = new PowellOptimizer(1e-3, 5);
    
    // needed since one optimization found positive 
    // charges (paying customer to consume...)
    double[][] boundaries = createBoundaries(NUM_RATES);
 
    final PointValuePair optimum
        = optimizer.optimize(
            new MaxEval(numEval),
            //new ObjectiveFunction(new MultivariateFunctionMappingAdapter(tariffUtilityEstimate, boundaries[0], boundaries[1])),
            new ObjectiveFunction(new OptimizerWrapperApacheObjective(tariffUtilityEstimate)),
            GoalType.MAXIMIZE,
            new InitialGuess(startingVertex)
            );
            
    TreeMap<Double, TariffSpecification> eval2TOUTariff = new TreeMap<Double, TariffSpecification>();
    eval2TOUTariff.put(optimum.getValue(), tariffUtilityEstimate.getCorrespondingSpec(optimum.getKey()));
    return eval2TOUTariff;
  }
  
  private double[][] createBoundaries(int NUM_RATES) {
    double[][] boundaries = new double[2][NUM_RATES];
    final double lower = -0.5;
    final double upper = 0;
    for (int i = 0; i < NUM_RATES; i++)
        boundaries[0][i] = lower;
    for (int i = 0; i < NUM_RATES; i++)
        boundaries[1][i] = upper;
    return boundaries;
  }
}
