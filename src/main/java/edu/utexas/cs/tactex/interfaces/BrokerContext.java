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
 *     Copyright (c) 2012 by the original author
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package edu.utexas.cs.tactex.interfaces;

import java.util.List;

import org.joda.time.Instant;
import org.powertac.common.Broker;

/**
 * Provides message handler registration, along with
 * access to competition context information, including
 * <ul>
 * <li>The message router for outgoing messages</li>
 * <li>The underlying org.powertac.common.Broker instance</li>
 * <li>The name of this broker</li>
 * <li>The base time for the current simulation</li>
 * <li>The length of common data arrays</li>
 * </ul>
 * @author John Collins
 */
public interface BrokerContext
{
  /**
   * Delegates registrations to the router
   */
  public void registerMessageHandler (Object handler, Class<?> messageType);
  
  /**
   * Sends an outgoing message. May need to be reimplemented in a remote broker.
   */
  public void sendMessage (Object message);

  //  /**
  //   * Returns the router for outgoing messages.
  //   */
  //  public MessageDispatcher getRouter ();
  
  /**
   * Returns the org.powerac.common.Broker instance
   */
  public Broker getBroker ();
  
  /**
   * Returns the broker name (username of the underlying Broker)
   */
  public String getBrokerUsername ();
  
  /**
   * Returns the simulation base time
   */
  public Instant getBaseTime ();
  
  /**
   * Returns length of data array used 
   * for tracking customer consumption/production
   */
  public int getUsageRecordLength ();
  
  /**
   * Returns the broker's list of competing brokers - non-public
   */
  public List<String> getBrokerList ();
}
