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
import java.util.Map.Entry;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TariffTransaction.Type;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.Activatable;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.EnergyPredictionManager;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.interfaces.PortfolioManager;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherForecastRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherReportRepo;
import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

/**
 * Handles customer energy predictions 
 * @author urieli
 */
@Service
public class EnergyPredictionManagerService
implements Initializable, Activatable, EnergyPredictionManager
{

  static private Logger log = Logger.getLogger(EnergyPredictionManagerService.class);
  
  @Autowired
  private CustomerRepo customerRepo;
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private PortfolioManager portfolioManager;
  
  @Autowired
  private ServerBasedWeatherReportRepo weatherReportRepo; 

  @Autowired
  private ServerBasedWeatherForecastRepo weatherForecastRepo;

  @Autowired
  private ConfiguratorFactoryService configuratorFactoryService;


  private BrokerContext broker;

  private HashMap<Integer, HashMap<TariffSpecification, HashMap<CustomerInfo, ShiftedEnergyData>>> ts2shiftedEnergyPredictions;

  
  public EnergyPredictionManagerService() {
    super();
  }
  
  /**
   * Sets up message handling
   */
  @SuppressWarnings("unchecked")
  @Override
  public void initialize(BrokerContext broker) {


    // NEVER CALL ANY SERVICE METHOD FROM HERE, SINCE THEY ARE NOT GUARANTEED
    // TO BE initalize()'d. 
    // Exception: it is OK to call configuratorFactory's public
    // (application-wide) constants


    
    this.broker = broker;
    ts2shiftedEnergyPredictions = new HashMap<Integer, HashMap<TariffSpecification,HashMap<CustomerInfo,ShiftedEnergyData>>>();

  }
  

  /**
   * Handles WeatherReport by adding it to a list of weather reports
   */
  public synchronized void handleMessage (WeatherReport report)
  {
      weatherReportRepo.add(report);
  }

  /**
   * Handles WeatherForecast by adding it to a list of weather forecasts
   */
  public synchronized void handleMessage (WeatherForecast forecast)
  {
    weatherForecastRepo.add(forecast);
  }


  @Override
  public synchronized void activate(int timeslot) {
    try {

      log.info("activate");

    } catch (Throwable e) {
      log.error("caught exception from activate(): ", e);      
    }    
  }


  @Override
  public ArrayRealVector getPredictionForAbout7Days(CustomerInfo customerInfo, boolean customerPerspective, int currentTimeslot, boolean fixed) {

    // portfolioManager returns predictions from the broker's
    // perspective (producer has kwh > 0, consumer has kwh < 0)
    // so to view it from customer perspective we multiply by -1
    int sign = customerPerspective ? -1 : 1; 
    
    // TODO this is a temporary place holder - idealy this class
    // won't need the portfolioManager to get its prediction
    RealVector energy = portfolioManager.getGeneralRawUsageForCustomer(customerInfo, fixed).mapMultiply(sign);
    
    // sanity check
    if (energy.getDimension() != 7 * 24) {
      log.error("Expecting energy dimension to be 7 * 24 - unexpected behavior might happen");
    }
    
    // rotate to start from current time
    return BrokerUtils.rotateWeeklyRecordAndAppendTillEndOfDay(energy, currentTimeslot);
  }

  /**
   * @param currentTimeslot 
   * @param fixed 
   */
  @Override
  public HashMap<CustomerInfo, ArrayRealVector> getAbout7dayPredictionForAllCustomers(boolean customerPerspective, int currentTimeslot, boolean fixed) {
    HashMap<CustomerInfo, ArrayRealVector> result = new HashMap<CustomerInfo, ArrayRealVector>();
    for (CustomerInfo customer : customerRepo.list()) {
      result.put(customer, getPredictionForAbout7Days(customer, customerPerspective, currentTimeslot, fixed));
    }
    return result;
  }

  /**
   * @param currentTimeslot 
   * @param fixed 
   */
  @Override
  public HashMap<CustomerInfo, ArrayRealVector> getAbout7dayPredictionForCustomersOfType(PowerType powerType, boolean useCanUse, boolean customerPerspective, int currentTimeslot, boolean fixed) {
    HashMap<CustomerInfo, ArrayRealVector> result = new HashMap<CustomerInfo, ArrayRealVector>();
    for (CustomerInfo customer : customerRepo.list()) {
      if ((customer.getPowerType() == powerType) ||
          (useCanUse && customer.getPowerType().canUse(powerType))) {
        result.put(customer, getPredictionForAbout7Days(customer, customerPerspective, currentTimeslot, fixed));
      }
    }
    return result;
  }

  @Override
  public double getShiftedUsageFromBrokerPerspective(TariffSpecification spec, CustomerInfo cust,
      int subscribedPopulation, int targetTimeslot, int currentTimeslot, HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> tariffSubscriptions) {
    ArrayRealVector shiftedEnergyRecord = getShiftedEnergyRecord(currentTimeslot, spec, cust, tariffSubscriptions);
    int    startTimeslot      = currentTimeslot + 1;
    int    recordIndex        = targetTimeslot - startTimeslot;
    double singleMemberEnergy = shiftedEnergyRecord.getEntry(recordIndex);
    double populationEnergy   = singleMemberEnergy * subscribedPopulation;
    return -populationEnergy; // '-' since need it from broker's perspective 
  }

  /**
   * get (compute if missing) energy prediction
   * for a customer under a certain tariff, starting
   * the next timeslot
   * @param currentTimeslot
   * @param spec
   * @param cust
   * @param tariffSubscriptions 
   * @return
   */
  ArrayRealVector getShiftedEnergyRecord(int currentTimeslot,
      TariffSpecification spec, CustomerInfo cust, 
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> tariffSubscriptions) {

    HashMap<TariffSpecification, HashMap<CustomerInfo, ShiftedEnergyData>> 
        shiftedEnergyPredictions = ts2shiftedEnergyPredictions.get(currentTimeslot);  
    if (null == shiftedEnergyPredictions) {
      // this means it's the first call for the current timeslot, 
      // clear current mappings (for older timeslots) and re-allocate
      log.debug("computing new predictions, currentTimeslot=" + currentTimeslot);
      ts2shiftedEnergyPredictions.clear();
      shiftedEnergyPredictions = computeShiftedEnergy(currentTimeslot, tariffSubscriptions);
      ts2shiftedEnergyPredictions.put(currentTimeslot, shiftedEnergyPredictions);
    }

    return shiftedEnergyPredictions.get(spec).get(cust).getShiftedEnergy();
  }

  HashMap<TariffSpecification,HashMap<CustomerInfo,ShiftedEnergyData>> computeShiftedEnergy(int currentTimeslot,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> tariffSubscriptions) {

    boolean customerPerspective = true;    
    HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy = 
        getAbout7dayPredictionForAllCustomers(customerPerspective, currentTimeslot,true /*false*/);
    
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
    customer2ShiftedEnergy = configuratorFactoryService.getShiftingPredictor().updateEstimatedEnergyWithShifting(
        customer2estimatedEnergy, 
        //dummySubscriptions,
        tariffSubscriptions,
        currentTimeslot);
    return BrokerUtils.revertKeyMapping(customer2ShiftedEnergy);
  }
}
