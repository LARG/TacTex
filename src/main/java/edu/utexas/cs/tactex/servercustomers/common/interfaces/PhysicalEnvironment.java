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
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *
 *     Copyright 2009-2010 the original author or authors.
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

package edu.utexas.cs.tactex.servercustomers.common.interfaces;

import org.powertac.common.Timeslot;
import org.powertac.common.WeatherReport;

import java.util.List;

/**
 * Common Interface for the Physical Environment module.
 *
 * @author David Dauer, Carsten Block
 * @version 0.1, January 2nd, 2011
 */
public interface PhysicalEnvironment {
  /**
   * Generates and returns weather forecasts for every enabled timeslot
   * The physical environment module is responsible for computing weather forecasts for each entry in {@code targetTimeslots} from the perspective of the {@code currentTimeslot}.
   *
   * Note: For the specific resulting {@link Weather} instance for which {@code weatherInstance.targetTimeslot == weatherInstance.currentTimeslot} (i.e. the "now" timeslot) {@code forecast} attribute must be set to false as this is the real (i.e. metered) weather data and not a forecast anymore
   *
   * @param currentTimeslot the current timeslot
   * @param targetTimeslots timeslots to generate weather forecasts for
   * @return a list of weather forecast objects
   */
  List<WeatherReport> generateWeatherData(Timeslot currentTimeslot, List<Timeslot> targetTimeslots);
}
