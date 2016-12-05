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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.ArrayComparisonFailure;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.TariffRepo;

import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.PriceMwhPair;

/**
 * 
 * @author urieli
 *
 */
public class BrokerUtilsTest {
  

  @Before
  public void setUp () throws Exception
  {
    
  }
  

  /**
   * Test
   */
  @Test
  public void test_PriceMwhPair() {
    PriceMwhPair pair1 = new PriceMwhPair(1.0, -1.0);
    PriceMwhPair pair2 = new PriceMwhPair(1.0, +1.0);
    PriceMwhPair pair3 = new PriceMwhPair(2.0, -1.0);
    assertEquals("getPrice()", 1.0, pair1.getPricePerMwh(), 1e-6);
    assertEquals("getMwh()", -1.0, pair1.getMwh(), 1e-6);
    assertEquals("same key", 0, pair1.compareTo(pair2));
    assertEquals("same key", 0, pair2.compareTo(pair1));
    assertEquals("smaller", -1, pair1.compareTo(pair3));
    assertEquals("smaller", -1, pair2.compareTo(pair3));
    assertEquals("larger", 1, pair3.compareTo(pair1));
    assertEquals("larger", 1, pair3.compareTo(pair2));
  }
  

  @Test
  public void test_rotateWeeklyRecordAndAppendTillEndOfDay() {
    
    // this test is very similar and is based on 
    // EnergyPredictionTest.testEnergyPredictionOfAboutOneWeek()
    // we moved it here after refactoring a method to BrokersUtils
    //
    // initialize an record vector where the value of an 
    // entry is its index
    ArrayRealVector record = new ArrayRealVector(7*24);
    for (int i = 0; i < record.getDimension(); ++i) {
      record.setEntry(i, i);      
    } 
   
    int currentTimeslot;
    ArrayRealVector expected;
    ArrayRealVector actual;

    currentTimeslot = 7*24 - 1;
    expected = new ArrayRealVector(record);
    actual = BrokerUtils.rotateWeeklyRecordAndAppendTillEndOfDay(record, currentTimeslot);
    assertArrayEquals("no rotation at the beginning of week", expected.toArray(), actual.toArray(), 1e-6);

    currentTimeslot = 1 * 24 - 1;
    // actual
    actual = BrokerUtils.rotateWeeklyRecordAndAppendTillEndOfDay(record, currentTimeslot);
    // prepare expected
    RealVector slice1 = record.getSubVector(1 * 24, 7 * 24 - 24);
    expected.setSubVector(0, slice1);
    expected.setSubVector(slice1.getDimension(), record.getSubVector(0, 24));
    assertArrayEquals("end of first day results in days 2,...,7,1", expected.toArray(), actual.toArray(), 1e-6);

    currentTimeslot = 6 * 24 - 1;
    // actual
    actual = BrokerUtils.rotateWeeklyRecordAndAppendTillEndOfDay(record, currentTimeslot);
    // prepare expected
    slice1 = record.getSubVector(6 * 24, 7 * 24 - 6 * 24);
    expected.setSubVector(0, slice1);
    expected.setSubVector(slice1.getDimension(), record.getSubVector(0, 6 * 24));
    assertArrayEquals("end of 6th day results in days 7,1,...,6", expected.toArray(), actual.toArray(), 1e-6);

    currentTimeslot = 0;
    // predict
    actual = BrokerUtils.rotateWeeklyRecordAndAppendTillEndOfDay(record, currentTimeslot);
    // prepare expected
    slice1 = record.getSubVector(1, 7 * 24 - 1);
    expected.setSubVector(0, slice1);
    expected.setSubVector(slice1.getDimension(), record.getSubVector(0, 1));
    expected.append(record.getSubVector(1, 24 - 1));
    assertArrayEquals("if not at day start, appending until the end of day", expected.toArray(), actual.toArray(), 1e-6);
  }

  
  @Test
  public void test_getNumIndividualCustomers() {
    HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions = 
        new HashMap<TariffSpecification, HashMap<CustomerInfo,Integer>>();
    
    int numCustomers;
    int expected; 
    
    numCustomers = BrokerUtils.getNumIndividualCustomers(tariffSubscriptions);
    expected = 0;
    assertEquals("no tariffs", expected , numCustomers);
    
    TariffSpecification spec1 = new TariffSpecification(null, null);
    tariffSubscriptions.put(spec1, new HashMap<CustomerInfo, Integer>());
    numCustomers = BrokerUtils.getNumIndividualCustomers(tariffSubscriptions);
    expected = 0;
    assertEquals("tariff with no customers", expected, numCustomers);
    
    CustomerInfo customer11 = new CustomerInfo("customer11", 1);
    tariffSubscriptions.get(spec1).put(customer11, 1);
    numCustomers = BrokerUtils.getNumIndividualCustomers(tariffSubscriptions);
    expected = 1;
    assertEquals("one customer with population=1", expected, numCustomers);
    
    CustomerInfo customer12 = new CustomerInfo("customer12", 1);
    tariffSubscriptions.get(spec1).put(customer12, 1);
    numCustomers = BrokerUtils.getNumIndividualCustomers(tariffSubscriptions);
    expected = 2;
    assertEquals("two customers, each with population=1", expected, numCustomers);
    
    TariffSpecification spec2 = new TariffSpecification(null, null);
    tariffSubscriptions.put(spec2, new HashMap<CustomerInfo, Integer>());
    numCustomers = BrokerUtils.getNumIndividualCustomers(tariffSubscriptions);
    expected = 2;
    assertEquals("added tariff with no customers", expected, numCustomers);
    
    CustomerInfo customer21 = new CustomerInfo("customer21", 1);
    tariffSubscriptions.get(spec1).put(customer21, 1);
    numCustomers = BrokerUtils.getNumIndividualCustomers(tariffSubscriptions);
    expected = 3;
    assertEquals("added customer to new tariff with population=1", expected, numCustomers);
  }
  

  @Test
  public void test_revertKeyMapping() {
    HashMap<String, HashMap<String, String>> twoLevelMap = 
        new HashMap<String, HashMap<String,String>>();
    
    HashMap<String, HashMap<String, String>> expectedMap = 
        new HashMap<String, HashMap<String,String>>();
    
    myAssertMapsEqual("empty map", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));
        
    twoLevelMap.put("am", new HashMap<String, String>());
    myAssertMapsEqual("key1 added with no key2 - should be ignored", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));    
        
    twoLevelMap.get("am").put("I", "mapped");
    expectedMap.put("I", new HashMap<String, String>());
    expectedMap.get("I").put("am", "mapped");
    myAssertMapsEqual("one item added", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));
    
    twoLevelMap.put("were", new HashMap<String, String>());
    twoLevelMap.get("were").put("I", "mapped");
    expectedMap.get("I").put("were", "mapped");
    myAssertMapsEqual("another item added with different key1 and same key2", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));
    
    twoLevelMap.get("were").put("we", "mapped");
    expectedMap.put("we", new HashMap<String, String>());
    expectedMap.get("we").put("were", "mapped");
    myAssertMapsEqual("another item added with same key1 and different key2", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));
    
    twoLevelMap.put("was", new HashMap<String, String>());
    myAssertMapsEqual("another key1 added with no key2", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));
    
    twoLevelMap.get("was").put("he", "mapped");
    expectedMap.put("he", new HashMap<String, String>());
    expectedMap.get("he").put("was", "mapped");
    myAssertMapsEqual("new key1 and key2 should be in the result", expectedMap, BrokerUtils.revertKeyMapping(twoLevelMap));    
  }
  

  @Test
  public void test_initializedPredictedFromCurrentPredictions() {
    HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> 
        oldmap = 
            new HashMap<CustomerInfo, HashMap<TariffSpecification,Integer>>();
    
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> newmap = 
        BrokerUtils.
            initializePredictedFromCurrentSubscriptions(
                oldmap);

    // empty map
    assertEquals("empty subscriptions", 0, newmap.size());    
    
    // 1 customer, no subscriptions
    CustomerInfo cust1 = new CustomerInfo("cust1", 10);
    oldmap.put(cust1, new HashMap<TariffSpecification, Integer>());
    newmap = 
        BrokerUtils.
            initializePredictedFromCurrentSubscriptions(
                oldmap);
    assertEquals("1 customer, no subscriptions, map size", 1, newmap.size());    
    assertEquals("1 customer, no subscriptions, subscriptions size", 0, newmap.get(cust1).size());
    
    Broker thebroker = new Broker("testBroker");
    // 1 customer, 1 tariff subscriptions
    TariffSpecification tariff1 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    oldmap.get(cust1).put(tariff1, 5);
    newmap = 
        BrokerUtils.
            initializePredictedFromCurrentSubscriptions(
                oldmap);
    assertEquals("1 customer, 1 subscription, map size", 1, newmap.size());    
    assertEquals("1 customer, 1 subscription, subscriptions size", 1, newmap.get(cust1).size());
    assertEquals("1 customer, 1 subscription, num subs", 5.0, newmap.get(cust1).get(tariff1), 1e-6);
    
    // 1 customer, 2 tariff subscriptions
    TariffSpecification tariff2 = new TariffSpecification(thebroker, PowerType.CONSUMPTION);
    oldmap.get(cust1).put(tariff2, 3);
    newmap = 
        BrokerUtils.
            initializePredictedFromCurrentSubscriptions(
                oldmap);
    assertEquals("1 customer, 2 subscription, map size", 1, newmap.size());    
    assertEquals("1 customer, 2 subscription, subscriptions size", 2, newmap.get(cust1).size());
    assertEquals("1 customer, 2 subscription, num subs tariff1", 5.0, newmap.get(cust1).get(tariff1), 1e-6);
    assertEquals("1 customer, 2 subscription, num subs tariff2", 3.0, newmap.get(cust1).get(tariff2), 1e-6);
    
    // 2 customer, 2 tariff subscriptions
    CustomerInfo cust2 = new CustomerInfo("cust2", 20);
    oldmap.put(cust2, new HashMap<TariffSpecification, Integer>());
    oldmap.get(cust2).put(tariff1, 15);
    oldmap.get(cust2).put(tariff2, 5);
    newmap = 
        BrokerUtils.
            initializePredictedFromCurrentSubscriptions(
                oldmap);
    assertEquals("2 customer, 2 subscription, map size", 2, newmap.size());    
    // missing? test in current line 
    assertEquals("2 customer, 2 subscription, cust1 subscriptions size", 2, newmap.get(cust1).size());
    assertEquals("2 customer, 2 subscription, cust1 num subs tariff1", 5.0, newmap.get(cust1).get(tariff1), 1e-6);
    assertEquals("2 customer, 2 subscription, cust1 num subs tariff2", 3.0, newmap.get(cust1).get(tariff2), 1e-6);
    assertEquals("2 customer, 2 subscription, cust2 subscriptions size", 2, newmap.get(cust2).size());
    assertEquals("2 customer, 2 subscription, cust2 num subs tariff1", 15.0, newmap.get(cust2).get(tariff1), 1e-6);
    assertEquals("2 customer, 2 subscription, cust2 num subs tariff2", 5.0, newmap.get(cust2).get(tariff2), 1e-6);
  }
    
    
  @Test
  public void test_addRemoveFromRepo() {
    
    TariffSpecification spec;
    TariffRepo tariffRepo;
    boolean success;
    Broker broker = new Broker("testbroker");

  }
  

  @Test
  public void test_insertIntoSortedArray() {
    
    // test with doubles
    ArrayList<Double> arr = new ArrayList<Double>();
    BrokerUtils.insertToSortedArrayList(arr, 3.0); // insert to empty
    BrokerUtils.insertToSortedArrayList(arr, 1.0); // insert smallest
    BrokerUtils.insertToSortedArrayList(arr, 5.0); // insert largest
    BrokerUtils.insertToSortedArrayList(arr, 2.0); // insert in the middle
    BrokerUtils.insertToSortedArrayList(arr, 4.0); // insert in the middle
    BrokerUtils.insertToSortedArrayList(arr, 0.0); // insert smallest
    BrokerUtils.insertToSortedArrayList(arr, 6.0); // insert largest
    assertEquals("Array length", 7, arr.size(), 1e-6);
    assertEquals("Array.get(0)", 0, arr.get(0), 1e-6);
    assertEquals("Array.get(1)", 1, arr.get(1), 1e-6);
    assertEquals("Array.get(2)", 2, arr.get(2), 1e-6);
    assertEquals("Array.get(3)", 3, arr.get(3), 1e-6);
    assertEquals("Array.get(4)", 4, arr.get(4), 1e-6);
    assertEquals("Array.get(5)", 5, arr.get(5), 1e-6);
    assertEquals("Array.get(6)", 6, arr.get(6), 1e-6);   

  }
  
  
  @Test
  public void test_sortByValues() {
    // test HashMap with Integer values
    Map<String, Integer> map1 = new HashMap<String, Integer>();
    map1.put("Should be 2nd", 1);
    map1.put("Should be 1st", 0);
    map1.put("Should be 3rd", 2);
    List<Entry<String,Integer>> sortedList1 = BrokerUtils.sortByValues(map1);
    assertEquals("sortedList1.get(0)", 0, sortedList1.get(0).getValue(), 1e-6);   
    assertEquals("sortedList1.get(1)", 1, sortedList1.get(1).getValue(), 1e-6);   
    assertEquals("sortedList1.get(2)", 2, sortedList1.get(2).getValue(), 1e-6);   
    
    // test TreeMap with Double values
    Map<String, Double> map2 = new TreeMap<String, Double>();
    map2.put("Should be 2nd", 1.0);
    map2.put("Should be 3rd", 2.0);
    map2.put("Should be 1st", 0.0);
    List<Entry<String,Double>> sortedList2 = BrokerUtils.sortByValues(map2);
    assertEquals("sortedList2.get(0)", 0.0, sortedList2.get(0).getValue(), 1e-6);   
    assertEquals("sortedList2.get(1)", 1.0, sortedList2.get(1).getValue(), 1e-6);   
    assertEquals("sortedList2.get(2)", 2.0, sortedList2.get(2).getValue(), 1e-6);   
  }
  

  @Test
  public void test_sumMapValues() {
    Map<String, Integer> map = new HashMap<String, Integer>();
    map.put("some key", 0);
    map.put("another key", 1);
    map.put("yet another key", 2);
    assertEquals("sum map values", 3, BrokerUtils.sumMapValues(map), 1e-6);   
  }


  /**
   * as assert function I added that compares hash maps
   * @param msg 
   * 
   * @param twoLevelMap
   * @param expectedMap
   * @throws ArrayComparisonFailure
   */ 
  private  <K1, K2, V> void myAssertMapsEqual(
      String msg, 
      HashMap<K1, HashMap<K2, V>> expectedMap, 
      HashMap<K1, HashMap<K2, V>> revertedTwoLevelMap
      )
      throws ArrayComparisonFailure {
    
    // compare key1
    assertTrue(msg, expectedMap.keySet().equals(revertedTwoLevelMap.keySet()));
    
    // for each key1, compare all <key2, value>s
    for (K1 key1 : expectedMap.keySet()) {
     
      assertTrue(msg + " comparing key2", expectedMap.get(key1).keySet().equals(revertedTwoLevelMap.get(key1).keySet()));

      for (K2 key2 : expectedMap.get(key1).keySet()) {
        assertEquals(msg + " comparing values", expectedMap.get(key1).get(key2), revertedTwoLevelMap.get(key1).get(key2));
      }
    }
  }
}
