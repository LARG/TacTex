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
package edu.utexas.cs.tactex.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

/**
 * Different utilities used in by the broker.
 * 
 * @author urieli 
 */
public class BrokerUtils {
  
  // approximate number based on informal game inspection
  private static final double LOWEST_TRANSIENT_CONSUMPTION_RATE = -0.125;  
  

  /**
   * a pair holder or <price-per-mwh, mwh>
   * default access level for testing purposes 
   * 
   * @author urieli
   *
   */
  public static class PriceMwhPair implements Comparable<PriceMwhPair> {
  
    double price;
    double mwh;  
  
    /**
     * @param price
     * @param mwh
     */
    public PriceMwhPair(double price, double mwh) {
      super();
      this.price = price;
      this.mwh = mwh;
    }
    
  
    public double getPricePerMwh() {
      return price;
    }
  
    public double getMwh() {
      return mwh;
    }    
    
    public double getPricePerKwh() {
      return price / 1000.0;
    }
    
    public double getKwh() {
      return mwh * 1000.0;
    }

    public void addMwh(double addedMwh) {
	  this.mwh += addedMwh;
	}


	@Override
    public int compareTo(PriceMwhPair o) {
      if (this.price < o.price) return -1;
      if (this.price > o.price) return 1;
      return 0;
    }
  }

  
  public static class ShiftedEnergyData {
    
    ArrayRealVector shiftedEnergy;
    Double inconvenienceFactor;
    
    public ShiftedEnergyData(ArrayRealVector shiftedEnergy, Double inconvenienceFactor) {
      this.shiftedEnergy = shiftedEnergy;
      this.inconvenienceFactor = inconvenienceFactor;
    }
    
    public ArrayRealVector getShiftedEnergy() {
      return shiftedEnergy;
    }
    
    public Double getInconvenienceFactor() {
      return inconvenienceFactor;
    }
  }


  static private Logger log = Logger.getLogger(BrokerUtils.class);


  /**
   * Rotates a weekly record to start from the next timeslot
   * and append the last day to continue until the end of the day
   * 
   * @param energy
   * @param currentTimeslot
   * @return
   */
  public static ArrayRealVector rotateWeeklyRecordAndAppendTillEndOfDay(
      RealVector record, int currentTimeslot) {
    // sanity check
    if (record.getDimension() != 7 * 24) {
      log.error("record dimension is not 7*24, not sure if code robust to that?");
    }
    int predictionStartWeeklyHour = (currentTimeslot + 1) % (record.getDimension());    
    ArrayRealVector copy = new ArrayRealVector(record.getDimension());
    // rotate to start from current moment
    RealVector slice1 = record.getSubVector(predictionStartWeeklyHour, record.getDimension() - predictionStartWeeklyHour);
    RealVector slice2 = record.getSubVector(0, predictionStartWeeklyHour);
    copy.setSubVector(0, slice1);
    copy.setSubVector(slice1.getDimension(), slice2);
    return copy;
  }


  /**
   * Given a mapping from TariffSpecification to subscribed customers
   * compute the total number of customers
   */
  public static int getNumIndividualCustomers(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Integer>> tariffSubscriptions) {

    int total = 0;
    for(HashMap<CustomerInfo, Integer> subscriptions : tariffSubscriptions.values()) {
      for(Integer numCustomers : subscriptions.values()) {
        total += numCustomers;
      }
    }
    return total;
  }


  /**
   * changes a mapping keyType1->keyType2->value to
   * keyType2->keyType1->value
   * 
   * @param tariffSubscriptions
   * @return
   */
  public static <K1, K2, V> HashMap<K2, HashMap<K1, V>> revertKeyMapping(
      HashMap<K1, HashMap<K2, V>> twoLevelMap) {

    HashMap<K2, HashMap<K1, V>> result = new HashMap<K2, HashMap<K1, V>>();
    for(Entry<K1, HashMap<K2, V>> entry : twoLevelMap.entrySet()) {
      K1 key1 = entry.getKey();
      HashMap<K2, V> keyvals = entry.getValue();
      for(Entry<K2, V> e : keyvals.entrySet()) {
        K2 key2 = e.getKey();
        V value = e.getValue();

        HashMap<K1,V> current = result.get(key2);
        if (current == null) {
          current = new HashMap<K1,V>();
          result.put(key2, current);
        }
        current.put(key1, value);
      }
    }
    return result;
  }
  

  /**
   * creates a copy of the map, but where keys are Double
   * instead of Integer. 
   * 
   * @param customer2tariffSubscriptions
   * @return
   * 
   */
  public static HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> initializePredictedFromCurrentSubscriptions(
      HashMap<CustomerInfo, HashMap<TariffSpecification, Integer>> customer2tariffSubscriptions) {
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> 
        predicted = 
            new HashMap<CustomerInfo, HashMap<TariffSpecification,Double>>();
    for (CustomerInfo customer : customer2tariffSubscriptions.keySet()){
      HashMap<TariffSpecification, Integer> oldmap = customer2tariffSubscriptions.get(customer);
      HashMap<TariffSpecification, Double> newmap = initializeDoubleSubsMap(oldmap);
      // copy construct
      predicted.put(customer, newmap); 
    }
    return predicted;
  }


  public static HashMap<TariffSpecification, Double> initializeDoubleSubsMap(
      HashMap<TariffSpecification, Integer> oldmap) {
    HashMap<TariffSpecification, Double> newmap = new HashMap<TariffSpecification, Double>();
    for ( Entry<TariffSpecification, Integer> entry : oldmap.entrySet() ) {
      TariffSpecification key = entry.getKey();
      double value = entry.getValue().doubleValue();
      newmap.put(key, value);
    }
    return newmap;
  }


  /**
   * @param list
   * @param value
   */
  public static <T> void insertToSortedArrayList(ArrayList<T> list, T value) {
    list.add(value);
    @SuppressWarnings("unchecked")
    Comparable<T> cmp = (Comparable<T>) value;
    for (int i = list.size()-1; i > 0 && cmp.compareTo(list.get(i-1)) < 0; i--)
      Collections.swap(list, i, i-1);
  }

  
  /**
   * sort map entries by value an return them in a sorted list
   * @param map
   * @return
   */
  public static <K, V extends Comparable> List<Entry<K, V>> sortByValues(Map<K,V> map){
    // create an array from the entries
    List<Map.Entry<K,V>> entries = new ArrayList<Map.Entry<K,V>>(map.entrySet()); 
    // sort entries by value, assuming value is Comparable
    Collections.sort(entries, new Comparator<Map.Entry<K,V>>() { 
      @Override
      public int compare(Entry<K, V> o1, Entry<K, V> o2) {
        return o1.getValue().compareTo(o2.getValue());
      }
    }); 
    return entries;  
  }  


  /**
   * 
   * @param subscriptions
   * @return
   */
  public static <K> Integer sumMapValues(Map<K, Integer> map) {
    Integer totalSubs = 0;
    for (Integer subs : map.values()) {
      totalSubs += subs;      
    }
    return totalSubs;
  }

  
  public static <K> Double sumMapValues(HashMap<K, Double> hashMap) {
    Double totalSubs = 0.0;
    for (Double subs : hashMap.values()) {
      totalSubs += subs;      
    }
    return totalSubs;
  }

  
  // -------- Tariff related utilities -------
  public static TariffSpecification getDefaultSpec(List<TariffSpecification> competingTariffs, PowerType powerType) {
    for (TariffSpecification spec : competingTariffs) {
      if (spec.getBroker().getUsername().equals("default broker") &&
          powerType.canUse(spec.getPowerType())) {
        return spec;
      }
    }
    log.error("didn't find default spec in competing tariffs");
    return null; 
  }


  public static int extractPredictionRecordLength(
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> result) {

    Collection<HashMap<TariffSpecification, ShiftedEnergyData>> listOfHashTrf2Engy = result.values();
    if (listOfHashTrf2Engy == null || listOfHashTrf2Engy.size() == 0) {
      log.warn("empty list of mappings of tariff=>energy");
      return 0;
    }

    HashMap<TariffSpecification, ShiftedEnergyData> someHashTrf2Engy = listOfHashTrf2Engy.iterator().next();
    if (someHashTrf2Engy == null || someHashTrf2Engy.size() == 0) {
      log.warn("empty mapping of tariff=>energy");
      return 0;
    }
    return someHashTrf2Engy.values().iterator().next().getShiftedEnergy().getDimension();
  }

  
  public static <K1, K2, V> void print2LevelMap(
      HashMap<K1, HashMap<K2, V>> twoLevelMap) {
    for(Entry<K1, HashMap<K2, V>> entry : twoLevelMap.entrySet()) {
      K1 key1 = entry.getKey();
      log.info("K1: " + key1.toString());
      HashMap<K2, V> keyvals = entry.getValue();
      for(Entry<K2, V> e : keyvals.entrySet()) {
        K2 key2 = e.getKey();
        V value = e.getValue();
        log.info("K2, V: " + key2.toString() + ", " + value.toString());
      }
    }
    
  }


  /**
   * Get number of actual brokers (excluding default broker)
   * @return
   */
  public static int getNumberOfBrokers() {
    // default value, in case something goes wrong
    int numberOfActualBrokers = 8; 
    try {
      
      // -1 to exclude the default broker:
      numberOfActualBrokers = Competition.currentCompetition().getBrokers().size() - 1;
      
    } catch (Throwable e) {
      log.error("caught exception while getting #brokers, assuming " + numberOfActualBrokers, e);
    }
    
    log.debug("getNumberOfBrokers(): " + numberOfActualBrokers);
    return numberOfActualBrokers; 
  }


  public static boolean isGameInTransientPhase(
      List<TariffSpecification> competingTariffs, PowerType powerType) {
    // Currently we only change behavior for CONSUMPTION tariffs, i.e.
    // assuming we are in 'equilibrium/best-response' mode for non-consumption
    // tariffs
    if (powerType != PowerType.CONSUMPTION)
      return false; 

    // this code assumes CONSUMPTION (e.g. LOWEST_TRANSIENT_CONSUMPTION_RATE)
    double bestRate = -Double.MAX_VALUE;
    for (TariffSpecification competingSpec : competingTariffs) {
      if (competingSpec.getPowerType().canUse(powerType)) {
        // Assumption: using only first rate, assuming fixed-rate
        double competingRate = competingSpec.getRates().get(0).getValue();
        if (competingRate > bestRate) {
          bestRate = competingRate;
        }
      }
    }
    return -Double.MAX_VALUE < bestRate && bestRate < LOWEST_TRANSIENT_CONSUMPTION_RATE && bestRate < 0;
  }
}
