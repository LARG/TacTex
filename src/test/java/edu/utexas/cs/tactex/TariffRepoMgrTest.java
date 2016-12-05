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

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powertac.common.Broker;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.TariffRepoMgrService;
import edu.utexas.cs.tactex.core.PowerTacBroker;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:test-config.xml"})
@DirtiesContext
public class TariffRepoMgrTest {

  @Autowired
  private TariffRepo tariffRepo;
  //private TariffRepo tariffRepo;
  
  @Autowired
  private TariffRepoMgrService tariffRepoMgrService;
  
  // private TimeslotRepo timeslotRepo;
  
  private PowerTacBroker brokerContext;
  private Broker thebroker;


  @Before
  public void setUp () throws Exception
  {
    brokerContext = mock(PowerTacBroker.class);
    thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);

    tariffRepoMgrService.initialize(brokerContext);

  }


  @SuppressWarnings("unchecked")
  @Test
  public void test_initialize () { 
     
    // initialize with arbitrary values
    
    ReflectionTestUtils.setField(tariffRepoMgrService,"deletedTariffs", null);
    
    // initialize should set all fields correctly
    tariffRepoMgrService.initialize(brokerContext);

    // maps should be initialized to empty
    //
    HashSet<Long> deletedTariffs = 
        (HashSet<Long>) 
            ReflectionTestUtils.getField(tariffRepoMgrService, "deletedTariffs");
    assertNotNull("deletedTariffs", deletedTariffs);
    assertEquals("deletedTariffs.length", 0, deletedTariffs.size());
    
    
  }

  @Test
  public void testAddRemoveTariffSpecs () {
    
    TariffSpecification consSpec1 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);    
    Rate rate = new Rate().withValue(-0.500);
    consSpec1.addRate(rate);

    TariffSpecification consSpec2 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);    
    rate = new Rate().withValue(-0.400);
    consSpec2.addRate(rate);

    TariffSpecification prodSpec1 = new TariffSpecification(thebroker, PowerType.PRODUCTION);    
    rate = new Rate().withValue(0.155);
    prodSpec1.addRate(rate);
    

    boolean success = tariffRepoMgrService.addToRepo(consSpec1);
    assertTrue("Successfully added to repo", success);
    assertEquals("TariffSpecification consSpec1 added", consSpec1, tariffRepoMgrService.findSpecificationById(consSpec1.getId())) ;
    assertNotNull("Tariff consSpec1 added", tariffRepoMgrService.findTariffById(consSpec1.getId()));
    // 
    success = tariffRepoMgrService.addToRepo(consSpec2);
    assertTrue("Successfully added to repo", success);
    assertEquals("TariffSpecification consSpec2 added", consSpec2, tariffRepoMgrService.findSpecificationById(consSpec2.getId())) ;
    assertNotNull("Tariff consSpec2 added", tariffRepoMgrService.findTariffById(consSpec2.getId()));
    //
    success = tariffRepoMgrService.addToRepo(prodSpec1);
    assertTrue("Successfully added to repo", success);
    assertEquals("TariffSpecification prodSpec1 added", prodSpec1, tariffRepoMgrService.findSpecificationById(prodSpec1.getId())) ;
    assertNotNull("Tariff prodSpec1 added", tariffRepoMgrService.findTariffById(prodSpec1.getId()));
    //
    // test findTariffSpecificationsByPowerType ()
    //
    // consumption
    List<TariffSpecification> consumptionSpecs = tariffRepoMgrService.findTariffSpecificationsByPowerType(PowerType.CONSUMPTION);
    assertEquals("2 consumption specs", 2, consumptionSpecs.size());
    assertTrue("consSpec1 found by power type", consumptionSpecs.contains(consSpec1));
    assertTrue("consSpec2 found by power type", consumptionSpecs.contains(consSpec2));
    //
    // production
    List<TariffSpecification> productionSpecs = tariffRepoMgrService.findTariffSpecificationsByPowerType(PowerType.PRODUCTION);
    assertEquals("1 production spec", 1, productionSpecs.size());
    assertTrue("prodSpec1 found by power type", productionSpecs.contains(prodSpec1));
    

    // "real remove" - test everything cleaned up propertly
    tariffRepoMgrService.removeRevokedSpec(consSpec2);
    // isRemoved()
    assertTrue("consSpec2 marked as removed", tariffRepoMgrService.isRemoved(consSpec2));
    // TariffSpecification and Tariff for both consSpec1 and consSpec2
    assertEquals("TariffSpecification consSpec1 still there", consSpec1, tariffRepoMgrService.findSpecificationById(consSpec1.getId())) ;
    assertNotNull("Tariff consSpec1 still there", tariffRepoMgrService.findTariffById(consSpec1.getId()));
    assertNull("TariffSpecification consSpec2 not there", tariffRepoMgrService.findSpecificationById(consSpec2.getId())) ;
    assertNull("Tariff consSpec2 not there", tariffRepoMgrService.findTariffById(consSpec2.getId()));
    // no longer found by PowerType
    consumptionSpecs = tariffRepoMgrService.findTariffSpecificationsByPowerType(PowerType.CONSUMPTION);
    assertEquals("1 consumption spec left", 1, consumptionSpecs.size());
    assertTrue("consSpec1 found by power type", consumptionSpecs.contains(consSpec1));
    assertFalse("consSpec2 not found by power type", consumptionSpecs.contains(consSpec2));


    // cleaning "temporary" specs - should be treated like they never existed
    HashSet<TariffSpecification> specsToRemove = new HashSet<TariffSpecification>();
    specsToRemove.add(prodSpec1);
    tariffRepoMgrService.removeTmpSpecsFromRepo(specsToRemove);
    // 
    assertFalse("prodSpec1 not marked as removed (since 'never existed')", tariffRepoMgrService.isRemoved(prodSpec1));
    assertFalse("consSpec1 was never removed", tariffRepoMgrService.isRemoved(consSpec1));
    assertTrue("consSpec2 marked as removed", tariffRepoMgrService.isRemoved(consSpec2));
    // 
    assertNull("TariffSpecification prodSpec1 not there", tariffRepoMgrService.findSpecificationById(prodSpec1.getId())) ;
    assertNull("Tariff prodSpec1 not there", tariffRepoMgrService.findTariffById(prodSpec1.getId()));
    assertNotNull("TariffSpecification consSpec1 still there", tariffRepoMgrService.findSpecificationById(consSpec1.getId())) ;
    assertNotNull("Tariff consSpec1 still there", tariffRepoMgrService.findTariffById(consSpec1.getId()));
    assertNull("TariffSpecification consSpec2 not there", tariffRepoMgrService.findSpecificationById(consSpec2.getId())) ;
    assertNull("Tariff consSpec2 not there", tariffRepoMgrService.findTariffById(consSpec2.getId()));
    // 
    // removed tariffs should not be found by findTariffSpecificationsByPowerType ()
    //
    // consumption
    consumptionSpecs = tariffRepoMgrService.findTariffSpecificationsByPowerType(PowerType.CONSUMPTION);
    assertEquals("1 consumption specs", 1, consumptionSpecs.size());
    assertTrue("consSpec1 found by power type", consumptionSpecs.contains(consSpec1));
    assertFalse("consSpec2 not found by power type", consumptionSpecs.contains(consSpec2));
    //
    // production
    productionSpecs = tariffRepoMgrService.findTariffSpecificationsByPowerType(PowerType.PRODUCTION);
    assertEquals("0 production spec", 0, productionSpecs.size());
    assertFalse("prodSpec1 not found by power type", productionSpecs.contains(prodSpec1));
    
  }
}
