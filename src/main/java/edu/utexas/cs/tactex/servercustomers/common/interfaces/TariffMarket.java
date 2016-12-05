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

import java.util.List;

import org.powertac.common.Tariff;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.servercustomers.common.TariffSubscription;

/**
 * Tariff Market Receives, validates, and stores new tariffs, enforces tariff 
 * validity rules. Generates transactions to represent tariff publication fees.
 * Provides convenience methods to find tariffs that might be of interest to Customers.
 * <p>
 * Note that all methods driven by messages from the incoming message channel are
 * polymorphic methods that select by argument type at runtime. They all return a 
 * TariffStatus instance that can be routed back to the originating broker.</p>
 *
 * @author John Collins
 */
public interface TariffMarket {

  // -------------------- Customer API ------------------------
  /**
   * Subscribes a block of Customers from a single Customer model to
   * the specified Tariff, as long as the Tariff has not expired. The
   * actual subscription processing is deferred until the TariffMarket is
   * next activated. Unsubscribe is indicated by a negative
   * value for customerCount.
   */
  public void subscribeToTariff (Tariff tariff,
                                 CustomerInfo customer, 
                                 int customerCount);

  /**
   * Returns the list of currently active tariffs for the given PowerType.
   * The list contains only non-expired tariffs that cover the given type.
   */
  public List<Tariff> getActiveTariffList(PowerType type);

  /**
   * Returns the default tariff.
   */
  public Tariff getDefaultTariff (PowerType type);

  /**
   * Convenience method to set the default tariff at the beginning of the game.
   * Returns true just in case the tariff was valid and was successfully saved.
   */
  public boolean setDefaultTariff (TariffSpecification newTariff);

  /**
   * Registers a listener for publication of new Tariffs.
   */
  public void registerNewTariffListener (NewTariffListener listener);

  /**
   * Revokes tariffs for which TariffRevoke messages have been received
   * since the last time this method was called in an earlier timeslot.
   */
  @Deprecated
  public void processRevokedTariffs ();
}
