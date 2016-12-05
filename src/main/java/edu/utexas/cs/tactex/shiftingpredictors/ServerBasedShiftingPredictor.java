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
package edu.utexas.cs.tactex.shiftingpredictors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TimeService;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;

import edu.utexas.cs.tactex.ConfiguratorFactoryService;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.servercustomers.common.TariffSubscription;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherForecastRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherReportRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.TariffSubscriptionRepo;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.FactoredCustomerService;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.CapacityBundle;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.FactoredCustomer;
import edu.utexas.cs.tactex.utils.BrokerUtils;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;

public class ServerBasedShiftingPredictor implements ShiftingPredictor {

  static private Logger log = Logger.getLogger(ServerBasedShiftingPredictor.class);

  private ShiftingPredictorNoShifts shiftingPredictorNoShifts;
  private ConfiguratorFactoryService configuratorFactoryService;
  private TariffRepoMgr tariffRepoMgr;
  
  private FactoredCustomerService factoredCustomerService;
  
  private HashMap<String, CustomerInfo> bundleCustInfos;
  private HashMap<String, CustomerInfo> brokerCustInfos;


  public ServerBasedShiftingPredictor(ShiftingPredictorNoShifts shiftingPredictorNoShifts, ConfiguratorFactoryService configuratorFactoryService, TariffRepoMgr tariffRepoMgr, CustomerRepo customerRepo, TimeslotRepo timeslotRepo, TimeService timeService, ServerBasedWeatherReportRepo weatherReportRepo, ServerBasedWeatherForecastRepo weatherForecastRepo) {
    this.shiftingPredictorNoShifts = shiftingPredictorNoShifts;
    this.configuratorFactoryService = configuratorFactoryService;
    this.tariffRepoMgr = tariffRepoMgr;
    factoredCustomerService = new FactoredCustomerService();
    factoredCustomerService.initialize(timeslotRepo, timeService, weatherReportRepo, weatherForecastRepo);
    // customerRepo should be initialized after the previous line
    this.bundleCustInfos = createCustomerMapping(factoredCustomerService.getCustomerRepo().list());
    // the following is needed since broker and factored-customer are using
    // different customerRepos (it was not possible unify them)
    this.brokerCustInfos = null; 
  }


  @Override
  public HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
  updateEstimatedEnergyWithShifting(
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy,
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedCustomerSubscriptions,
      int currentTimeslot) {

    // first, some preparations and support code
    
    // create cache mapping the first time we are here needed since broker and
    // factored-customer are using different customerRepos (was not possible to
    // unify them)
    if (null == brokerCustInfos) {
      brokerCustInfos = createCustomerMapping(customer2estimatedEnergy.keySet());
    }
    
    // temporarily add missing tariffs to repo
    HashSet<TariffSpecification> specsToRemove = new HashSet<TariffSpecification>();
    for (TariffSpecification spec : predictedCustomerSubscriptions.keySet()) {
      if (null == tariffRepoMgr.findTariffById(spec.getId())) {
        tariffRepoMgr.addToRepo(spec);
        specsToRemove.add(spec);
      }
    }

    
    
    // Next is the actual code
    
    // first create a default mapping
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        result = shiftingPredictorNoShifts.  
            updateEstimatedEnergyWithShifting(  
                customer2estimatedEnergy, 
                predictedCustomerSubscriptions, 
                currentTimeslot);
    
    try {
      int recordLength = BrokerUtils.extractPredictionRecordLength(result);
      
      // push energy estimations to customer - disabling it meaning generating
      // energy estimations using factored-customers' time-series code, which
      // uses weather, time and so on
      //fillCustomersWithEstimatedEnergy(customer2estimatedEnergy, currentTimeslot);
      
      // push subscriptions to customer
      fillRepoWithPredictedSubscriptions(predictedCustomerSubscriptions);
          
      // run shifting prediction
      factoredCustomerService.predictAndOverwriteWithShiftingAndElasticity(currentTimeslot);

      // override parts of result with shifted predictions
      try {
        updateResultWithShiftedEnergyPredictions(result, predictedCustomerSubscriptions, recordLength, currentTimeslot);
      } catch (Exception e) {
        log.error("caught exception from updateResultWithShiftedEnergyPredictions(): ", e);
      }

    } catch (Throwable e) {
      log.error("caught exception from updateEstimatedEnergyWithShifting(): ", e);
    }
    
    // cleanup
    tariffRepoMgr.removeTmpSpecsFromRepo((HashSet<TariffSpecification>) specsToRemove);
    
    return result;
  }


  private void fillRepoWithPredictedSubscriptions(
      HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedCustomerSubscriptions) {
    
    // clean
    factoredCustomerService.cleanSubscriptionRelatedData();

    TariffSubscriptionRepo tariffSubscriptionRepo = factoredCustomerService.getTariffSubscriptionRepo();
    // record all subscriptions
    for (Entry<TariffSpecification, HashMap<CustomerInfo, Double>> entry : predictedCustomerSubscriptions.entrySet()) {
      TariffSpecification spec = entry.getKey();
      Tariff tariff = tariffRepoMgr.findTariffById(spec.getId());
      HashMap<CustomerInfo, Double> cust2subs = entry.getValue();
      for (Entry<CustomerInfo, Double> custSub : cust2subs.entrySet()) {
        int count = (int)Math.round((double)custSub.getValue());
        CustomerInfo custInfo = bundleCustInfos.get(custSub.getKey().getName());
        TariffSubscription subscription = tariffSubscriptionRepo.getSubscription(custInfo, tariff);
        subscription.subscribe(count);
      }    
    }
  }


  private void fillCustomersWithEstimatedEnergy(
      HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy, int currentTimeslot) {
    List<FactoredCustomer> customers = factoredCustomerService.getCustomers();
    for (FactoredCustomer customer : customers) {
      // get all consumption bundles, extract CustomerInfo, ..., update energy record for bundle's originators (assuming there is one?)
      for (CapacityBundle bundle : customer.getCapacityBundlesOfTypeThatCanUse(PowerType.CONSUMPTION)) {
        // convert bundle to CustomerInfo, and energy record to whole population
        CustomerInfo custInfo = brokerCustInfos.get(bundle.getCustomerInfo().getName());
        RealVector populationEstimatedEnergy = customer2estimatedEnergy.get(custInfo).mapMultiply(bundle.getCustomerInfo().getPopulation());
        customer.updateEnergyRecord(bundle, populationEstimatedEnergy, currentTimeslot);
      }
    }
  }


  private void updateResultWithShiftedEnergyPredictions(
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> result, HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedCustomerSubscriptions, int recordLength, int currentTimeslot) throws Exception {

     // revert mappings
    HashMap<CustomerInfo, HashMap<TariffSpecification, Double>> 
        cust2spec2subs = 
            BrokerUtils.revertKeyMapping(predictedCustomerSubscriptions);

    // get all consumption bundles, extract CustomerInfo, ..., get energy
    // record from bundle's originators 
    
    // this just iterates over all CustomerInfo's
    for (FactoredCustomer customer : factoredCustomerService.getCustomers()) {
      for (CapacityBundle bundle : customer.getCapacityBundles()) {
        // convert bundle to CustomerInfo
        CustomerInfo custInfoBundle = bundle.getCustomerInfo();
        CustomerInfo custInfoBroker = brokerCustInfos.get(custInfoBundle.getName());
        // get specs to which customer subscribed
        HashMap<TariffSpecification, Double> spec2subs = cust2spec2subs.get(custInfoBroker);
        // sanity test
        if (null == spec2subs) {
          //throw new Exception("How come spec2subs is null for customer " + custInfoBundle.getName());
          
          // don't need prediction for a customer that is not in cust2spec2subs
          // since if the top-caller was PorftolioMgr.collectShiftedUsage() we 
          // only need it for currently subscribed customers, and if the caller
          // was utility-prediction, than it should already put all the
          // customers/tariffs for which it needs predictions
          continue;
        }
        
        // for each spec get predicted energy, which is the sum of all
        // CapacityOriginator's energy
        for (TariffSpecification spec : spec2subs.keySet()) {
          Tariff tariff = tariffRepoMgr.findTariffById(spec.getId());
          TariffSubscription subscription = 
              factoredCustomerService.getTariffSubscriptionRepo().getSubscription(custInfoBundle, tariff);
          ArrayRealVector bundlePredictedEnergy = bundle.getPredictedEnergy(subscription, recordLength, currentTimeslot);
          Double inconvenience = bundle.getShiftingInconvenienceFactor(subscription, recordLength);
          ShiftedEnergyData entry = new ShiftedEnergyData(bundlePredictedEnergy, inconvenience);
          putEnergyProfile(result, custInfoBroker, spec, entry);
        }
      }
    }
  }


  private void putEnergyProfile(
      HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        result, 
      CustomerInfo 
        custInfo, 
      TariffSpecification 
        spec, 
      ShiftedEnergyData 
        entry) {
    HashMap<TariffSpecification, ShiftedEnergyData> spec2energy = result.get(custInfo);
    if (null == spec2energy) {
      spec2energy = new HashMap<TariffSpecification, ShiftedEnergyData>(); 
      result.put(custInfo, spec2energy);
    }
    spec2energy.put(spec, entry);
  }


  private HashMap<String, CustomerInfo> createCustomerMapping(
      Collection<CustomerInfo> customers) {
    
    HashMap<String, CustomerInfo> result = new HashMap<String, CustomerInfo>();
    for (CustomerInfo cust : customers) {
      result.put(cust.getName(), cust);
    }
    return result;
  }

}
