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
package edu.utexas.cs.tactex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.util.ListTools;
import org.powertac.util.Predicate;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.EnergyPredictionManagerService;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.interfaces.PortfolioManager;

public class EnergyPredictionTest {
  //private Instant baseTime;
  private PowerTacBroker broker;
  private TimeslotRepo timeslotRepo;
  private CustomerRepo customerRepo;
  private Competition competition;
  private EnergyPredictionManagerService energyPredictionManagerService;
  private PortfolioManager portfolioManager;
  static final private int IGNORED_TIME_SLOTS = 24;


  @Before
  public void setUp () throws Exception
  {
    broker = mock(PowerTacBroker.class);
    when(broker.getUsageRecordLength()).thenReturn(7*24);
    timeslotRepo = mock(TimeslotRepo.class);
    customerRepo = new CustomerRepo();
    competition = mock(Competition.class);    
    when(competition.getBootstrapDiscardedTimeslots()).thenReturn(IGNORED_TIME_SLOTS);
    Competition.setCurrent(competition);
    energyPredictionManagerService = new EnergyPredictionManagerService();
    portfolioManager = mock(PortfolioManager.class);
    ReflectionTestUtils.setField(energyPredictionManagerService,
        "timeslotRepo",
        timeslotRepo);
    ReflectionTestUtils.setField(energyPredictionManagerService,
        "customerRepo",
        customerRepo);
    ReflectionTestUtils.setField(energyPredictionManagerService,
        "portfolioManager",
        portfolioManager);
    energyPredictionManagerService.initialize(broker);
  
    // set the time
    //baseTime =
    //    new DateTime(2011, 2, 1, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // add 2 customers
    CustomerInfo podunk = new CustomerInfo("Podunk", 3).withPowerType(PowerType.CONSUMPTION);
    customerRepo.add(podunk);
    CustomerInfo midvale = new CustomerInfo("Midvale", 1000).withPowerType(PowerType.CONSUMPTION); 
    customerRepo.add(midvale);
  }
}
