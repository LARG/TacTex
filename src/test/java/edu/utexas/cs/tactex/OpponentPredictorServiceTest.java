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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.OpponentPredictorService;
import edu.utexas.cs.tactex.PortfolioManagerService.CustomerRecord;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.utils.RegressionUtils.WekaLinRegData;

import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:test-config.xml"})
public class OpponentPredictorServiceTest {

  private OpponentPredictorService opponentPredictorService;
  private PowerTacBroker brokerContext;  
  private Broker mybroker;
  private Broker opponentbroker;

  /**
   *
   */
  @Before
  public void setUp () throws Exception
  {

    brokerContext = mock(PowerTacBroker.class);
    String brokername = "myBroker";
    mybroker = new Broker(brokername);
    when(brokerContext.getBroker()).thenReturn(mybroker);
    when(brokerContext.getBrokerUsername()).thenReturn(brokername);
    
    String opponentName = "opponentBroker";
    opponentbroker = new Broker(opponentName);
    
    opponentPredictorService = new OpponentPredictorService();

    opponentPredictorService.initialize(brokerContext);

  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_initialize () { 

    // initialize with arbitrary values

    ReflectionTestUtils.setField(opponentPredictorService,"brokerContext", null);
    ReflectionTestUtils.setField(opponentPredictorService,"myTs2tariff", null);
    ReflectionTestUtils.setField(opponentPredictorService,"opponentTs2tariff", null);

    // initialize should set all fields correctly
    opponentPredictorService.initialize(brokerContext);

    // maps should be initialized to empty
    //
    BrokerContext brokerContext = 
        (BrokerContext) 
        ReflectionTestUtils.getField(opponentPredictorService, "brokerContext");
    assertNotNull("brokerContext", brokerContext);

    TreeMap<Integer, TariffSpecification> myTs2tariff = 
        (TreeMap<Integer, TariffSpecification>) 
        ReflectionTestUtils.getField(opponentPredictorService, "myTs2tariff");
    assertNotNull("myTs2tariff", myTs2tariff);
    assertEquals("myTs2tariff.length", 0, myTs2tariff.size());

    TreeMap<Integer, TariffSpecification> opponentTs2tariff = 
        (TreeMap<Integer, TariffSpecification>) 
        ReflectionTestUtils.getField(opponentPredictorService, "opponentTs2tariff");
    assertNotNull("opponentTs2tariff", opponentTs2tariff);
    assertEquals("opponentTs2tariff.length", 0, opponentTs2tariff.size());

    int lastTimeslot = 
        (Integer) 
        ReflectionTestUtils.getField(opponentPredictorService, "lastTimeslot");
    assertEquals("lastTimeslot", lastTimeslot, 360);

    WekaLinRegData wekaData = 
        (WekaLinRegData) 
        ReflectionTestUtils.getField(opponentPredictorService, "wekaData");
    assertEquals("wekaData", null, wekaData);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_handleMsgTariffSpecification() {
    
    // create some tariffs
    TariffSpecification myConsSpec = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec.addRate(new Rate().withValue(-0.100));
    TariffSpecification myProdSpec = new TariffSpecification(mybroker, PowerType.PRODUCTION);
    myConsSpec.addRate(new Rate().withValue(0.200));
    TariffSpecification oppConsSpec = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec.addRate(new Rate().withValue(-0.300));
    TariffSpecification oppProdSpec = new TariffSpecification(opponentbroker, PowerType.PRODUCTION);
    oppConsSpec.addRate(new Rate().withValue(0.400));

    // First, initialize - container sizes should be 0
    opponentPredictorService.initialize(brokerContext);

    TreeMap<Integer, TariffSpecification> myTs2tariff = 
        (TreeMap<Integer, TariffSpecification>) 
        ReflectionTestUtils.getField(opponentPredictorService, "myTs2tariff");
    assertNotNull("myTs2tariff", myTs2tariff);
    assertEquals("myTs2tariff.length", 0, myTs2tariff.size());

    TreeMap<Integer, TariffSpecification> opponentTs2tariff = 
        (TreeMap<Integer, TariffSpecification>) 
        ReflectionTestUtils.getField(opponentPredictorService, "opponentTs2tariff");
    assertNotNull("opponentTs2tariff", opponentTs2tariff);
    assertEquals("opponentTs2tariff.length", 0, opponentTs2tariff.size());

    // Second, handle some messages
    opponentPredictorService.handleMessage(myConsSpec);
    opponentPredictorService.handleMessage(myProdSpec);
    opponentPredictorService.handleMessage(oppConsSpec);
    opponentPredictorService.handleMessage(oppProdSpec);

    // we should have one tariff in each container
    myTs2tariff = 
        (TreeMap<Integer, TariffSpecification>) 
        ReflectionTestUtils.getField(opponentPredictorService, "myTs2tariff");
    assertNotNull("myTs2tariff after handleMessage", myTs2tariff);
    assertEquals("myTs2tariff.length  after handleMessage", 1, myTs2tariff.size());
    // construct expected
    TariffSpecification myspec1 = myTs2tariff.firstEntry().getValue();
    List<Rate> myrates1 = myspec1.getRates();
    Rate myrate1 = myrates1.get(0);
    double actual = myrate1.getMinValue();
    double expected = -0.100;
    assertEquals("my rate", expected, actual, 1e-6);

    opponentTs2tariff = 
        (TreeMap<Integer, TariffSpecification>) 
        ReflectionTestUtils.getField(opponentPredictorService, "opponentTs2tariff");
    assertNotNull("opponentTs2tariff after handleMessage", opponentTs2tariff);
    assertEquals("opponentTs2tariff.length after handleMessage", 1, opponentTs2tariff.size());
    actual = opponentTs2tariff.firstEntry().getValue().getRates().get(0).getMinValue();
    expected = -0.300;
    assertEquals("opponent rate", expected, actual, 1e-6);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test_predictOpponentRates() {

    // Test 1 - state is always 1, action is always 0.9
    
    // First, initialize - container sizes should be 0
    opponentPredictorService.initialize(brokerContext);

    // my tariffs
    TariffSpecification myConsSpec1 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec1.addRate(new Rate().withValue(-0.100));
    TariffSpecification myConsSpec2 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec2.addRate(new Rate().withValue(-0.100 * .9));
    TariffSpecification myConsSpec3 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec3.addRate(new Rate().withValue(-0.100 * .9 * .9));
    TariffSpecification myConsSpec4 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec4.addRate(new Rate().withValue(-0.100 * .9 * .9 * .9));
    // opponent tariffs
    TariffSpecification oppConsSpec1 = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec1.addRate(new Rate().withValue(-0.100));
    TariffSpecification oppConsSpec2 = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec2.addRate(new Rate().withValue(-0.100 * .9));
    TariffSpecification oppConsSpec3 = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec3.addRate(new Rate().withValue(-0.100 * .9 * .9));
    TariffSpecification oppConsSpec4 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    oppConsSpec4.addRate(new Rate().withValue(-0.100 * .9 * .9 * .9));


    // assume my tariffs were recieved in handleMessage
    TreeMap<Integer, TariffSpecification> myTs2tariff = 
      new TreeMap<Integer, TariffSpecification>();
    myTs2tariff.put(361, myConsSpec1);
    myTs2tariff.put(367, myConsSpec2);
    myTs2tariff.put(373, myConsSpec3);
    myTs2tariff.put(379, myConsSpec4);
    TreeMap<Integer, TariffSpecification> opponentTs2tariff = 
      new TreeMap<Integer, TariffSpecification>();
    opponentTs2tariff.put(361, oppConsSpec1);
    opponentTs2tariff.put(367, oppConsSpec2);
    opponentTs2tariff.put(373, oppConsSpec3);
    opponentTs2tariff.put(379, oppConsSpec4);

    // set maps in fields 
    ReflectionTestUtils.setField(opponentPredictorService,
                                 "myTs2tariff",
                                 myTs2tariff);
    ReflectionTestUtils.setField(opponentPredictorService,
                                 "opponentTs2tariff",
                                 opponentTs2tariff);

    double mySuggestedRate = -0.100 * .9 * .9 * .9; // simple case - no change to state
    ArrayList<Double> predictedRates = 
        opponentPredictorService.predictOpponentRates(mySuggestedRate, 383); // timeslot I am supposed to compute tariffs
    assertEquals("predictedRates.size", 2, predictedRates.size());
    double expectedValue = -0.06561; // -0.100 * .9 * .9 * .9 * .9
    double actual = predictedRates.get(0);
    assertEquals("predicted rate", expectedValue, actual, 1e-6);
    expectedValue = -0.06561; // -0.100 * .9 * .9 * .9 * .9
    actual = predictedRates.get(1);
    assertEquals("predicted rate", expectedValue, actual, 1e-6);



    // Test 2 - states are 0.9,1,1.1 actions are noop, 0.9, 0.8 
    
    // First, initialize - container sizes should be 0
    opponentPredictorService.initialize(brokerContext);

    // my tariffs
    myConsSpec1 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec1.addRate(new Rate().withValue(-0.100));
    myConsSpec2 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec2.addRate(new Rate().withValue(-0.090));
    myConsSpec3 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec3.addRate(new Rate().withValue(-0.090 * 1 * .9 / 1.1));
    myConsSpec4 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    myConsSpec4.addRate(new Rate().withValue(-0.090 * 1 * .9 / 1.1 * .8)); // <= my last state is not part of the prediction, but determines next state
    // opponent tariffs
    oppConsSpec1 = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec1.addRate(new Rate().withValue(-0.090));
    oppConsSpec2 = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec2.addRate(new Rate().withValue(-0.090 * 1));
    oppConsSpec3 = new TariffSpecification(opponentbroker, PowerType.CONSUMPTION);
    oppConsSpec3.addRate(new Rate().withValue(-0.090 * 1 * .9 ));
    oppConsSpec4 = new TariffSpecification(mybroker, PowerType.CONSUMPTION);
    oppConsSpec4.addRate(new Rate().withValue(-0.090 * 1 * .9 / 1.1 * .8));


    // assume my tariffs were recieved in handleMessage
    myTs2tariff = 
      new TreeMap<Integer, TariffSpecification>();
    myTs2tariff.put(361, myConsSpec1);
    myTs2tariff.put(367, myConsSpec2);
    myTs2tariff.put(373, myConsSpec3);
    myTs2tariff.put(379, myConsSpec4);
    opponentTs2tariff = 
      new TreeMap<Integer, TariffSpecification>();
    opponentTs2tariff.put(361, oppConsSpec1);
    opponentTs2tariff.put(367, oppConsSpec2);
    opponentTs2tariff.put(373, oppConsSpec3);
    opponentTs2tariff.put(379, oppConsSpec4);

    // set maps in fields 
    ReflectionTestUtils.setField(opponentPredictorService,
                                 "myTs2tariff",
                                 myTs2tariff);
    ReflectionTestUtils.setField(opponentPredictorService,
                                 "opponentTs2tariff",
                                 opponentTs2tariff);

    mySuggestedRate = -0.090 * 1 * .9 / 1.1 * .8 / 1.2; // => a state of 1.2 => action should be 0.7
    predictedRates = 
        opponentPredictorService.predictOpponentRates(mySuggestedRate, 383); // timeslot I am supposed to compute tariffs
    assertEquals("predictedRates.size", 2, predictedRates.size());
    expectedValue = -0.090 * 1 * .9 / 1.1 * .8 * .9; // action should be 0.9
    actual = predictedRates.get(0);
    assertEquals("predicted current action", expectedValue, actual, 1e-6);
    expectedValue = -0.090 * 1 * .9 / 1.1 * .8 / 1.2 * .7; // action should be 0.7
    actual = predictedRates.get(1);
    assertEquals("predicted next action", expectedValue, actual, 1e-6);
  }
}
