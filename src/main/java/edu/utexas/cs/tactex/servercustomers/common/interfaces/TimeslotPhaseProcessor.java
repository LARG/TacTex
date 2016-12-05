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
 *     Copyright 2011 the original author or authors.
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

import org.joda.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Plugins must extend this class in order to be invoked during timeslot
 * processing by the CompetitionControl. Each subclass must implement the
 * activate() method to do its thing during a timeslot, and must call its
 * superclass init() method during per-game initialization.
 * 
 * Per-timeslot processing takes place in 
 * phases. See https://github.com/powertac/powertac-server/wiki/Competition-controller-timeslot-process
 * for a summary of this process.
 * 
 * @author John Collins
 */
public abstract class TimeslotPhaseProcessor
{
  @Autowired
  private CompetitionControl competitionControlService;
  
  private int timeslotPhase = 0;
  
  public TimeslotPhaseProcessor ()
  {
    super();
  }
  
  /** This method must be called in the per-game initialization code in each 
   * implementing class. This is where the timeslot phase registration gets
   * done.
   */
  protected void init ()
  {
    competitionControlService.registerTimeslotPhase(this, timeslotPhase);
  }
  
  /**
   * This is the Spring-accessible setter for the phase number
   */
  public void setTimeslotPhase (int newValue)
  {
    timeslotPhase = newValue;
  }
  
  /**
   * This method gets called once during each timeslot. To get called, the
   * module must first call the register(phaseNumber) method on CompetitionControl.
   * The call will give the current simulation time and phase number in the
   * arguments.
   */
  public abstract void activate (Instant time, int phaseNumber);
}
