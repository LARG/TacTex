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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TimeService;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.TariffRepo;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.beans.factory.annotation.Autowired;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.TariffRepoMgrService;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.utils.ChargeEstimatorDefault;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:test-config.xml"})
public class ChargeEstimatorTest {

  private Instant baseTime;

  private ConfiguratorFactoryService configuratorFactoryService;

  @Autowired
  private TimeService timeService; // dependency injection

  private ChargeEstimatorDefault chargeEstimatorDefault;

  private PowerTacBroker brokerContext;

  TariffRepo tariffRepo;
  TariffRepoMgrService tariffRepoMgrService;



  @Before
  public void setUp () throws Exception
  {
    baseTime = new DateTime(2011, 2, 1, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    configuratorFactoryService = mock(ConfiguratorFactoryService.class);
    //timeService = new TimeService();
    timeService.setCurrentTime(baseTime);

    tariffRepo = new TariffRepo();
    tariffRepoMgrService = new TariffRepoMgrService();
    ReflectionTestUtils.setField(tariffRepoMgrService, 
                                 "tariffRepo", 
                                 tariffRepo);
    
    chargeEstimatorDefault = new ChargeEstimatorDefault(tariffRepoMgrService);
    
    brokerContext = mock(PowerTacBroker.class);
    Broker thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);
    

  }
  
  @Test
  public void testEstimateCharge () {

    
    // test estimating one charge: estimateCharge()
    ArrayRealVector customerEnergy = new ArrayRealVector(7*24, 1.6);
    TariffSpecification spec1 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    spec1.addRate(new Rate().withValue(-1.0));
    TestHelperUtils.addToRepo(spec1, tariffRepo, timeService);    

    double expected = 7*24 * 1.6 * -1.0;
    double actual = chargeEstimatorDefault.estimateCharge(customerEnergy, spec1);
    assertEquals("fixed rate", expected, actual, 1e-6);
    
    // test estimating several charges: estimateTariffCharges()
    TariffSpecification spec2 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    spec2.addRate(new Rate().withValue(-2.0));
    TestHelperUtils.addToRepo(spec2, tariffRepo, timeService);    

    List<TariffSpecification> tariffSpecs = new ArrayList<TariffSpecification>();
    tariffSpecs.add(spec1);
    tariffSpecs.add(spec2);

    CustomerInfo customer1 = new CustomerInfo("Austin", 1);
    CustomerInfo customer2 = new CustomerInfo("Dallas", 1);
    CustomerInfo customer3 = new CustomerInfo("Houston", 1);
    CustomerInfo customer4 = new CustomerInfo("NYC", 2); 
    List<CustomerInfo> customers = new ArrayList<CustomerInfo>();
    customers.add(customer1);
    customers.add(customer2);
    customers.add(customer3);
    customers.add(customer4);

    ArrayRealVector energy1 = new ArrayRealVector(7*24, 1.0);
    ArrayRealVector energy2 = new ArrayRealVector(7*24, 2.0);
    ArrayRealVector energy3 = new ArrayRealVector(7*24, 3.0);
    ArrayRealVector energy4 = new ArrayRealVector(7*24, 4.0);
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> customer2energy = new HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>>();
    customer2energy.put(customer1, new HashMap<TariffSpecification, ShiftedEnergyData>());
    customer2energy.put(customer2, new HashMap<TariffSpecification, ShiftedEnergyData>());
    customer2energy.put(customer3, new HashMap<TariffSpecification, ShiftedEnergyData>());
    customer2energy.put(customer4, new HashMap<TariffSpecification, ShiftedEnergyData>());
    customer2energy.get(customer1).put(spec1, new ShiftedEnergyData(energy1, 0.0));
    customer2energy.get(customer2).put(spec1, new ShiftedEnergyData(energy2, 0.0));
    customer2energy.get(customer3).put(spec1, new ShiftedEnergyData(energy3, 0.0));
    customer2energy.get(customer4).put(spec1, new ShiftedEnergyData(energy4, 0.0));
    customer2energy.get(customer1).put(spec2, new ShiftedEnergyData(energy1, 0.0));
    customer2energy.get(customer2).put(spec2, new ShiftedEnergyData(energy2, 0.0));
    customer2energy.get(customer3).put(spec2, new ShiftedEnergyData(energy3, 0.0));
    customer2energy.get(customer4).put(spec2, new ShiftedEnergyData(energy4, 0.0));
    
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> estimatedCharges = 
        chargeEstimatorDefault.estimateRelevantTariffCharges(tariffSpecs, customer2energy);

    expected = 7*24 * 1.0 * -1.0;
    actual = estimatedCharges.get(customer1).get(spec1);
    assertEquals("fixed rate, customer1, spec1", expected, actual, 1e-6);

    expected = 7*24 * 2.0 * -1.0;
    actual = estimatedCharges.get(customer2).get(spec1);
    assertEquals("fixed rate, customer2, spec1", expected, actual, 1e-6);

    expected = 7*24 * 3.0 * -1.0;
    actual = estimatedCharges.get(customer3).get(spec1);
    assertEquals("fixed rate, customer3, spec1", expected, actual, 1e-6);

    // charge computation is for 1 customer - therefore should ignore population number (here 2)
    expected = 7*24 * 4.0 * -1.0;
    actual = estimatedCharges.get(customer4).get(spec1);
    assertEquals("fixed rate, customer4, spec1", expected, actual, 1e-6);

    expected = 7*24 * 1.0 * -2.0;
    actual = estimatedCharges.get(customer1).get(spec2);
    assertEquals("fixed rate, customer1, spec2", expected, actual, 1e-6);

    expected = 7*24 * 2.0 * -2.0;
    actual = estimatedCharges.get(customer2).get(spec2);
    assertEquals("fixed rate, customer2, spec2", expected, actual, 1e-6);

    expected = 7*24 * 3.0 * -2.0;
    actual = estimatedCharges.get(customer3).get(spec2);
    assertEquals("fixed rate, customer3, spec2", expected, actual, 1e-6);

    // charge computation is for 1 customer - therefore should ignore population number (here 2)
    expected = 7*24 * 4.0 * -2.0;
    actual = estimatedCharges.get(customer4).get(spec2);
    assertEquals("fixed rate, customer4, spec2", expected, actual, 1e-6);
    
  }

}
