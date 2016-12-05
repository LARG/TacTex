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
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.powertac.common.Broker;
import org.powertac.common.ClearedTrade;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.Competition;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.CostCurvesPredictorService;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.CostCurvesPredictor;
import edu.utexas.cs.tactex.utils.BrokerUtils.PriceMwhPair;


/**
 * @author urieli
 *
 */
public class CostCurvesTest {

  private CostCurvesPredictorService costCurves;

  private int recordLength;
  private double clearingPriceForBootstrap;
  private int firstGameTimeslot;

  private Competition competition;
  private ConfiguratorFactoryService configuratorFactoryService;
  private BrokerContext brokerContext;
  private Broker thebroker;
  
  static final private int IGNORED_TIME_SLOTS = 24;
  

  @Before
  public void setUp () throws Exception
  {
    costCurves = new CostCurvesPredictorService();

    competition = mock(Competition.class);    
    when(competition.getBootstrapDiscardedTimeslots()).thenReturn(IGNORED_TIME_SLOTS);
    Competition.setCurrent(competition);

    recordLength = 7*24; // like real game
    clearingPriceForBootstrap = 22.0;
    firstGameTimeslot = 360; // like real game

    configuratorFactoryService = new ConfiguratorFactoryService();
    ConfiguratorFactoryService.GlobalConstants constants = configuratorFactoryService.new GlobalConstants();    
    ReflectionTestUtils.setField(constants, "USAGE_RECORD_LENGTH", recordLength);
    ReflectionTestUtils.setField(configuratorFactoryService, "CONSTANTS", constants); 

    ReflectionTestUtils.setField(costCurves,
                                 "configuratorFactoryService",
                                 configuratorFactoryService);

    brokerContext = mock(PowerTacBroker.class);
    thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);
    when(brokerContext.getBrokerUsername()).thenReturn(thebroker.getUsername());

  }
  
  /**
   * Test 
   */
  @Test
  public void test_initialize()
  {
    ReflectionTestUtils.setField(costCurves, "bootstrapMWh", null);
    ReflectionTestUtils.setField(costCurves, "bootstrapPayments", null);
    //    ReflectionTestUtils.setField(costCurves,"transactionsData", null);
    ReflectionTestUtils.setField(costCurves,"ts2boot", null);
    ReflectionTestUtils.setField(costCurves,"ts2mtx", null);
    ReflectionTestUtils.setField(costCurves,"ts2trade", null);
    ReflectionTestUtils.setField(costCurves,"ts2mycons", null);
    ReflectionTestUtils.setField(costCurves,"ts2totalcons", null);
    ReflectionTestUtils.setField(costCurves,"ts2wholesaleBasedCostPrediction", null);
    ReflectionTestUtils.setField(costCurves,"ts2consumptionBasedCostPrediction", null);
    ReflectionTestUtils.setField(costCurves,"ts2actualCost", null);
    

    costCurves.initialize(brokerContext);


    // arrays are not null, of the right size, and initialized 
    // with 0's
    int usageRecordLength = configuratorFactoryService.CONSTANTS.USAGE_RECORD_LENGTH();
    double[] bootstrapMWh = (double[]) 
        ReflectionTestUtils.getField(costCurves, "bootstrapMWh");
    assertNotNull("bootstrapMWh", bootstrapMWh);
    assertEquals("length of bootstrapMWh", usageRecordLength, bootstrapMWh.length);
    assertArrayEquals("initialized to 0s", new double[usageRecordLength], bootstrapMWh, 1e-6);

    double[] bootstrapPayments = (double[]) 
        ReflectionTestUtils.getField(costCurves, "bootstrapPayments");
    assertNotNull("bootstrapPayments", bootstrapPayments);
    assertEquals("length of bootstrapPayments", usageRecordLength, bootstrapPayments.length);
    assertArrayEquals("initialized to 0s", new double[usageRecordLength], bootstrapPayments, 1e-6);

    @SuppressWarnings("unchecked")
    HashMap<Integer,ArrayList<PriceMwhPair>> ts2boot = 
        (HashMap<Integer,ArrayList<PriceMwhPair>>) 
            ReflectionTestUtils.getField(costCurves, "ts2boot");
    assertNotNull("ts2boot", ts2boot);

    @SuppressWarnings("unchecked")
    HashMap<Integer,ArrayList<PriceMwhPair>> ts2mtx = 
        (HashMap<Integer,ArrayList<PriceMwhPair>>) 
            ReflectionTestUtils.getField(costCurves, "ts2mtx");
    assertNotNull("ts2mtx", ts2mtx);

    @SuppressWarnings("unchecked")
    HashMap<Integer,ArrayList<PriceMwhPair>> ts2trade = 
        (HashMap<Integer,ArrayList<PriceMwhPair>>) 
            ReflectionTestUtils.getField(costCurves, "ts2trade");
    assertNotNull("ts2trade", ts2trade);

    @SuppressWarnings("unchecked")
    HashMap<Integer, Double> ts2mycons = 
        (HashMap<Integer, Double>) 
            ReflectionTestUtils.getField(costCurves, "ts2mycons");
    assertNotNull("ts2mycons", ts2mycons);
    
    @SuppressWarnings("unchecked")
    HashMap<Integer, Double> ts2totalcons = 
        (HashMap<Integer, Double>) 
            ReflectionTestUtils.getField(costCurves, "ts2totalcons");
    assertNotNull("ts2totalcons", ts2totalcons);
    
    @SuppressWarnings("unchecked")
    HashMap<Integer, Double> ts2wholesaleBasedCostPrediction = 
        (HashMap<Integer, Double>) 
            ReflectionTestUtils.getField(costCurves, "ts2wholesaleBasedCostPrediction");
    assertNotNull("ts2wholesaleBasedCostPrediction", ts2wholesaleBasedCostPrediction);
    
    @SuppressWarnings("unchecked")
    HashMap<Integer, Double> ts2consumptionBasedCostPrediction = 
        (HashMap<Integer, Double>) 
            ReflectionTestUtils.getField(costCurves, "ts2consumptionBasedCostPrediction");
    assertNotNull("ts2consumptionBasedCostPrediction", ts2consumptionBasedCostPrediction);
    
    @SuppressWarnings("unchecked")
    HashMap<Integer, Double> ts2actualCost = 
        (HashMap<Integer, Double>) 
            ReflectionTestUtils.getField(costCurves, "ts2actualCost");
    assertNotNull("ts2actualCost", ts2actualCost);
  }
  
  @Test
  public void testRecordTradeData () {
  }

  @Test
  public void testRecordMtxData () {
  }
    
  @Test
  public void test_getAvgBootstrapPricePerKWh() {
    // want to start from a clear one
    costCurves.initialize(brokerContext);

    int usageRecordLength = recordLength;
    double[] mwh = new double[2 * usageRecordLength];
    double[] price = new double[2 * usageRecordLength];
    Arrays.fill(mwh, 1);
    Arrays.fill(price, 1);
    // building such that avg is 2mw x 10 + 1mw x 25 => 15
    mwh[0] = 2;
    price[0] = 10;
    mwh[usageRecordLength] = 1;
    price[usageRecordLength] = 25;
    MarketBootstrapData boot = new MarketBootstrapData(mwh, price);
    costCurves.handleMessage(boot);

    double actual = costCurves.getAvgBootstrapPricePerMwh(firstGameTimeslot);
    double expected = 15.0;
    assertEquals("first game timeslot indexes to first location", expected, actual, 1e-6);

    actual = costCurves.getAvgBootstrapPricePerMwh(firstGameTimeslot + 1);
    expected = 1.0;
    assertEquals("second game timeslot indexes to second location", expected, actual, 1e-6);
  }
  
  // ================ helper function ================
  private void initWithBootData() {
    double[] mwh = new double[configuratorFactoryService.CONSTANTS.USAGE_RECORD_LENGTH()];
    Arrays.fill(mwh, 20);
    double[] price = new double[configuratorFactoryService.CONSTANTS.USAGE_RECORD_LENGTH()];
    Arrays.fill(price, clearingPriceForBootstrap);
    MarketBootstrapData boot = new MarketBootstrapData(mwh, price);
    costCurves.handleMessage(boot);
  }
}
