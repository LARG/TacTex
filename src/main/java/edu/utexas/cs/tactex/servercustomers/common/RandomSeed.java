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
 *     Copyright 2009-2013 the original author or authors.
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
package edu.utexas.cs.tactex.servercustomers.common;

import org.powertac.common.IdGenerator;
import org.powertac.common.state.StateChange;

/**
 * RandomSeed is used to store generated random seed in the database in
 * order to be able to "replay" PowerTAC competitions later on with
 * exactly the same random seed settings as originally used.
 * <p>
 * <b>Note</b> that server code is not intended to create instances of
 * RandomSeed directly. Instead, please request seeds through 
 * RandomSeedRepo.getRandomSeed(). This way, your code will work the same
 * whether using new seeds or replaying a previous simulation.</p>
 * <p>State log entry format:<br/>
 * <code>requesterClass::requesterId::purpose::value</code></p>
 *
 * @author Carsten Block, John Collins
 * @version 1.0 - January 01, 2011
 */
public class RandomSeed extends java.util.Random
{
  // needed because Random is serializable
  private static final long serialVersionUID = 1L;
  
  long id = IdGenerator.createId();
  String requesterClass;
  long requesterId;
  String purpose = "unspecified";
  long value;
  
  /**
   * Constructor that creates a new seed with a random value.
   * To keep the logfile simple, constructors are not logged in this
   * class; only the init() method is logged.
   */
  public RandomSeed (String classname, long requesterId, String purpose)
  {
    super();
    this.value = this.nextLong();
    init(classname, requesterId, purpose, value);
  }
  
  /**
   * Constructor to re-create a random seed with a given value.
   */
  public RandomSeed (String classname, long requesterId,
                     String purpose, long value)
  {
    super();
    init(classname, requesterId, purpose, value);
  }
  
  @StateChange
  private void init (String classname, long requesterId,
                     String purpose, long value)
  {
    this.requesterClass = classname;
    this.requesterId = requesterId;
    if (purpose != null)
      this.purpose = purpose;
    this.value = value;
    this.setSeed(this.value);
  }

  public long getId ()
  {
    return id;
  }

  public String getRequesterClass ()
  {
    return requesterClass;
  }

  public long getRequesterId ()
  {
    return requesterId;
  }

  public String getPurpose ()
  {
    return purpose;
  }

  public long getValue ()
  {
    return value;
  }
}
