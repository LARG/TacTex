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

import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.powertac.common.Broker;
import org.powertac.common.repo.TariffRepo;

import edu.utexas.cs.tactex.TariffRepoMgrService;
import edu.utexas.cs.tactex.interfaces.EnergyPredictionManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.TariffSuggestionMaker;
import edu.utexas.cs.tactex.interfaces.UtilityEstimator;
import edu.utexas.cs.tactex.utilityestimation.UtilityArchitectureActionGenerator;

public class StochasticConsumptionTariffGeneratorTest {

  
  private TariffRepoMgrService tariffRepoMgrService;
  
  private EnergyPredictionManager energyPredictionManager;

  private MarketPredictionManager marketPredictionManager;

  private TariffSuggestionMaker consumptionTariffSuggestionMaker;

  private UtilityEstimator utilityEstimator;
  
  private Broker broker;
    

  private UtilityArchitectureActionGenerator sctg;

//  @Before
//  public void setUp () throws Exception
//  {
//    tariffRepoMgrService = new TariffRepoMgrService();
//    energyPredictionManager = mock(EnergyPredictionManager.class);
//    marketPredictionManager = mock(MarketPredictionManager.class);
//    consumptionTariffSuggestionMaker = mock(ConsumptionTariffSuggestionMaker.class);
//    utilityEstimator = mock(UtilityEstimator.class);
//    broker = new Broker("testbroker");
//    
//    TariffOptimizer tariffOptimizer = new
//        TariffOptimizerOneShot(
//            withdrawFeesOptimizer, 
//            tariffRepoMgrService,
//            consumptionTariffSuggestionMaker, 
//            utilityEstimator,
//            marketPredictionManager, 
//            chargeEstimator, 
//            configuratorFactoryService);
//    sctg = new StochasticConsumptionTariffGenerator(energyPredictionManager, 
//        tariffOptimizer );
//
//  }
  
  @Test
  public void testTemporarilyAddRemoveSpecs () {
    
//    TariffSpecification spec1 = new TariffSpecification(broker, PowerType.CONSUMPTION);
//    TariffSpecification spec2 = new TariffSpecification(broker, PowerType.CONSUMPTION);
//    TariffSpecification spec3 = new TariffSpecification(broker, PowerType.CONSUMPTION);
//    TariffSpecification spec4 = new TariffSpecification(broker, PowerType.CONSUMPTION);
//    List<TariffSpecification> suggestedSpecs = new ArrayList<TariffSpecification>();
//    suggestedSpecs.add(spec1);
//    suggestedSpecs.add(spec2);
//    suggestedSpecs.add(spec3);
//    suggestedSpecs.add(spec4);
//
//    List<TariffSpecification> specsToAdd = new ArrayList<TariffSpecification>();
//    specsToAdd.add(spec2);
//    specsToAdd.add(spec4);
//    
//    sctg.addSuggestedSpecsToRepo(suggestedSpecs);
//    sctg.removeNonChosenSpecsFromRepo(suggestedSpecs, specsToAdd);
//
//    assertNull("remove non-chosen spec1", tariffRepo.findSpecificationById(spec1.getId()));
//    assertNotNull("not remove remove chosen spec2", tariffRepo.findSpecificationById(spec2.getId()));
//    assertNull("remove non-chosen spec3", tariffRepo.findSpecificationById(spec3.getId()));
//    assertNotNull("not remove chosen spec4", tariffRepo.findSpecificationById(spec4.getId()));

  }
}
