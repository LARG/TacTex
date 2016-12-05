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
 *     Copyright (c) 2013 by the original author
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
package edu.utexas.cs.tactex.servercustomers.common;

import org.powertac.common.BalancingTransaction;
import org.powertac.common.BankTransaction;
import org.powertac.common.Broker;
import org.powertac.common.CashPosition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.DistributionTransaction;
import org.powertac.common.MarketTransaction;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.Timeslot;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Constructs transaction objects in a way that guarantees that the correct
 * data goes into the state log, without requiring unnecessary couplings
 * on the part of transaction sources.
 * 
 * Each method constructs a new transaction for the given broker and
 * arguments and returns it. 
 * There is no attempt to cache them or look them up.
 * 
 * @author John Collins
 */
@Scope("singleton")
@Service
public class TransactionFactory
{
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  private int getTimeslotIndex ()
  {
    return timeslotRepo.currentSerialNumber();
  }
  
  public BankTransaction makeBankTransaction (Broker broker, double amount)
  {
    return new BankTransaction(broker, amount, getTimeslotIndex());
  }
  
  public BalancingTransaction
  makeBalancingTransaction (Broker broker, double kWh, double charge)
  {
    return new BalancingTransaction(broker, getTimeslotIndex(),
                                    kWh, charge);
  }
  
  public CashPosition makeCashPosition (Broker broker, double balance)
  {
    return new CashPosition(broker, balance, getTimeslotIndex());
  }
  
  public DistributionTransaction
  makeDistributionTransaction (Broker broker, double kWh, double charge)
  {
    return new DistributionTransaction(broker, getTimeslotIndex(),
                                       kWh, charge);
  }
  
  public MarketTransaction
  makeMarketTransaction (Broker broker, Timeslot timeslot,
                         double mWh, double price)
  {
    return new MarketTransaction(broker, getTimeslotIndex(),
                                 timeslot, mWh, price);
  }
  
  public TariffTransaction
  makeTariffTransaction(Broker broker, TariffTransaction.Type txType,
                        TariffSpecification spec,
                        CustomerInfo customer,
                        int customerCount,
                        double kWh, double charge)
  {
    return new TariffTransaction (broker, getTimeslotIndex(),
                                  txType, spec, customer,
                                  customerCount, kWh, charge);
  }
}
