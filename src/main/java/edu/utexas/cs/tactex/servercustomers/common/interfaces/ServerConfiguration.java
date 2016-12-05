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
package edu.utexas.cs.tactex.servercustomers.common.interfaces;

import java.util.Collection;

/**
 * Support for annotation-driven configuration. Configurable services, including
 * services that create configurable instances (such as gencos or customer models),
 * configure themselves by calling configure(). The result is that properties
 * annotated as @ConfigurableValue will be matched with configuration
 * data, and classes annotated as @ConfigurableInstance will have instances
 * created and configured as specified in the server configuration.
 * @author John Collins
 */
public interface ServerConfiguration
{
  /**
   * Configures a target object by matching configuration clauses with 
   * @ConfigurableValue annotations on the target object. This is typically
   * called in the initialize() method.
   */
  public void configureMe (Object target);
  
  /**
   * Creates and configures potentially multiple instances of a target class
   * annotated as @ConfigurableInstance. Returns the created instances in 
   * a list in no particular order.
   */
  public Collection<?> configureInstances (Class<?> target);
  
  /**
   * Gathers public configuration data for publication to brokers. Data is gathered
   * from @ConfigurableValue properties with publish=true. Note that such properties
   * must either be fields, or have a "standard" getter, or must specify a getter
   * that produces the value as a String. This is typically called at the end of
   * the initialize() method.
   */
  public void publishConfiguration (Object target);

  /**
   * Gathers state information at the end of a boot session to be restored
   * in a subsequent sim session. Data is gathered from @ConfigurableValue
   * properties with bootstrapState=true. Such properties can be fields, or
   * may have "standard" getters and setters. This method is called at the
   * end of a bootstrap session by a component that wishes to have its state
   * saved in the boot record.
   */
  public void saveBootstrapState (Object thing);
}
