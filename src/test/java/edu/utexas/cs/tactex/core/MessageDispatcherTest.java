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
package edu.utexas.cs.tactex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.powertac.common.msg.BrokerAccept;

import edu.utexas.cs.tactex.core.MessageDispatcher;

public class MessageDispatcherTest
{

  private MessageDispatcher router;
  

  @Before
  public void setUp () throws Exception
  {
    router = new MessageDispatcher();
  }


  @Test
  public void testRegisterMessageHandler ()
  {
    assertNotNull("make sure we have a router", router);
    Set<Object> regs = router.getRegistrations(Set.class);
    assertNull("no registrations yet", regs);
    router.registerMessageHandler(this, Set.class);
    regs = router.getRegistrations(Set.class);
    assertEquals("one registration", 1, regs.size());
    assertTrue("correct registration", regs.contains(this));
  }


  @Test
  public void testRouteMessage ()
  {
    LocalHandler handler = new LocalHandler();
    router.registerMessageHandler(handler, BrokerAccept.class);
    assertNull("initially null", handler.result);
    BrokerAccept accept = new BrokerAccept(1);
    router.routeMessage(accept);
    assertEquals("received message", accept, handler.result);
  }


  public class LocalHandler
  {
    Object result = null;

    public void handleMessage (BrokerAccept msg)
    {
      result = msg;
    }
  }
}
