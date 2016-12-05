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
 *     Copyright (c) 2011 by the original author
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
package edu.utexas.cs.tactex.servercustomers.common.interfaces;

import java.util.List;

/**
 * Handles the pre-game competition setup process. To start a simulation,
 * one must call preGame(), followed by CompetitionControl.runOnce().
 * @author John Collins
 */
public interface CompetitionSetup
{  
  /**
   * Runs the pre-game cycle of the simulator, which sets all plugin components
   * to their default state.
   */
  public void preGame ();
  
  /**
   * Starts a bootstrap session with parameters. Result is null if successful,
   * otherwise contains an error message. Parameters are:
   * <ul>
   * <li>bootFilename is the (required)
   * name of an output file where the bootstrap dataset will be stored.</li>
   * <li>configFilename is the (optional) name of a server-configuration file;
   * if the name contains a ":" it will be treated as a URL.</li>
   * <li>logfileSuffix is an (optional) filename suffix for the log output
   * files; default value is "boot".</li>
   * <li>weatherData is the (optional) name of a file containing weather data
   * for the simulation; Either XMl or a state file
   * A state file will be treated as a URL if it contains a ":" character.</li>
   * </ul> 
   */
  public String bootSession (String bootFilename,
                             String configFilename,
                             String logfileSuffix,
                             String seedData,
                             String weatherData);
  
  /**
   * Starts a simulation session with parameters, in a new thread.
   * Result is null if successful, otherwise contains an error message.
   * Parameters are:
   * <ul>
   * <li>bootData is the (required) name of an input file containing a
   * bootstrap dataset for the simulation; it will be treated as a URL if it
   * contains a ":" character.</li>
   * <li>config is the (optional) name of a server-configuration file;
   * if the name contains a ":" it will be treated as a URL.</li>
   * <li>jmsUrl is the URL on which the server should listen for JMS connections.
   * This is required if the server is to interact with brokers on separate
   * machines. If given, all brokers must use this URL to contact the server.
   * <li>logfileSuffix is an (optional) filename suffix for the log output
   * files; default value is "sim".</li>
   * <li>brokerUsernames is a list of Strings giving the usernames of brokers
   * who are expected to log in to the simulation before it starts. If a username
   * is of the form string1/string2, then string1 is the broker's username,
   * and string2 is the name of that broker's JMS input queue. If this argument
   * is null or empty, the simulation will start without brokers.</li>
   * <li>seedData is the (optional) name of a file containing seed data
   * for the simulation;
   * it will be treated as a URL if it contains a ":" character.</li>
   * <li>weatherData is the (optional) name of a file containing weather data
   * for the simulation; Either XMl or a state file
   * A state file will be treated as a URL if it contains a ":" character.</li>
   * <li>inputQueueName is the name of the server's JMS input queue. If not
   * given, then the default value of 'serverInput' will be used.
   * </ul> 
   */
  public String simSession (String bootData,
                            String config,
                            String jmsUrl,
                            String logfileSuffix,
                            List<String> brokerUsernames,
                            String seedData,
                            String weatherData,
                            String inputQueueName);
}
