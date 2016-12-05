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
 *     Copyright (c) 2013 by John Collins
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
package edu.utexas.cs.tactex.servercustomers.common.interfaces;

import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;

/**
 * Defines an interface for access to customer model details
 * that support tariff evaluation. This includes generation of per-tariff
 * customer usage/production profiles. 
 * Required by {@link org.powertac.common.TariffEvaluationHelper}.
 * Profiles can be for a full day, a full week,
 * or whatever time period makes sense for the customer. Usage may be for
 * a single individual customer or for the entire population.
 * Results are normalized by the tariff evaluation process, so the 
 * only requirement is that all profiles for a given customer use the
 * same time period (and the same weather), and the same population.
 *
 * @author John Collins
 */
public interface CustomerModelAccessor
{
  /**
   * Returns the CustomerInfo instance for this customer model.
   */
  public CustomerInfo getCustomerInfo ();

  /**
   * Returns a capacity profile for the given tariff. This must represent
   * the usage of a single individual in a population model over some
   * model-specific time period.
  */
//  public double[] getCapacityProfile (Tariff tariff);

  /**
   * Returns the inconvenience of switching brokers. The value may depend
   * on whether the current subscription is being switched to a superseding
   * tariff as a result of revocation.
   */
  public double getBrokerSwitchFactor (boolean isSuperseding);

  /**
   * Returns a [0,1] random value used to make choices using the logit choice
   * model.
   */
  public double getTariffChoiceSample();

  /**
   * Returns a [0,1] random value used to choose whether individual customers
   * evaluate tariffs or not.
   */
  public double getInertiaSample();

  // UNUSED - in broker we call it from somewhere else
  //public double getShiftingInconvenienceFactor(Tariff tariff);
}
