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
/**
 * 
 */
package edu.utexas.cs.tactex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Competition;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.Broker;
import org.powertac.common.ClearedTrade;
import org.powertac.common.CustomerInfo;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.OrderbookOrder;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.Timeslot;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.test.util.ReflectionTestUtils;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.MarketManagerService;
import edu.utexas.cs.tactex.PortfolioManagerService;
import edu.utexas.cs.tactex.MarketManagerService.DPResult;
import edu.utexas.cs.tactex.core.PowerTacBroker;
import edu.utexas.cs.tactex.utils.BrokerUtils.PriceMwhPair;

/**
 * @author urieli
 *
 */
public class MarketManagerTest {
  
  public class MarketMgrThatDoesntSend extends MarketManagerService {
    @Override
    protected void submitOrder (double neededKWh, int timeslot, int currentTimeslotIndex, List<Timeslot> enabledTimeslots) {
    }
  }


  private MarketMgrThatDoesntSend marketManagerService;
  private ConfiguratorFactoryService configuratorFactoryService;
  private PowerTacBroker brokerContext; 
  private Broker thebroker;
  private Competition competition;
  private PortfolioManagerService portfolioManagerService;
  private Timeslot currentTimeslot;
  private List<Timeslot> enabledTimeslots;
  private TimeslotRepo timeslotRepo;

  static final private int IGNORED_TIME_SLOTS = 1;


  private double[] mwh;
  private double[] price;

  /**
   * @author urieli
   *
   */
  @Before
  public void setUp () throws Exception
  {
    marketManagerService = new MarketMgrThatDoesntSend();
    
    competition = mock(Competition.class);    
    when(competition.getBootstrapDiscardedTimeslots()).thenReturn(IGNORED_TIME_SLOTS);
    Competition.setCurrent(competition);

    configuratorFactoryService = new ConfiguratorFactoryService();
    ConfiguratorFactoryService.GlobalConstants constants = configuratorFactoryService.new GlobalConstants();    
    ReflectionTestUtils.setField(constants, "USAGE_RECORD_LENGTH", 3);
    ReflectionTestUtils.setField(constants, "MARKET_TRADES_ALPHA", 0.3);
    ReflectionTestUtils.setField(configuratorFactoryService, "CONSTANTS", constants);
    
    ReflectionTestUtils.setField(marketManagerService,
                                 "configuratorFactoryService",
                                 configuratorFactoryService);


    brokerContext = mock(PowerTacBroker.class);
    thebroker = new Broker("testBroker");
    when(brokerContext.getBroker()).thenReturn(thebroker);
    when(brokerContext.getBrokerUsername()).thenReturn(thebroker.getUsername());

    portfolioManagerService = mock(PortfolioManagerService.class);
    ReflectionTestUtils.setField(marketManagerService,
                                 "portfolioManager",
                                 portfolioManagerService);


    currentTimeslot = new Timeslot(363, null);
    enabledTimeslots = new ArrayList<Timeslot>();
    enabledTimeslots.add(new Timeslot(364, null)); // -1
    enabledTimeslots.add(new Timeslot(365, null)); // -2
    timeslotRepo = mock(TimeslotRepo.class);
    ReflectionTestUtils.setField(marketManagerService,
                                 "timeslotRepo",
                                 timeslotRepo);
    when(timeslotRepo.enabledTimeslots()).thenReturn(enabledTimeslots);
    
  }
    

  private void initWithBootData() {
    // initialize() is tested separately and should 
    // work correctly for this test
    marketManagerService.initialize(brokerContext);
    mwh = new double[] {1,2,3,1,2,3}; 
    price = new double[] {-10,-20,-10,-20,-10,-20};
    MarketBootstrapData boot = new MarketBootstrapData(mwh, price);
    marketManagerService.handleMessage(boot);
  }

  /**
   * Test 
   */
  @Test
  public void test_initialize()
  {
    // initialize with arbitrary values

    ReflectionTestUtils.setField(marketManagerService,"marketTotalMwh", 1234.0);
    ReflectionTestUtils.setField(marketManagerService,"marketTotalPayments", 1234.0);
    ReflectionTestUtils.setField(marketManagerService,"lastOrder", null);
    ReflectionTestUtils.setField(marketManagerService,"marketMWh", null);
    ReflectionTestUtils.setField(marketManagerService,"marketPayments", null);
    ReflectionTestUtils.setField(marketManagerService,"predictedUsage", null);
    ReflectionTestUtils.setField(marketManagerService,"actualUsage", null);
    ReflectionTestUtils.setField(marketManagerService,"orderbooks", null);
    ReflectionTestUtils.setField(marketManagerService,"maxTradePrice", 1234);
    ReflectionTestUtils.setField(marketManagerService,"minTradePrice", 1234);
    ReflectionTestUtils.setField(marketManagerService,"supportingBidGroups", null);
    ReflectionTestUtils.setField(marketManagerService,"dpCache2013", null);    
    ReflectionTestUtils.setField(marketManagerService,"shortBalanceTransactionsData", null);    
    ReflectionTestUtils.setField(marketManagerService,"surplusBalanceTransactionsData", null);    

    // initialize should set all fields correctly
    marketManagerService.initialize(brokerContext);


    // get fields from object and verify they are initialized correctly
 
    double marketTotalMwh = (Double) 
        ReflectionTestUtils.getField(marketManagerService, 
            "marketTotalMwh");
    assertEquals("marketTotalMwh", 0.0, marketTotalMwh, 1e-6);

    double marketTotalPayments = (Double) 
        ReflectionTestUtils.getField(marketManagerService, 
            "marketTotalPayments");
    assertEquals("marketTotalPayments", 0.0, marketTotalPayments, 1e-6);
    
    // map should be initialized to empty
    @SuppressWarnings("unchecked")
    HashMap<Integer, Order> lastOrder = (HashMap<Integer, Order>) 
        ReflectionTestUtils.getField(marketManagerService, "lastOrder");
    assertNotNull("lastOrder", lastOrder);
    assertEquals("lastOrder.length", 0, lastOrder.size());

    // arrays are not null, of the right size, and initialized 
    // with 0's
    int usageRecordLength = configuratorFactoryService.CONSTANTS.USAGE_RECORD_LENGTH();
    double[] marketMWh = (double[]) 
        ReflectionTestUtils.getField(marketManagerService, "marketMWh");
    assertNotNull("marketMWh", marketMWh);
    assertEquals("length of marketMWh",   
        usageRecordLength, marketMWh.length);
    assertArrayEquals(new double[usageRecordLength], marketMWh, 1e-6);
  
    double[] marketPayments = (double[]) 
        ReflectionTestUtils.getField(marketManagerService, "marketPayments");
    assertNotNull("marketPayments", marketPayments);
    assertEquals("length of marketPayments", 
        usageRecordLength, marketPayments.length);
    assertArrayEquals(new double[usageRecordLength], marketPayments, 1e-6);

    double[][] predictedUsage = 
        (double[][]) 
            ReflectionTestUtils.getField(marketManagerService, "predictedUsage");
    assertNotNull("predictedUsage", predictedUsage);
    assertEquals("predictedUsage.size", 24, predictedUsage.length);
    for(double[] p : predictedUsage) {
    	assertNotNull("predictedUsage[i]", p);
    	assertEquals("p.size", 2500, p.length);
    	assertEquals("p[i]", (Integer)0, p[0], 1e-6);
    }
    
    double[] actualUsage = (double[])
    	ReflectionTestUtils.getField(marketManagerService, "actualUsage");
    assertNotNull("actualUsage", actualUsage);
    assertEquals("actualUsage.size", 2500, actualUsage.length);
    assertEquals("actualUsage[i]", 0, actualUsage[0], 1e-6);

    @SuppressWarnings("unchecked")
    HashMap<Integer, Orderbook> orderbooks = (HashMap<Integer, Orderbook>) 
        ReflectionTestUtils.getField(marketManagerService, "orderbooks");
    assertNotNull("orderbooks", orderbooks);
    assertEquals("orderbooks.length", 0, orderbooks.size());

    double maxTradePrice = (Double) 
        ReflectionTestUtils.getField(marketManagerService, 
            "maxTradePrice");
    assertEquals("maxTradePrice", -Double.MAX_VALUE, maxTradePrice, 1e-6);

    double minTradePrice = (Double) 
        ReflectionTestUtils.getField(marketManagerService, 
            "minTradePrice");
    assertEquals("minTradePrice", Double.MAX_VALUE, minTradePrice, 1e-6);

    @SuppressWarnings("unchecked")
    TreeMap<Integer ,TreeMap<Double, Double>> supportingBidGroups = 
        (TreeMap<Integer ,TreeMap<Double, Double>>)
            ReflectionTestUtils.getField(marketManagerService, "supportingBidGroups");
    assertNotNull("supportingBidGroups", supportingBidGroups);
    assertEquals("supportingBidGroups.length", 0, supportingBidGroups.size());
    
    MarketManagerService.DPCache dpCache2013 = 
        (MarketManagerService.DPCache)
            ReflectionTestUtils.getField(marketManagerService, "dpCache2013");
    assertNotNull("dpCache2013", dpCache2013);
    @SuppressWarnings("unchecked")
    ArrayList<Double> bestActions = 
        (ArrayList<Double>)
          ReflectionTestUtils.getField(dpCache2013, "bestActions");
    assertNotNull("dpCache2013.bestActions", bestActions);
    @SuppressWarnings("unchecked")
    ArrayList<Double> stateValues = 
        (ArrayList<Double>)
          ReflectionTestUtils.getField(dpCache2013, "stateValues");
    assertNotNull("dpCache2013.stateValues", stateValues);
    @SuppressWarnings("unchecked")
    HashMap<Integer, Boolean> validTimeslots = 
        (HashMap<Integer, Boolean>)
          ReflectionTestUtils.getField(dpCache2013, "validTimeslots");
    assertNotNull("dpCache2013.validTimeslots", validTimeslots);
    assertEquals("dpCache2013.bestActions", 0, dpCache2013.getBestActions().size());
    assertEquals("dpCache2013.stateValues", 0, dpCache2013.getStateValues().size());
    assertEquals("dpCache2013.valid(ts)", false, dpCache2013.isValid(currentTimeslot.getSerialNumber()));
     
    // map should be initialized to empty
    @SuppressWarnings("unchecked")
    ArrayList<PriceMwhPair>shortBalanceTransactionsData = 
        (ArrayList<PriceMwhPair>) 
            ReflectionTestUtils.getField(marketManagerService, "shortBalanceTransactionsData");
    assertNotNull("shortBalanceTransactionsData", shortBalanceTransactionsData);
    assertEquals("shortBalanceTransactionsData.length", 0, shortBalanceTransactionsData.size());

    @SuppressWarnings("unchecked")
    ArrayList<PriceMwhPair>surplusBalanceTransactionsData = 
        (ArrayList<PriceMwhPair>) 
            ReflectionTestUtils.getField(marketManagerService, "surplusBalanceTransactionsData");
    assertNotNull("surplusBalanceTransactionsData", surplusBalanceTransactionsData);
    assertEquals("surplusBalanceTransactionsData.length", 0, surplusBalanceTransactionsData.size());

  }

  /**
   * testing the usage predictions vs actual
   */
  @Test
  public void testPredictedVsActualUsage () {
    // initialize should set all fields correctly
    // test_initialize() should verify it works fine
    marketManagerService.initialize(brokerContext);
    
    // test: transaction adding to actual usage
    int ts = currentTimeslot.getSerialNumber();
    TariffSpecification spec1 = new TariffSpecification(brokerContext.getBroker(), PowerType.CONSUMPTION);
    CustomerInfo austin = new CustomerInfo("Austin", 3);
    TariffTransaction ttx1 = new TariffTransaction(brokerContext.getBroker(), 
        ts, 
        TariffTransaction.Type.CONSUME, 
        spec1, 
        austin, 
        3, 
        -10, 
        1);
    marketManagerService.handleMessage(ttx1);
    double[] actualUsage = (double[])
    	ReflectionTestUtils.getField(marketManagerService, "actualUsage");
    assertEquals("actualUsage[ts]", -10, actualUsage[ts], 1e-6);
    
    TariffTransaction ttx2 = new TariffTransaction(brokerContext.getBroker(), 
        ts, 
        TariffTransaction.Type.CONSUME, 
        spec1, 
        austin, 
        3, 
        -5, 
        1);
    marketManagerService.handleMessage(ttx2);
    actualUsage = (double[])
        ReflectionTestUtils.getField(marketManagerService, "actualUsage");
    assertEquals("actualUsage[ts]", -15, actualUsage[ts], 1e-6);
    

    // test: recording predicted usage
    when(portfolioManagerService.collectUsage(any(int.class))).thenReturn(20.0);
    when(portfolioManagerService.collectShiftedUsage(any(int.class), any(int.class))).thenReturn(20.0);
    marketManagerService.activate(ts);
    int firstEnabled = enabledTimeslots.get(0).getSerialNumber();
    int lastEnabled = enabledTimeslots.get(enabledTimeslots.size() - 1).getSerialNumber();    
    double[][] predictedUsage = (double[][])
    	ReflectionTestUtils.getField(marketManagerService, "predictedUsage");
    assertEquals("predictedUsage[1][next]", -20, predictedUsage[0][firstEnabled], 1e-6);
    assertEquals("predictedUsage[1][next]", -20, predictedUsage[1][lastEnabled], 1e-6);
  }

  /**
   * NOTE:
   * If we don't want to assume a specific 
   * method for accumulating bootstrap data,
   * we could test a record that is in the 
   * length of the usage record, so each cell
   * gets only 1 data point. 
   */
  @Test
  public void testMarketBootstrapData ()
  {
    initWithBootData();

    // test MarketBootstrapData
    
    double[] assignedMwh = (double[]) ReflectionTestUtils.getField(marketManagerService,
        "marketMWh");
    double[] assignedPayments = (double[]) ReflectionTestUtils.getField(marketManagerService,
        "marketPayments");
    
    
    // construct expected - prices should be recorded as positive
    double[] expectedMwh = {1+1, 2+2, 3+3};
    double[] expectedPayments = {1*10 + 1*20, 2*10 + 2*20, 3*10 + 3*20};

    assertArrayEquals("MarketBootstrapData correct mwh data", expectedMwh, assignedMwh, 1e-6);
    assertArrayEquals("MarketBootstrapData correct Payments data", expectedPayments, assignedPayments, 1e-6);   
  }

  /**
   * Testing the private method that updates
   * estimation of future costs (cost curves)
   */
  @Test
  public void testUpdateMarketTracking ()
  {
    marketManagerService.initialize(brokerContext);


    double executionMWh3 = 2 - 1;
    double executionPrice3 = 3 - 0.1;
    marketManagerService.updateMarketTracking(3, executionMWh3, executionPrice3);

    double executionMWh4 = 2;
    double executionPrice4 = 3;
    marketManagerService.updateMarketTracking(4, executionMWh4, executionPrice4);

    double executionMWh5 = 2 + 1;
    double executionPrice5 = 3 + 0.1;
    marketManagerService.updateMarketTracking(5, executionMWh5, executionPrice5);


    double[] assignedMwh = (double[]) ReflectionTestUtils.getField(marketManagerService,
        "marketMWh");
    double[] assignedPayments = (double[]) ReflectionTestUtils.getField(marketManagerService,
        "marketPayments");

    double[] expectedMwh = new double[3];
    double[] expectedPayments = new double[3];

    // NOTE: should have been 3=>0, 4=>1, 5=>2, 
    // but since IGNORED_TIME_SLOTS == 1,
    // the mapping is 4=>0, 5=>1, 3=>2, i.e. the one here:
    expectedMwh [0] += executionMWh4;
    expectedMwh[1] += executionMWh5;
    expectedMwh[2] += executionMWh3;
    expectedPayments[0] += executionMWh4 * executionPrice4;
    expectedPayments[1] += executionMWh5 * executionPrice5;
    expectedPayments[2] += executionMWh3 * executionPrice3;

    assertArrayEquals("updateMarketTracking() correct mwh data", expectedMwh, assignedMwh, 1e-6);
    assertArrayEquals("updateMarketTracking() correct Payments data", expectedPayments, assignedPayments, 1e-6); 

  }

  /**
   * Testing the private method that tracks
   * min/max trade prices
   */
  @Test
  public void testMinMaxTradePrice ()
  {
    marketManagerService.initialize(brokerContext);

    marketManagerService.updateLowHighTradePrices(-1);
    marketManagerService.updateLowHighTradePrices(0);
    marketManagerService.updateLowHighTradePrices(1);

    double maxTradePrice = (Double) ReflectionTestUtils.getField(marketManagerService,
        "maxTradePrice");
    double minTradePrice = (Double) ReflectionTestUtils.getField(marketManagerService,
        "minTradePrice");
    assertEquals("maxTradePrice", 1, maxTradePrice, 1e-6);
    assertEquals("minTradePrice", -1, minTradePrice, 1e-6); 
  }

  /**
   * Testing the private method that learns
   * bid clearing distributions for the 
   * bidding MDP
   */
  @Test
  public void testRecordTradeResult ()
  {
    marketManagerService.initialize(brokerContext); 
    
    int tradeCreationTime = 1;

    double executionPrice = 2;
    double executionMWh = 3;
    marketManagerService.recordTradeResult(tradeCreationTime, 4, executionPrice, executionMWh);
    marketManagerService.recordTradeResult(tradeCreationTime, 5, executionPrice, executionMWh);
    marketManagerService.recordTradeResult(tradeCreationTime, 6, executionPrice, executionMWh);
    marketManagerService.recordTradeResult(tradeCreationTime, 10, executionPrice - 1, executionMWh);
    marketManagerService.recordTradeResult(tradeCreationTime, 10, executionPrice + 1, executionMWh);

    // test: trades are recorded
    @SuppressWarnings("unchecked")
    TreeMap<Integer,ArrayList<PriceMwhPair>> supportingBidGroups = 
        (TreeMap<Integer, ArrayList<PriceMwhPair>>)
            ReflectionTestUtils.getField(marketManagerService,
                "supportingBidGroups");
    assertEquals("supportingBidGroups.size", 4, supportingBidGroups.size());
    assertEquals("supportingBidGroups.get(4).size", 1, supportingBidGroups.get(4).size());
    assertEquals("supportingBidGroups.get(5).size", 1, supportingBidGroups.get(5).size());
    assertEquals("supportingBidGroups.get(6).size", 1, supportingBidGroups.get(6).size());
    assertEquals("supportingBidGroups.get(10).size", 2, supportingBidGroups.get(10).size());
    assertEquals("supportingBidGroups(4,0).price", executionPrice, supportingBidGroups.get(4).get(0).getPricePerMwh(), 1e-6);
    assertEquals("supportingBidGroups(5,0).price", executionPrice, supportingBidGroups.get(5).get(0).getPricePerMwh(), 1e-6);
    assertEquals("supportingBidGroups(6,0).price", executionPrice, supportingBidGroups.get(6).get(0).getPricePerMwh(), 1e-6);
    assertEquals("supportingBidGroups(10,0).price is lowest", executionPrice - 1, supportingBidGroups.get(10).get(0).getPricePerMwh(), 1e-6);
    assertEquals("supportingBidGroups(10,1).price is higher", executionPrice + 1, supportingBidGroups.get(10).get(1).getPricePerMwh(), 1e-6);
    assertEquals("supportingBidGroups(4,0).mwh", executionMWh, supportingBidGroups.get(4).get(0).getMwh(), 1e-6);
    assertEquals("supportingBidGroups(5,0).mwh", executionMWh, supportingBidGroups.get(5).get(0).getMwh(), 1e-6);
    assertEquals("supportingBidGroups(6,0).mwh", executionMWh, supportingBidGroups.get(6).get(0).getMwh(), 1e-6);
    assertEquals("supportingBidGroups(10,0).mwh", executionMWh, supportingBidGroups.get(10).get(0).getMwh(), 1e-6);
    assertEquals("supportingBidGroups(10,1).mwh", executionMWh, supportingBidGroups.get(10).get(1).getMwh(), 1e-6);
  }

  /**
   * Here we only verify function calls -  
   * each of the called functions is tested 
   * separately 
   */
  @Test
  public void testClearedTrade ()
  { 
    // prepare spy
    marketManagerService.initialize(brokerContext); 
    MarketMgrThatDoesntSend spyMktMgr = spy(marketManagerService); 

    // prepare some trade data
    ClearedTrade trade;
    double executionMWh = 2;
    double executionPrice = 3;    
    Instant now = new Instant(null); 
    Instant tradeCreationTime = now.plus(TimeService.HOUR); // this probably doesn't work but what we care about is the next line    
    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(1);

    // record trade twice: with and without useMtx
    trade = new ClearedTrade(4, executionMWh, executionPrice, tradeCreationTime);
    ReflectionTestUtils.setField(configuratorFactoryService, "useMtx", false); 
    spyMktMgr.handleMessage(trade);
    ReflectionTestUtils.setField(configuratorFactoryService, "useMtx", true); 
    spyMktMgr.handleMessage(trade);
    
    // verify calls: updateMarketTracking only called if useMtx == false
    //verify(mockMktMgr, times(2)).handleMessage(trade);
    verify(spyMktMgr, times(1)).updateMarketTracking(4, executionMWh, executionPrice);
    verify(spyMktMgr, times(2)).updateLowHighTradePrices(executionPrice);
    verify(spyMktMgr, times(2)).recordTradeResult(timeslotRepo.getTimeslotIndex(tradeCreationTime), 4, executionPrice, executionMWh);
  }

  /**
   * Here we only verify function calls -  
   * called function(s) is tested separately 
   */
  @Test
  public void testMarketTransaction ()
  {
    // prepare spy
    marketManagerService.initialize(brokerContext); 
    MarketMgrThatDoesntSend spyMktMgr = spy(marketManagerService); 

    // prepare a market transaction 
    double executionMWh = 2;
    double executionPrice = 3;    
    Instant now = new Instant(null); 
    Instant tradeCreationTime = now.plus(TimeService.HOUR); // this probably doesn't work but what we care about is the next line    
    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(1);


    // record trade twice: with and without useMtx
    MarketTransaction mtx = new MarketTransaction(thebroker, 1, 4,
        executionMWh, executionPrice);
    ReflectionTestUtils.setField(configuratorFactoryService, "useMtx", false); 
    spyMktMgr.handleMessage(mtx);
    ReflectionTestUtils.setField(configuratorFactoryService, "useMtx", true); 
    spyMktMgr.handleMessage(mtx);
    
    // verify calls: updateMarketTracking only called if useMtx == true
    verify(spyMktMgr, times(1)).updateMarketTracking(4, executionMWh, executionPrice);
  }

  /**
   * Test 
   * TODO cover a few more cases 
   */
  @Test
  public void test_MeanMarketPrice()
  {
    initWithBootData();

    double avgPricePerMWH = marketManagerService.getMeanMarketPricePerMWH();
    assertEquals("correct value for market price/mWh", 15.0, avgPricePerMWH, 1e-6);


    double avgPricePerKWH = marketManagerService.getMeanMarketPricePerKWH();
    assertEquals("correct value for market price/kWh", 0.015, avgPricePerKWH, 1e-6);
    
     ArrayRealVector avgPriceArray = marketManagerService.getMarketAvgPricesArrayKwh();
     double[] expected = new double[] {0.015, 0.015, 0.015};
     assertArrayEquals("correct value for avg-prices array", expected, avgPriceArray.toArray(), 1e-6);
     
     double expected0 = 0.015;
     double expected1 = 0.015;
     double expected2 = 0.015;
     assertEquals("correct avg price per slot0",expected0, marketManagerService.getMarketAvgPricePerSlotKWH(0), 1e-6);
     assertEquals("correct avg price per slot1",expected1, marketManagerService.getMarketAvgPricePerSlotKWH(1), 1e-6);
     assertEquals("correct avg price per slot2",expected2, marketManagerService.getMarketAvgPricePerSlotKWH(2), 1e-6);
     assertEquals("correct avg price per slot - wrapping index",expected0, marketManagerService.getMarketAvgPricePerSlotKWH(4), 1e-6);
  }

  /**
   * Test 
   */
  @Test
  public void test_getMarketPricePerKWHRecordStd()
  {
    initWithBootData();
    double std = marketManagerService.getMarketPricePerKWHRecordStd();
    // bias corrected std: sum the squared errors, divide by n-1, and take the
    // root, and divide by 1000 to get from mWh => kWh
    assertEquals("correct value for market price/kWh std", 0, std, 1e-6);
    //assertEquals("correct value for market price/kWh std", Math.sqrt(3 * Math.pow(2,2) / (3 - 1)) / 1000, std, 1e-6);
  }
  
  /**
   * correctess of whether we can run DP
   * this tests assumes that cleared trades
   * are handled correctly: this should be taken
   * care of in a test above
   *
   * Test
   */
  @Test
  public void test_canRunDP() {
    // make sure all fields are in place
    marketManagerService.initialize(brokerContext);
    
    ClearedTrade trade;
    BalancingTransaction balanceTx;
    Instant now = new Instant(null); 
    Instant tradeCreationTime = now.plus(364 * TimeService.HOUR); // this probably doesn't work but what we care about is the next line    
    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(364);
    
    // add the 'enabled timeslots from setUp(), eventhough 
    // they are not contiguous
    trade = new ClearedTrade(364, 0, 0, tradeCreationTime);
    marketManagerService.handleMessage(trade);
    trade = new ClearedTrade(365, 0, 0, tradeCreationTime);
    marketManagerService.handleMessage(trade);
    balanceTx  = new BalancingTransaction(thebroker, 1, -1234, -1.234);
    marketManagerService.handleMessage(balanceTx);

    int backup = (Integer)
        ReflectionTestUtils.getField(configuratorFactoryService.CONSTANTS,
            "LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES");
    ReflectionTestUtils.setField(configuratorFactoryService.CONSTANTS,
        "LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES", 1); assertEquals("canRunDP()",
            true, marketManagerService.canRunDP(currentTimeslot.getSerialNumber(), enabledTimeslots));
    ReflectionTestUtils.setField(configuratorFactoryService.CONSTANTS,
        "LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES", 2); assertEquals("canRunDP()",
            false, marketManagerService.canRunDP(currentTimeslot.getSerialNumber(), enabledTimeslots));
    // restore value
    ReflectionTestUtils.setField(configuratorFactoryService.CONSTANTS,
        "LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES", backup);
  }

    /**
   * Test
   */
  @Test
  public void test_ChargeMwhPair() {
    MarketManagerService.ChargeMwhPair pair1 = marketManagerService.new ChargeMwhPair(1.0, -1.0);
    assertEquals("getCharge()", 1.0, pair1.getCharge(), 1e-6);
    assertEquals("getMwh()", -1.0, pair1.getMwh(), 1e-6);
  }

  /**
   * Test
   */
  @Test
  public void test_RunDP2013() {
    // make sure all fields are in place
    marketManagerService.initialize(brokerContext);
    
    
    ClearedTrade trade;
    BalancingTransaction balanceTx;
    int currentTimeslotIndex = 363; 
    // easy initialization, but not consistent with ts=363 
    Instant now = new Instant(null); 
    Instant tradeCreationTime = now.plus(364 * TimeService.HOUR); // this probably doesn't work but what we care about is the next line    
    

    // initialize with transactions from ts 364

    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(364);
    // 15/Mwh for n+1
    trade = new ClearedTrade(364, 1, 15, tradeCreationTime); 
    //                       ts  mwh charge
    marketManagerService.handleMessage(trade);

    // 20/Mwh for n+2
    trade = new ClearedTrade(365, 1, 20, tradeCreationTime); 
    //                       ts  mwh charge

    marketManagerService.handleMessage(trade);
    //0.025/Kwh => 25/Mwh for balancing
    balanceTx  = new BalancingTransaction(thebroker, 364, -1000, -25); 
    //                                               ts    kwh  charge
    marketManagerService.handleMessage(balanceTx);



    // initialize with transactions from ts 365 

    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(365);
    // 15/Mwh for n+1
    trade = new ClearedTrade(365, 1, 10, tradeCreationTime); 
    //                       ts  mwh charge
    marketManagerService.handleMessage(trade);

    // 20/Mwh for n+2
    trade = new ClearedTrade(366, 1, 20, tradeCreationTime); 
    //                       ts  mwh charge

    marketManagerService.handleMessage(trade);
    //0.025/Kwh => 25/Mwh for balancing
    balanceTx  = new BalancingTransaction(thebroker, 365, -1000, -25); 
    //                                               ts    kwh  charge
    marketManagerService.handleMessage(balanceTx);



    // initialize with transactions from ts 366 

    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(366);
    // 15/Mwh for n+1
    trade = new ClearedTrade(366, 2, 20, tradeCreationTime); // TODO still 10 but should fail test since doesn't do a weighted avg
    //                       ts  mwh charge
    marketManagerService.handleMessage(trade);

    // 20/Mwh for n+2
    trade = new ClearedTrade(367, 1, 10, tradeCreationTime); 
    //                       ts  mwh charge

    marketManagerService.handleMessage(trade);
    //0.025/Kwh => 25/Mwh for balancing
    balanceTx  = new BalancingTransaction(thebroker, 366, -2000, -20); // -10
    //                                               ts    kwh  charge
    marketManagerService.handleMessage(balanceTx);



    // Run DP
    
    double neededMwh = 10; // not used
    marketManagerService.runDP2013(neededMwh, currentTimeslotIndex);

    MarketManagerService.DPCache dpCache2013 = 
        (MarketManagerService.DPCache)
            ReflectionTestUtils.getField(marketManagerService, "dpCache2013");



    // examine results
    
    ArrayList<Double> stateValues = dpCache2013.getStateValues();
    assertEquals("stateValues.size", 3, stateValues.size());
    double step0Val = stateValues.get(0); // balancing
    double step1Val = stateValues.get(1); // n+1 auction
    double step2Val = stateValues.get(2); // n+2 auction
    //
    // expected results:
    //
    // (-25-25-20) / (-1mwh -1mwh -2mwh) = -17.5
    assertEquals("step0 - balancing", -17.5, step0Val, 1e-6); 
    //
    // 1/4 was cleared for 10, 1/4 for 15, 1/2 for 20
    //(*)action -10: 1/4 x -10 + 3/4 x V(s_t+1) = 1/4 x -10 + 3/4 x -17.5 = -15.625
    //   action -15: 1/2 x -15 + 1/2 x V(s_t+1) = 1/2 x -15 + 1/2 x -17.5 = -16.25
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20
    assertEquals("step1 - auction n+1", -15.625, step1Val, 1e-6);
    //
    // 1/3 was cleared for 10, 2/3 was cleared for 20
    //(*)action -10: 1/3 x -10 + 2/3 x V(s_t+1) = 1/3 x -10 + 2/3 x -15.625 = -13.75
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20 
    assertEquals("step2 - auction n+2", -13.75, step2Val, 1e-6);
    
    
    ArrayList<Double> bestActions = dpCache2013.getBestActions();
    assertEquals("bestActions.size", 3, bestActions.size());
    assertEquals("bestActions(n+1)", -10, bestActions.get(1), 1e-6);
    assertEquals("getBestAction(n+1)", -10, dpCache2013.getBestAction(1), 1e-6);
    assertEquals("bestActions(n+2)", -10, bestActions.get(2), 1e-6);
    assertEquals("getBestAction(n+2)", -10, dpCache2013.getBestAction(2), 1e-6);
    
    assertEquals("current ts - valid", true, dpCache2013.isValid(currentTimeslotIndex));
    assertEquals("next ts - not valid", false, dpCache2013.isValid(currentTimeslotIndex + 1));
    marketManagerService.runDP2013(neededMwh, currentTimeslotIndex + 1);
    assertEquals("current ts - not valid", false, dpCache2013.isValid(currentTimeslotIndex));
    assertEquals("next ts - valid", true, dpCache2013.isValid(currentTimeslotIndex + 1));
  }

  /**
   * Test
   */
  @Test
  public void test_RunDP2014() {
    
    ///////////////////
    // SETUP
    ///////////////////
    
    // make sure all fields are in place
    marketManagerService.initialize(brokerContext);
    
    
    ClearedTrade trade;
    BalancingTransaction balanceTx;
    // easy initialization, but not consistent with ts=363 
    Instant now = new Instant(null); 
    Instant tradeCreationTime = now.plus(364 * TimeService.HOUR); // this probably doesn't work but what we care about is the next line    
    

    // initialize with transactions from ts 364

    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(364);
    // 15/Mwh for n-1
    trade = new ClearedTrade(364, 1, 15, tradeCreationTime); 
    //                       ts  mwh charge
    marketManagerService.handleMessage(trade);

    // 20/Mwh for n-2
    trade = new ClearedTrade(365, 1, 20, tradeCreationTime); 
    //                       ts  mwh charge

    marketManagerService.handleMessage(trade);
    //0.025/Kwh => 25/Mwh for balancing
    balanceTx  = new BalancingTransaction(thebroker, 364, -1000, -25); 
    //                                               ts    kwh  charge
    marketManagerService.handleMessage(balanceTx);



    // initialize with transactions from ts 365 

    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(365);
    // 15/Mwh for n-1
    trade = new ClearedTrade(365, 1, 10, tradeCreationTime); 
    //                       ts  mwh charge
    marketManagerService.handleMessage(trade);

    // 20/Mwh for n-2
    trade = new ClearedTrade(366, 1, 20, tradeCreationTime); 
    //                       ts  mwh charge

    marketManagerService.handleMessage(trade);
    //0.025/Kwh => 25/Mwh for balancing
    balanceTx  = new BalancingTransaction(thebroker, 365, -1000, -25); 
    //                                               ts    kwh  charge
    marketManagerService.handleMessage(balanceTx);



    // initialize with transactions from ts 366 

    when(timeslotRepo.getTimeslotIndex(tradeCreationTime)).thenReturn(366);
    // 15/Mwh for n-1
    trade = new ClearedTrade(366, 2, 20, tradeCreationTime); // TODO still 10 but should fail test since doesn't do a weighted avg
    //                       ts  mwh charge
    marketManagerService.handleMessage(trade);

    // 20/Mwh for n-2
    trade = new ClearedTrade(367, 1, 10, tradeCreationTime); 
    //                       ts  mwh charge

    marketManagerService.handleMessage(trade);
    //0.025/Kwh => 25/Mwh for balancing
    balanceTx  = new BalancingTransaction(thebroker, 366, -2000, -20); // -10
    //                                               ts    kwh  charge
    marketManagerService.handleMessage(balanceTx);



    ///////////////////
    // TEST DP RESULTS
    ///////////////////

    String msgSuffix = " no orderbooks";

    // To remind: cleared trades data (price, mwh) is
    // state n-1: (10, 1), (15, 1), (20, 2)
    // state n-2: (10, 1), (20, 1), (20, 1)
    
    // DP for action from state 1
    double neededMwh = 10; // not used
    int currentTimeslotIndex = 369; // should be after above tradeCreationTime's
    int targetTimeslotIndex = 370;
    DPResult dpResult = marketManagerService.runDP2014(targetTimeslotIndex, neededMwh, currentTimeslotIndex);
    //
    // examine results - state values
    //
    ArrayList<Double> stateValues = dpResult.getStateValues();
    assertNotNull("stateValues not null", stateValues);
    assertEquals("stateValues.size", 2, stateValues.size());
    double V_0 = stateValues.get(0); // balancing
    double V_1 = stateValues.get(1); // n-1 auction
    //
    // state 0:
    // (-25-25-20) / (-1mwh -1mwh -2mwh) = -17.5
    assertEquals("A(1): V(0) - balancing" + msgSuffix, -17.5, V_0, 1e-6); 
    //
    // state 1:
    // 1/4 was cleared for 10, 1/4 for 15, 1/2 for 20
    //   action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(*)action -10: 1/4 x -10 + 3/4 x V(s_t+1) = 1/4 x -10 + 3/4 x -17.5 = -15.625
    //   action -15: 1/2 x -15 + 1/2 x V(s_t+1) = 1/2 x -15 + 1/2 x -17.5 = -16.25
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20
    assertEquals("A(1): V(1) - auction n-1" + msgSuffix, -15.625, V_1, 1e-6);
    //
    // examine results - action values
    //
    ArrayList<Double> bestActions = dpResult.getBestActions();
    assertEquals("A(1): bestActions.size" + msgSuffix, 2, bestActions.size());
    assertEquals("A(1): bestActions(n-1)" + msgSuffix, -10, bestActions.get(1), 1e-6);

    // DP for action from state 2
    neededMwh = 10; // not used
    currentTimeslotIndex = 369; // should be after above tradeCreationTime's
    targetTimeslotIndex = 371;
    dpResult = marketManagerService.runDP2014(targetTimeslotIndex, neededMwh, currentTimeslotIndex);
    //
    // examine results - state values
    //
    stateValues = dpResult.getStateValues();
    assertNotNull("stateValues not null", stateValues);
    assertEquals("stateValues.size", 3, stateValues.size());
    V_0 = stateValues.get(0); // balancing
    V_1 = stateValues.get(1); // n-1 auction
    double V_2 = stateValues.get(2); // n-2 auction
    //
    // expected results:
    //
    // state 0:
    // (-25-25-20) / (-1mwh -1mwh -2mwh) = -17.5
    assertEquals("A(2): V(0) - balancing" + msgSuffix, -17.5, V_0, 1e-6); 
    //
    // state 1:
    // 1/4 was cleared for 10, 1/4 for 15, 1/2 for 20
    //   action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(*)action -10: 1/4 x -10 + 3/4 x V(s_t+1) = 1/4 x -10 + 3/4 x -17.5 = -15.625
    //   action -15: 1/2 x -15 + 1/2 x V(s_t+1) = 1/2 x -15 + 1/2 x -17.5 = -16.25
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20
    assertEquals("A(2): V(1) - auction n-1" + msgSuffix, -15.625, V_1, 1e-6);
    //
    // state 2:
    // 1/3 was cleared for 10, 2/3 was cleared for 20
    //   action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -15.625 = -15.625
    //(*)action -10: 1/3 x -10 + 2/3 x V(s_t+1) = 1/3 x -10 + 2/3 x -15.625 = -13.75
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20 
    assertEquals("A(2): V(2) - auction n-2" + msgSuffix, -13.75, V_2, 1e-6);
    //
    // examine results - action values
    //
    bestActions = dpResult.getBestActions();
    assertEquals("bestActions.size", 3, bestActions.size());
    assertEquals("bestActions(n-1)", -10, bestActions.get(1), 1e-6);
    assertEquals("bestActions(n-2)", -10, bestActions.get(2), 1e-6);



    // test with orderbooks 
    msgSuffix = " with orderbooks";

    // To remind: cleared trades data (price, mwh) is
    // state n-1: (10, 1), (15, 1), (20, 2)
    // state n-2: (10, 1), (20, 1), (20, 1)



    // DP for action from state 1
    neededMwh = 10; // not used
    currentTimeslotIndex = 369; // should be after above tradeCreationTime's
    targetTimeslotIndex = 370;
    // order book for 370
    Orderbook o1 = new Orderbook(370, null, null); 
    o1.addAsk(new OrderbookOrder(-1.0, 14.0)); 
    marketManagerService.handleMessage(o1);
    //
    dpResult = marketManagerService.runDP2014(targetTimeslotIndex, neededMwh, currentTimeslotIndex);
    //
    // examine results - state values
    //
    stateValues = dpResult.getStateValues();
    assertNotNull("stateValues not null", stateValues);
    assertEquals("stateValues.size", 2, stateValues.size());
    V_0 = stateValues.get(0); // balancing
    V_1 = stateValues.get(1); // n-1 auction
    //
    // state 0:
    // (-25-25-20) / (-1mwh -1mwh -2mwh) = -17.5
    assertEquals("A(1): V(0) - balancing" + msgSuffix, -17.5, V_0, 1e-6); 
    //
    // state 1:
    // 1/3 was cleared for 15, 2/3 for 20
    //   action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(x)action -10: 
    //(*)action -15: 1/3 x -15 + 2/3 x V(s_t+1) = 1/3 x -15 + 2/3 x -17.5 = -16.6666666
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20
    assertEquals("A(1): V(1) - auction n-1" + msgSuffix, -16.666666, V_1, 1e-6);
    //
    // examine results - action values
    //
    bestActions = dpResult.getBestActions();
    assertEquals("bestActions.size", 2, bestActions.size());
    assertEquals("bestActions(n-1)", -15, bestActions.get(1), 1e-6);


    // DP for action from state 2
    neededMwh = 10; // not used
    currentTimeslotIndex = 369; // should be after above tradeCreationTime's
    targetTimeslotIndex = 371;
    // order book for 371
    Orderbook o2 = new Orderbook(371, null, null); 
    o2.addAsk(new OrderbookOrder(-1.0, 19.0)); 
    marketManagerService.handleMessage(o2);
    //
    dpResult = marketManagerService.runDP2014(targetTimeslotIndex, neededMwh, currentTimeslotIndex);
    //
    // examine results - state values
    //
    stateValues = dpResult.getStateValues();
    assertNotNull("stateValues not null", stateValues);
    assertEquals("stateValues.size", 3, stateValues.size());
    V_0 = stateValues.get(0); // balancing
    V_1 = stateValues.get(1); // n-1 auction
    V_2 = stateValues.get(2); // n-2 auction
    //
    // expected results:
    //
    // state 0:
    // (-25-25-20) / (-1mwh -1mwh -2mwh) = -17.5
    assertEquals("A(2): V(0) - balancing" + msgSuffix, -17.5, V_0, 1e-6); 
    //
    // state 1:
    // all cleared for 20
    //(*)action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(x)action -10:
    //(x)action -15: 
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20
    assertEquals("A(2): V(1) - auction n-1" + msgSuffix, -17.5, V_1, 1e-6);
    //
    // state 2:
    // all cleared for 20
    //(*)action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(x)action -10:
    //   action -20: 1   x -20 + 0   x V(s_t+1) = -20 
    assertEquals("A(2): V(2) - auction n-2" + msgSuffix, -17.5, V_2, 1e-6);
    //
    // examine results - action values
    //
    bestActions = dpResult.getBestActions();
    assertEquals("bestActions.size", 3, bestActions.size());
    assertEquals("bestActions(n-1)", -0.0, bestActions.get(1), 1e-6);
    assertEquals("bestActions(n-2)", -0.0, bestActions.get(2), 1e-6);



    // test with orderbooks that eliminate all
    msgSuffix = " orderbooks eliminate all";

    // To remind: cleared trades data (price, mwh) is
    // state n-1: (10, 1), (15, 1), (20, 2)
    // state n-2: (10, 1), (20, 1), (20, 1)


    // DP for action from state 2 - orderbook eliminates all
    neededMwh = 10; // not used
    currentTimeslotIndex = 370; // should be after above tradeCreationTime's
    targetTimeslotIndex = 372;
    // order book for 372
    Orderbook o3 = new Orderbook(372, null, null); 
    o3.addAsk(new OrderbookOrder(-1.0, 100.0)); 
    marketManagerService.handleMessage(o3);
    //
    dpResult = marketManagerService.runDP2014(targetTimeslotIndex, neededMwh, currentTimeslotIndex);
    //
    // examine results - state values
    //
    stateValues = dpResult.getStateValues();
    assertNotNull("stateValues not null", stateValues);
    assertEquals("stateValues.size", 3, stateValues.size());
    V_0 = stateValues.get(0); // balancing
    V_1 = stateValues.get(1); // n-1 auction
    V_2 = stateValues.get(2); // n-2 auction
    //
    // expected results:
    //
    // state 0:
    // (-25-25-20) / (-1mwh -1mwh -2mwh) = -17.5
    assertEquals("A(2): V(0) - balancing" + msgSuffix, -17.5, V_0, 1e-6); 
    //
    // state 1:
    // all cleared for 20
    //(*)action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(x)action -10:
    //(x)action -15: 
    //(x)action -20: 
    assertEquals("A(2): V(1) - auction n-1" + msgSuffix, -17.5, V_1, 1e-6);
    //
    // state 2:
    // all cleared for 20
    //(*)action   0: 0 x 0 + 1 x V(s_t+1) = 0 + 1 x -17.5 = -17.5
    //(x)action -10:
    //(x)action -20: 
    assertEquals("A(2): V(2) - auction n-2" + msgSuffix, -17.5, V_2, 1e-6);
    //
    // examine results - action values
    //
    bestActions = dpResult.getBestActions();
    assertEquals("bestActions.size", 3, bestActions.size());
    assertEquals("bestActions(n-1)", -0.0, bestActions.get(1), 1e-6);
    assertEquals("bestActions(n-2)", -0.0, bestActions.get(2), 1e-6);


  }

  /**
   * Test
   */
  @Test
  public void test_meanClearingPrice() {
    ArrayList<PriceMwhPair> clearings = new ArrayList<PriceMwhPair>();
    clearings.add(new PriceMwhPair(1.0, 123));
    clearings.add(new PriceMwhPair(2.0, 234));
    clearings.add(new PriceMwhPair(3.0, 345));
    assertEquals("meanClearingBidPrice", 2.0, marketManagerService.meanClearingBidPrice(clearings), 1e-6);
  }
  
  /**
   * Test
   */
  @Test
  public void test_timeslotIsEnabled() {
    assertFalse("currentTimeslot is not enabled", 
        marketManagerService.timeslotIsEnabled(enabledTimeslots, currentTimeslot.getSerialNumber()));
    
    Timeslot t364 = new Timeslot(364, null);
    assertTrue("timeslot 364 is not enabled",
        marketManagerService.timeslotIsEnabled(enabledTimeslots, t364.getSerialNumber()));
    
    Timeslot t365 = new Timeslot(365, null);
    assertTrue("timeslot 365 is enabled",
        marketManagerService.timeslotIsEnabled(enabledTimeslots, t365.getSerialNumber()));
    
    Timeslot t366 = new Timeslot(366, null);
    assertFalse("timeslot 366 is enabled",
        marketManagerService.timeslotIsEnabled(enabledTimeslots, t366.getSerialNumber()));
  }
}

