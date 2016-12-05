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

import java.util.HashMap;

import static org.junit.Assert.*;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Broker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;

import edu.utexas.cs.tactex.shiftingpredictors.ShiftingPredictorNoShifts;
import edu.utexas.cs.tactex.utils.BrokerUtils.ShiftedEnergyData;


public class ShiftingPredictorTest {
  
  private static final int HORIZON = 48;
  private Broker broker;
  private ShiftingPredictorNoShifts shiftingPredictorNoShifts;
  private HashMap<CustomerInfo, ArrayRealVector> customer2estimatedEnergy;
  private CustomerInfo cust1;
  private ArrayRealVector energy1;
  private CustomerInfo cust2;
  private ArrayRealVector energy2;
  private HashMap<TariffSpecification, HashMap<CustomerInfo, Double>> predictedCustomerSubscriptions;
  private int currentTimeslot;
  private TariffSpecification spec1;
  private TariffSpecification spec2;

  @Before
  public void setUp () throws Exception {

    broker = new Broker("mybroker");
    shiftingPredictorNoShifts = new ShiftingPredictorNoShifts();

    // initialize energy profiles
    customer2estimatedEnergy = new HashMap<CustomerInfo, ArrayRealVector>();
    cust1 = new CustomerInfo("centerville", 4);
    energy1 = new ArrayRealVector(HORIZON);
    for (int i = 0; i < HORIZON; ++i) {
      energy1.setEntry(i, i%4);
    }
    customer2estimatedEnergy.put(cust1, energy1);
    cust2 = new CustomerInfo("frosty", 6);
    energy2 = new ArrayRealVector(HORIZON);
    for (int i = 0; i < HORIZON; ++i) {
      energy2.setEntry(i, i%2);
    }
    customer2estimatedEnergy.put(cust2, energy2);
    
    // initialize subscriptions
    predictedCustomerSubscriptions = new HashMap<TariffSpecification, HashMap<CustomerInfo,Double>>();
    spec1 = new TariffSpecification(broker, PowerType.CONSUMPTION);
    predictedCustomerSubscriptions.put(spec1, new HashMap<CustomerInfo, Double>());
    predictedCustomerSubscriptions.get(spec1).put(cust1, 1.0);
    predictedCustomerSubscriptions.get(spec1).put(cust2, 2.0);
    spec2 = new TariffSpecification(broker, PowerType.CONSUMPTION);
    predictedCustomerSubscriptions.put(spec2, new HashMap<CustomerInfo, Double>());
    predictedCustomerSubscriptions.get(spec2).put(cust1, 3.0);
    predictedCustomerSubscriptions.get(spec2).put(cust2, 4.0);

    currentTimeslot = 370; // magic number

  }
  

  @Test
  public void testShiftingPredictorNoShifts () {
    HashMap<CustomerInfo, HashMap<TariffSpecification, ShiftedEnergyData>> 
        cust2trf2energy = shiftingPredictorNoShifts.
            updateEstimatedEnergyWithShifting(
                customer2estimatedEnergy ,
                predictedCustomerSubscriptions, 
                currentTimeslot);
    // verify keys are cust1 and cust2
    assertEquals("2 customers keys in result", 2, cust2trf2energy.keySet().size());
    assertNotNull("cust1 in keys", cust2trf2energy.get(cust1));
    assertNotNull("cust2 in keys", cust2trf2energy.get(cust2));
    
    // verify cust[i] is mapped to spec1=>energy[i] spec2=>energy[i] 
    //
    // cust1
    HashMap<TariffSpecification, ShiftedEnergyData> cust1_spec2energy = cust2trf2energy.get(cust1);
    ArrayRealVector predictedenergy11 = cust1_spec2energy.get(spec1).getShiftedEnergy();
    ArrayRealVector predictedenergy12 = cust1_spec2energy.get(spec2).getShiftedEnergy();
    assertNotNull("cust1: spec1=>energy1 exists", predictedenergy11);
    assertEquals("cust1: spec1=>energy1 as expected", energy1, predictedenergy11);
    assertNotNull("cust1: spec2=>energy1 exists", predictedenergy12);
    assertEquals("cust1: spec2=>energy1 as expected", energy1, predictedenergy12);
    //
    // cust2
    HashMap<TariffSpecification, ShiftedEnergyData> cust2_spec2energy = cust2trf2energy.get(cust2);
    ArrayRealVector predictedenergy21 = cust2_spec2energy.get(spec1).getShiftedEnergy();
    ArrayRealVector predictedenergy22 = cust2_spec2energy.get(spec2).getShiftedEnergy();
    assertNotNull("cust2: spec1=>energy2 exists", predictedenergy21);
    assertEquals("cust2: spec1=>energy2 as expected", energy2, predictedenergy21);
    assertNotNull("cust2: spec2=>energy2 exists", predictedenergy22);
    assertEquals("cust2: spec2=>energy2 as expected", energy2, predictedenergy22);


  }

}
