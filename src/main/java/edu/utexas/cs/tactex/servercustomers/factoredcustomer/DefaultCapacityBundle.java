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
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *
 *     Copyright 2011-2013 the original author or authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an
 *     "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *     either express or implied. See the License for the specific language
 *     governing permissions and limitations under the License.
 */

package edu.utexas.cs.tactex.servercustomers.factoredcustomer;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import org.w3c.dom.*;

import edu.utexas.cs.tactex.servercustomers.common.TariffSubscription;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.*;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.state.Domain;

/**
 * A simple collection of capacity originators, all with the same base capacity
 * type;
 * i.e., CONSUMPTION or PRODUCTION.
 * 
 * @author Prashant Reddy
 */
@Domain
class DefaultCapacityBundle implements CapacityBundle
{
  
  static private Logger log = Logger.getLogger(DefaultCapacityBundle.class);

  
  protected FactoredCustomerService service;
  private final CustomerStructure customerStructure;

  // identity
  private final String name;
  private final CustomerInfo customerInfo;

  // evaluation tools
  //protected TariffEvaluator tariffEvaluator;

  private final TariffSubscriberStructure subscriberStructure;
  private final ProfileOptimizerStructure optimizerStructure;

  protected final List<CapacityOriginator> capacityOriginators =
    new ArrayList<CapacityOriginator>();

  DefaultCapacityBundle (FactoredCustomerService service,
                         CustomerStructure structure,
                         Element xml)
  {
    this.service = service;
    customerStructure = structure;
    //tariffEvaluator = new TariffEvaluator();

    String bundleId = xml.getAttribute("id");
    name =
      (bundleId == null || bundleId.isEmpty())? customerStructure.name
                                              : customerStructure.name + "@"
                                                + bundleId;

    customerInfo =
      new CustomerInfo(name, Integer.parseInt(xml.getAttribute("population")))
              .withPowerType(PowerType.valueOf(xml.getAttribute("powerType")))
              .withMultiContracting(Boolean.parseBoolean(xml
                                            .getAttribute("multiContracting")))
              .withCanNegotiate(Boolean.parseBoolean(xml
                                        .getAttribute("canNegotiate")));

    Element tariffSubscriberElement =
      (Element) xml.getElementsByTagName("tariffSubscriber").item(0);
    subscriberStructure =
      new TariffSubscriberStructure(service, structure, this, tariffSubscriberElement);

    Element profileOptimizerElement =
      (Element) xml.getElementsByTagName("profileOptimizer").item(0);
    optimizerStructure =
      new ProfileOptimizerStructure(structure, this, profileOptimizerElement);
  }

  @Override
  public void initialize (CustomerStructure structure,
                          Element xml)
  {
    NodeList capacityNodes = xml.getElementsByTagName("capacity");
    for (int i = 0; i < capacityNodes.getLength(); ++i) {
      Element capacityElement = (Element) capacityNodes.item(i);
      String name = capacityElement.getAttribute("name");
      String countString = capacityElement.getAttribute("count");
      if (countString == null || Integer.parseInt(countString) == 1) {
        CapacityStructure capacityStructure =
          new CapacityStructure(service, name, capacityElement, this);
        capacityOriginators.add(createCapacityOriginator(capacityStructure));
      }
      else {
        if (name == null)
          name = "";
        for (int j = 1; j < (1 + Integer.parseInt(countString)); ++j) {
          CapacityStructure capacityStructure =
            new CapacityStructure(service, name + j, capacityElement, this);
          capacityOriginators.add(createCapacityOriginator(capacityStructure));
        }
      }
    }
  }

  /** @Override hook **/
  protected CapacityOriginator
    createCapacityOriginator (CapacityStructure capacityStructure)
  {
    return new DefaultCapacityOriginator(service,
                                         capacityStructure,
                                         this);
  }

  @Override
  public String getName ()
  {
    return name;
  }

  @Override
  public int getPopulation ()
  {
    return customerInfo.getPopulation();
  }

  @Override
  public PowerType getPowerType ()
  {
    return customerInfo.getPowerType();
  }

  @Override
  public CustomerInfo getCustomerInfo ()
  {
    return customerInfo;
  }
  
  //@Override
  //public TariffEvaluator getTariffEvaluator ()
  //{
    //return tariffEvaluator;
  //}

  @Override
  public TariffSubscriberStructure getSubscriberStructure ()
  {
    return subscriberStructure;
  }

  @Override
  public ProfileOptimizerStructure getOptimizerStructure ()
  {
    return optimizerStructure;
  }

  @Override
  public List<CapacityOriginator> getCapacityOriginators ()
  {
    return capacityOriginators;
  }

  @Override
  public String toString ()
  {
    return this.getClass().getCanonicalName() + ":" + customerStructure.name
           + ":" + customerInfo.getPowerType();
  }


  // Daniel: data access methods
  
  /**
   * sum all originator's (scaled) energies (since I believe the broker sees the
   * sum of them only). 
   * @param currentTimeslot 
   * @throws Exception 
   * @throws DimensionMismatchException 
   */
  @Override
  public ArrayRealVector getPredictedEnergy(
      TariffSubscription subscription,
      int recordLength, 
      int currentTimeslot) throws DimensionMismatchException, Exception {
    ArrayRealVector result = new ArrayRealVector(recordLength);
    
    // sum all originator's energies 
    for (CapacityOriginator originator : capacityOriginators) {
      ArrayRealVector originatorPredictedEnergy = originator.getPredictedEnergy(subscription, recordLength, currentTimeslot);
      //log.info("originatorPredictedEnergy " + Arrays.toString(originatorPredictedEnergy.toArray()));
      result = result.add(originatorPredictedEnergy);
      //log.info("bundleresult " + Arrays.toString(result.toArray()));
    }
    
    // normalize to 1 population member
    result.mapDivideToSelf(customerInfo.getPopulation());
    //log.info("bundleresultnormalized " + Arrays.toString(result.toArray()));
    
    // all predictions are positive and are adjusted in the server in
    // DefaultUtilityOptimizer.usePower() therefore we adjust the sign here,
    // the last point before returning to our shifting predictor
    double usageSign = getPowerType().isConsumption()? +1: -1;
    result.mapMultiplyToSelf(usageSign);
    
    return result;
  }
  
  @Override
  public Double getShiftingInconvenienceFactor(TariffSubscription subscription, int recordLength) {
    Double result = 0.0;
    // sum over originator inconv's
    for (CapacityOriginator originator : capacityOriginators) {
      result += originator.getShiftingInconvenienceFactor(subscription.getTariff(), recordLength);
    }
    // sanity check - but need to change ServerBasedShiftingPredictor.fillRepoWithPredictedSubscriptions() to record #subs = 1
    //if (customerInfo.getPopulation() != 1)
    //  log.warn("Inconvenience factor might be wrong - should be for a single population member");
    return result;
  }

  @Override
  public void clearSubscriptionRelatedData() {
    for (CapacityOriginator originator : capacityOriginators) {
      originator.clearSubscriptionRelatedData();
    }
  }


} // end class
