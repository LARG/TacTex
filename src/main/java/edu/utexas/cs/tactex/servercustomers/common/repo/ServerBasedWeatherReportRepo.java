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
 *     Copyright (c) 2011-2013 by the original author
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
package edu.utexas.cs.tactex.servercustomers.common.repo;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

//import org.apache.log4j.Logger;
import org.apache.log4j.Logger;
import org.powertac.common.WeatherReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.powertac.common.exceptions.PowerTacException;
import org.powertac.common.repo.DomainRepo;
import org.powertac.common.repo.TimeslotRepo;

/**
 * Repository for WeatherReports. The weather reports are indexed by the
 * timeslot that they are issued for. This allows them to be quickly accessed
 * via a hashMap.
 * 
 * @author Erik Onarheim
 */
@Repository
public class ServerBasedWeatherReportRepo implements DomainRepo
{
  static private Logger log = Logger.getLogger(ServerBasedWeatherReportRepo.class
          .getName());

  // storage
  private Map<Integer, WeatherReport> indexedWeatherReports;

  // Check if the weather service has run at least once
  private boolean hasRunOnce = false;

  @Autowired
  private TimeslotRepo timeslotRepo;

  /** standard constructor */
  public ServerBasedWeatherReportRepo ()
  {
    super();
    indexedWeatherReports =
        new ConcurrentHashMap<Integer, WeatherReport>(2000, 0.9f, 1);
  }

  /**
   * Adds a WeatherReport to the repo
   */
  public void add (WeatherReport weather)
  {
    runOnce();
    indexedWeatherReports.put(weather.getTimeslotIndex(), weather);
  }

  /**
   * Returns the current weatherReport
   * @param currentTimeslot 
   */
  public WeatherReport currentWeatherReport (int currentTimeslot) throws PowerTacException
  {
    if (!hasRunOnce) {
      log.error("Weather Service has yet to run, cannot retrieve report");
      throw new PowerTacException(
                                  "Attempt to retrieve report before data available");
    }
    // Returns the weather report for the current timeslot
    return indexedWeatherReports.get(currentTimeslot);
  }

  /**
   * Returns a list of all the issued weather reports up to the
   * currentTimeslot
   * @param currentTimeslot 
   */
  public List<WeatherReport> allWeatherReports (int currentTimeslot)
  {
    //Integer current = timeslotRepo.currentSerialNumber();
    Integer current = currentTimeslot;
    // Some weather reports exist in the repo for the future
    // but have not been issued for the current timeslot.
    ArrayList<WeatherReport> issuedReports = new ArrayList<WeatherReport>();
    for (WeatherReport w: indexedWeatherReports.values()) {
      if (w.getTimeslotIndex() < current) {
        issuedReports.add(w);
      }
    }
    issuedReports.add(this.currentWeatherReport(currentTimeslot));

    return (List<WeatherReport>) issuedReports;
  }

  /**
   * Returns the number of weatherReports that have been successfully added.
   */
  public int count ()
  {
    return indexedWeatherReports.size();
  }

  /**
   * Called by weather service to indicate weather exists
   */
  public void runOnce ()
  {
    hasRunOnce = true;
  }

  @Override
  public void recycle ()
  {
    hasRunOnce = false;
    indexedWeatherReports.clear();
  }
}
