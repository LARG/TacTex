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
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.OptimizerWrapper;
import edu.utexas.cs.tactex.interfaces.TariffUtilityEstimate;

public class OptimizerWrapperApacheAmoeba implements OptimizerWrapper {

  //private static final double SIMPLEX_EDGE = 0.001;
  private static final double SIMPLEX_EDGE = 0.01;

  @Override
  public TreeMap<Double, TariffSpecification> findOptimum(TariffUtilityEstimate tariffUtilityEstimate,
      int NUM_RATES, int numEval) {
    double[] startingVertex = new double[NUM_RATES]; // start from the fixed-rate tariff's offset
    //Arrays.fill(startingVertex, -0.5 * SIMPLEX_EDGE);
    //Arrays.fill(startingVertex, -1 * SIMPLEX_EDGE);

    // TODO if there are convergence issues, change these guessed thresholds
    //SimplexOptimizer optimizer = new SimplexOptimizer(1e-10, 1e-30); 
    //SimplexOptimizer optimizer = new SimplexOptimizer(1e-4, 1e-4); 
    //SimplexOptimizer optimizer = new SimplexOptimizer(1e-2, 10); 
    SimplexOptimizer optimizer = new SimplexOptimizer(1e-3, 5); 
    //SimplexOptimizer optimizer = new SimplexOptimizer(1e-2, 5); 
 
    final PointValuePair optimum
        = optimizer.optimize(
            new MaxEval(numEval),
            new ObjectiveFunction(new OptimizerWrapperApacheObjective(tariffUtilityEstimate)),
            GoalType.MAXIMIZE,
            new InitialGuess(startingVertex), 
            //new NelderMeadSimplex(NUM_RATES, -1 * SIMPLEX_EDGE)); 
            new NelderMeadSimplex(NUM_RATES, SIMPLEX_EDGE));// should be positive since this reflects decrease in (negative) charges
    
    TreeMap<Double, TariffSpecification> eval2TOUTariff = new TreeMap<Double, TariffSpecification>();
    eval2TOUTariff.put(optimum.getValue(), tariffUtilityEstimate.getCorrespondingSpec(optimum.getKey()));
    return eval2TOUTariff;
  }


}
