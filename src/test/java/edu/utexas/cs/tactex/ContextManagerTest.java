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


import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import edu.utexas.cs.tactex.ContextManagerService;
import edu.utexas.cs.tactex.core.PowerTacBroker;


/**
 * @author urieli
 */
public class ContextManagerTest {

  private ContextManagerService contextManagerService;
  private PowerTacBroker broker;

  /**
   *
   */
  @Before
  public void setUp () throws Exception
  {
    broker = mock(PowerTacBroker.class);
    contextManagerService = new ContextManagerService();
    contextManagerService.initialize(broker);
  }

  /**
   * Test server properties message: 
   * different simulation parameters
   */
  @Test
  public void testServerProps ()
  {
    // prepare the message
    java.util.Properties serverProps = new java.util.Properties();
    serverProps.setProperty("tariffmarket.tariffMarketService.revocationFee", "0.1");
    serverProps.setProperty("auctioneer.auctionService.sellerSurplusRatio", "0.2");
    serverProps.setProperty("balancemkt.balancingMarketService.pPlusPrime", "0.3");
    serverProps.setProperty("auctioneer.auctionService.defaultClearingPrice", "0.4");
    serverProps.setProperty("balancemkt.balancingMarketService.defaultSpotPrice", "0.5");
    serverProps.setProperty("balancemkt.balancingMarketService.settlementProcess", "static");
    serverProps.setProperty("auctioneer.auctionService.defaultMargin", "0.7");
    serverProps.setProperty("accounting.accountingService.bankInterest", "0.8");
    serverProps.setProperty("balancemkt.balancingMarketService.balancingCost", "0.9");
    serverProps.setProperty("tariffmarket.tariffMarketService.publicationFee", "1.0");
    serverProps.setProperty("distributionutility.distributionUtilityService.distributionFee", "1.1");
    serverProps.setProperty("balancemkt.balancingMarketService.pMinusPrime", "1.2");

    

    // receive message
    contextManagerService.handleMessage(serverProps);

    assertEquals(contextManagerService.getRevocationFee(), 0.1, 0.00000001);
    assertEquals(contextManagerService.getSellerSurplusRatio(), 0.2, 0.00000001);
    assertEquals(contextManagerService.getPPlusPrime(), 0.3, 0.00000001);
    assertEquals(contextManagerService.getDefaultClearingPrice(), 0.4, 0.00000001);
    assertEquals(contextManagerService.getDefaultSpotPrice(), 0.5, 0.00000001);
    assertEquals(contextManagerService.getSettlementProcess(), "static");
    assertEquals(contextManagerService.getDefaultMargin(), 0.7, 0.00000001);
    assertEquals(contextManagerService.getBankInterest(), 0.8, 0.00000001);
    assertEquals(contextManagerService.getBalancingCost(), 0.9, 0.00000001);
    assertEquals(contextManagerService.getPublicationFee(), 1.0, 0.00000001);
    assertEquals(contextManagerService.getDistributionFee(), 1.1, 0.00000001);
    assertEquals(contextManagerService.getPMinusPrime(), 1.2, 0.00000001);

  }
}
