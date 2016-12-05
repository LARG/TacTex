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
 *     Copyright 2009-2012 the original author or authors.
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

import java.util.List;

/**
 * This is the core of the Power TAC simulation framework, responsible
 * for starting, running, and completing a competition. Plugins that are
 * designed to run in the main simulation loop can be activated at the
 * proper phase during each timeslot by registering themselves by phase
 * number. 
 * @author jcollins
 */
public interface CompetitionControl
{
  /**
   * True just in case the sim is running in bootstrap mode - generating
   * bootstrap data.
   */
  public boolean isBootstrapMode ();
  
  /**
   * Registers the caller to be activated during each timeslot in the
   * proper phase sequence.
   */
  public void registerTimeslotPhase (TimeslotPhaseProcessor thing, int phase);
  
  /**
   * Attempts to log in a broker by username. Returns true just in case the
   * login is successful. The intent is that login will be successful if the
   * username is on the authorizedBrokerList, or if it is one of the
   * pre-configured brokers (usually only the default broker).
   */
  public boolean loginBroker (String username);

  /**
   * Sets the list of brokers authorized to log in to the next game. Must
   * be called after completion of a simulation and before calling runOnce(). 
   * This is normally done immediately after calling preGame().
   */
  public void setAuthorizedBrokerList (List<String> brokerList);
  
  /**
   * Waits for broker login, then starts and runs a simulation.
   */
  public void runOnce (boolean bootstrapMode);
  
  /**
   * True if a simulation (boot or sim) session is currently running.
   */
  public boolean isRunning();
  
  /**
   * Stops a running simulation, and sends out the SimEnd message
   * to brokers.
   */
  public void shutDown ();
}
