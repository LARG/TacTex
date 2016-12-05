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
package edu.utexas.cs.tactex.interfaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import edu.utexas.cs.tactex.utils.RegressionUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.PriceMwhPair;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaXYLambdas;
import edu.utexas.cs.tactex.utils.RegressionUtils.XYForRegression;
import weka.core.Instances;

/**
 * @author urieli
 *
 */
public interface CostCurvesDataProcessor {
  
  
  public ArrayList<Double> extractFeatures(double neededMwh, double competitorMwh); 
  
  /**
   * 
   */
  public ArrayList<String> getFeatureNames();

  /**
   * 
   * @param currentTimeslot
   * @return
   */
  public WekaXYLambdas createWekaRegressionInput(int currentTimeslot,
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2boot,
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2mtx,
      HashMap<Integer, ArrayList<PriceMwhPair>> ts2trade,
      HashMap<Integer, Double> ts2mycons, HashMap<Integer, Double> ts2totalcons);

}
