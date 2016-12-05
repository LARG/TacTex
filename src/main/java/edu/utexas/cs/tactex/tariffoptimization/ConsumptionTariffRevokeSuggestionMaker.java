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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;

import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.TariffSuggestionMaker;

public class ConsumptionTariffRevokeSuggestionMaker implements
    TariffSuggestionMaker {

  /**
   * Currently just filters all tariffs that are of 
   * consumption type from existing subscriptions.
   */
  @Override
  public List<TariffSpecification> suggestTariffs(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions,
      List<TariffSpecification> competingTariffs, MarketManager marketManager,
      ContextManager contextManager, Broker me) {
    
    List<TariffSpecification> result = new ArrayList<TariffSpecification>();
    for (TariffSpecification spec : tariffSubscriptions.keySet()) {
      if (spec.getPowerType().isConsumption())
        result.add(spec);
    }
    return result;
  }
}
