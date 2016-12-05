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
 *     Copyright (c) 2012 by the original author
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package edu.utexas.cs.tactex.core;

import static org.powertac.util.MessageDispatcher.dispatch;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;
import org.powertac.common.IdGenerator;
import org.powertac.common.XMLMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;

/**
 * Routes incoming messages to broker components, and outgoing messages
 * to the server. 
 * Components must register for specific message types with the broker, 
 * which passes the registrations to this router. For this to work, 
 * registered components must implement a handleMessage(msg) method that
 * takes the specified type as its single argument.
 * @author John Collins
 */
@Service
public class MessageDispatcher
{
  static private Logger log = Logger.getLogger(MessageDispatcher.class);

  @Autowired
  private XMLMessageConverter converter;

  @Autowired
  private JmsTemplate template;
  
  @Autowired
  private JmsManagementService jmsManagementService; 

  private HashMap<Class<?>, Set<Object>> registrations;
  String key = ""; // server registration secret

  /**
   * Default constructor
   */
  public MessageDispatcher ()
  {
    super();
    registrations = new HashMap<Class<?>, Set<Object>>();
  }
  
  void setKey (String key)
  {
    this.key = key;
  }

  // ------------- incoming messages ----------------
  /**
   * Sets up handlers for incoming messages by message type.
   */
  public void registerMessageHandler (Object handler, Class<?> messageType)
  {
    Set<Object> reg = registrations.get(messageType);
    if (reg == null) {
      reg = new HashSet<Object>();
      registrations.put(messageType, reg);
    }
    reg.add(handler);
  }
  
  /**
   * Routes incoming messages from the server
   */
  public void routeMessage (Object message)
  {
    Class<?> clazz = message.getClass();
    log.debug("Route " + clazz.getName());
    Set<Object> targets = registrations.get(clazz);
    if (targets == null) {
      log.warn("no targets for message of type " + clazz.getName());
      return;
    }
    for (Object target: targets) {
      try {
        dispatch(target, "handleMessage", message);
      } catch (Throwable e) {
        log.error("caught exception from handleMessage(): ", e);
      }
    }
  }

  // ------------------ Outgoing messages ------------------
  /**
   * Sends outgoing messages to the server
   */
  public void sendMessage(Object message)
  {
    if (!validateId(message))
      return;
    final String text = key + converter.toXML(message);
    log.info("sending text: \n" + text);

    template.send(jmsManagementService.getServerQueueName(),
                  new MessageCreator() {
      @Override
      public Message createMessage (Session session) throws JMSException
      {
        TextMessage message = session.createTextMessage(text);
        return message;
      }
    });
  }

  private boolean validateId (Object thing)
  {
    try {
      Field idField = thing.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      long value = idField.getLong(thing);
      if (IdGenerator.getPrefix() != IdGenerator.extractPrefix(value)) {
        log.error("Invalid id value " + value + " in message "
                  + thing.toString());
        return false;
      }
    }
    catch (NoSuchFieldException e) {
      // no id field, OK to send
      return true;
    }
    catch (SecurityException e) {
      // Should not happen
      log.error("Exception accessing id field: " + e.toString());
    }
    catch (IllegalArgumentException e) {
      // Should not happen
      log.error("Exception reading id field: " + e.toString());
    }
    catch (IllegalAccessException e) {
      // Should not happen
      log.error("Exception reading id field: " + e.toString());
    }
    return true;
  }

  // test-support
  Set<Object> getRegistrations (Class<?> messageType)
  {
    return registrations.get(messageType);
  }
}
