/*  * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
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
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *
Copyright 2011 the original author or authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an
 *     "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *     either express or implied. See the License for the specific language
 *     governing permissions and limitations under the License.
 */

package edu.utexas.cs.tactex.servercustomers.factoredcustomer;

import java.util.Map;

/**
 * Utility class with functions used to build the various structure objects.
 * 
 * @author Prashant Reddy
 */
final class ParserFunctions
{
    static double[] parseDoubleArray(String input) {
        String[] items = input.split(",");
        double[] ret = new double[items.length];
        for (int i=0; i < items.length; ++i) {
            ret[i] = Double.parseDouble(items[i]);
        }
        return ret;
    }
    
    static double[][] parseMapToDoubleArray(String input) {
        String[] pairs = input.split(",");
        double[][] ret = new double[pairs.length][2];
        for (int i=0; i < pairs.length; ++i) {
            String[] vals = pairs[i].split(":");
            ret[i][0] = Double.parseDouble(vals[0]);
            ret[i][1] = Double.parseDouble(vals[1]);
        }
        return ret;
    }

    static void parseRangeMap(String input, Map<Integer, Double> map) 
    {
        String[] pairs = input.split(",");
        for (int i=0; i < pairs.length; ++i) {
            String[] parts = pairs[i].split(":");
            Double value = Double.parseDouble(parts[1]);
            String[] range = parts[0].split("~");
            Integer start = Integer.parseInt(range[0].trim());
            Integer end = Integer.parseInt(range[1].trim());
            for (Integer key=start; key <= end; ++key) {
                map.put(key, value);
            }
        }        
    }
        
} // end class

