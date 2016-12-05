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
*     Copyright 2011 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
//import org.powertac.common.ConfigServerBroker;
import org.powertac.common.Tariff;
import org.powertac.common.TariffSpecification;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherForecastPrediction;
import org.powertac.common.WeatherReport;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.state.Domain;

import edu.utexas.cs.tactex.servercustomers.common.TariffSubscription;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.CapacityStructure.BaseCapacityType;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.CapacityStructure.InfluenceKind;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.*;

/**
 * Key class responsible for drawing from a base capacity and ajusting that
 * capacity in response to various static and dynamic factors for each timeslot.
 * 
 * @author Prashant Reddy
 */
@Domain
class DefaultCapacityOriginator implements CapacityOriginator
{
  protected Logger log = Logger.getLogger(DefaultCapacityOriginator.class
          .getName());

  protected final FactoredCustomerService service;
  // protected final TimeService timeService;
  // protected final TimeslotRepo timeslotRepo;
  // protected final WeatherReportRepo weatherReportRepo;
  // protected final WeatherForecastRepo weatherForecastRepo;

  private final double SMOOTHING_WEIGHT = 0.4; // 0.0 => ignore previous value

  private final TimeseriesGenerator tsGenerator;

  private final CapacityStructure capacityStructure;
  private final CapacityBundle parentBundle;

  protected final String logIdentifier;

  private final Map<Integer, Double> baseCapacities =
    new HashMap<Integer, Double>();
  protected final Map<Integer, Double> forecastCapacities =
    new HashMap<Integer, Double>();
  protected final Map<Integer, Double> actualCapacities =
    new HashMap<Integer, Double>();
  protected final Map<Integer, Double> curtailedCapacities =
    new HashMap<Integer, Double>();
  protected final Map<Integer, Double> shiftedCurtailments =
    new HashMap<Integer, Double>();

  // cache
  private WeatherReport currentWeatherReport;
  private WeatherForecast currentWeatherForecast;

  DefaultCapacityOriginator (FactoredCustomerService service,
                             CapacityStructure structure, CapacityBundle bundle)
  {
    this.service = service;
    capacityStructure = structure;
    parentBundle = bundle;

    logIdentifier =
      capacityStructure.capacityName.isEmpty()? bundle.getName(): bundle
              .getName() + "#" + capacityStructure.capacityName;

    // timeService = (TimeService)
    // SpringApplicationContext.getBean("timeService");
    // timeslotRepo = (TimeslotRepo)
    // SpringApplicationContext.getBean("timeslotRepo");
    // weatherReportRepo = (WeatherReportRepo)
    // SpringApplicationContext.getBean("weatherReportRepo");
    // weatherForecastRepo = (WeatherForecastRepo)
    // SpringApplicationContext.getBean("weatherForecastRepo");

    if (capacityStructure.baseCapacityType == BaseCapacityType.TIMESERIES) {
      tsGenerator =
        new TimeseriesGenerator(service,
                                capacityStructure.baseTimeseriesStructure);
    }
    else
      tsGenerator = null;
  }

  // changed to propagate currentTimeslot and avoid sync issues
  @Override
  public CapacityProfile getCurrentForecast (int currentTimeslot)
  {
    //int timeslot = service.getTimeslotRepo().currentSerialNumber();
    return getForecastStartingAt(currentTimeslot, currentTimeslot);
  }
  
  @Override
  public CapacityProfile getForecastStartingAt(int currentTimeslot, int startingTimeslot) {
    int timeslot = startingTimeslot;
    List<Double> values = new ArrayList<Double>();
    for (int i = 0; i < CapacityProfile.NUM_TIMESLOTS; ++i) {
      Double forecastCapacity = forecastCapacities.get(timeslot);
      if (forecastCapacity != null) {
        values.add(forecastCapacity);
      }
      else {
        values.add(getForecastCapacity(currentTimeslot, timeslot));
      }
      timeslot += 1;
    }
    return new CapacityProfile(values);
  }
  
  @Override
  public CapacityProfile getCurrentForecastPerSub(int currentTimeslot, TariffSubscription sub) {
    // DefaultCapacityOriginator doesn't track subscriptions, so:
    return getCurrentForecast(currentTimeslot);
  }

  protected double getForecastCapacity (int currentTimeslot, int timeslot)
  {
    Double ret = forecastCapacities.get(timeslot);
    if (ret == null)
      ret = computeForecastCapacity(currentTimeslot, timeslot);
    return ret;
  }

  private double computeForecastCapacity (int currentTimeslot, int future)
  {
    //log.info("computeForecastCapacity()");
    //int now = service.getTimeslotRepo().currentSerialNumber();
    int now = currentTimeslot;
    int timeToFuture = future - now;
    Weather weather = null;
    if (timeToFuture == 0) {
      weather =
        new Weather(getCurrentWeatherReport(now));
    }
    else {
      WeatherForecast forecast =
        getCurrentWeatherForecast(now);
      List<WeatherForecastPrediction> predictions = forecast.getPredictions();
      for (WeatherForecastPrediction prediction: predictions) {
        if (prediction.getForecastTime() == timeToFuture) {
          weather = new Weather(prediction);
        }
      }
    }
    if (weather == null)
      throw new Error("Could not find weather forecast for timeslot " + future);

    //log.info("wind speed =" + weather.getWindSpeed());
    
    double baseCapacity = getBaseCapacity(future);
    //log.info("baseCapacity" + baseCapacity);
    if (Double.isNaN(baseCapacity))
      throw new Error("Base capacity is NaN!");

    // Compute for full population ignoring current tariff rates
    double forecastCapacity = baseCapacity;
    forecastCapacity =
      adjustCapacityForPeriodicSkew(forecastCapacity, service.getTimeslotRepo()
              .getTimeForIndex(future).toDateTime(DateTimeZone.UTC), false);
    //log.info("forecastCapacity after periodic skew: " + forecastCapacity);
    forecastCapacity =
      adjustCapacityForWeather(forecastCapacity, weather, false);
    //log.info("forecastCapacity after weather skew: " + forecastCapacity);
    if (Double.isNaN(forecastCapacity))
      throw new Error("Adjusted capacity is NaN for base capacity = "
                      + baseCapacity);

    forecastCapacity = truncateTo2Decimals(forecastCapacity);
    //log.info("forecastCapacity after truncation do decimals: " + forecastCapacity);
    forecastCapacities.put(future, forecastCapacity);
    log.debug(logIdentifier + ": Forecast capacity for timeslot " + future
              + " = " + forecastCapacity);
    return forecastCapacity;
  }

  private double getBaseCapacity (int future)
  {
    Double ret = baseCapacities.get(future);
    if (ret == null)
      ret = drawBaseCapacitySample(future);
    return ret;
  }

  private double drawBaseCapacitySample (int timeslot)
  {
    double baseCapacity = 0.0;
    switch (capacityStructure.baseCapacityType) {
    case POPULATION:
      baseCapacity = capacityStructure.basePopulationCapacity.drawSample();
      break;
    case INDIVIDUAL:
      for (int i = 0; i < parentBundle.getPopulation(); ++i) {
        double draw = capacityStructure.baseIndividualCapacity.drawSample();
        baseCapacity += draw;
      }
      break;
    case TIMESERIES:
      baseCapacity = getBaseCapacityFromTimeseries(timeslot);
      break;
    default:
      throw new Error(logIdentifier + ": Unexpected base capacity type: "
                      + capacityStructure.baseCapacityType);
    }
    Double prevCapacity = baseCapacities.get(timeslot - 1);
    if (prevCapacity != null) {
      baseCapacity =
        SMOOTHING_WEIGHT * prevCapacity + (1 - SMOOTHING_WEIGHT) * baseCapacity;
    }
    baseCapacity = truncateTo2Decimals(baseCapacity);
    baseCapacities.put(timeslot, baseCapacity);
    return baseCapacity;
  }

  private double getBaseCapacityFromTimeseries (int timeslot)
  {
    try {
      return tsGenerator.generateNext(timeslot);
    }
    catch (ArrayIndexOutOfBoundsException e) {
      log.error(logIdentifier
                + ": Tried to get base capacity from time series at index beyond maximum!");
      throw e;
    }
  }

  @Override
  public double getShiftingInconvenienceFactor(Tariff tariff, int recordLength) {
    return 0; // not shifting should take place in DefaultCapacityOriginator
  }

  @Override
  public double useCapacity (TariffSubscription subscription)
  {
    //log.info("useCapacity()");
    int timeslot = service.getTimeslotRepo().currentSerialNumber();

    double baseCapacity = getBaseCapacity(timeslot);
    if (Double.isNaN(baseCapacity))
      throw new Error("Base capacity is NaN!");
    logCapacityDetails(logIdentifier + ": Base capacity for timeslot "
                       + timeslot + " = " + baseCapacity);

    // total adjusted capacity
    double adjustedCapacity = baseCapacity;
    if (parentBundle.getPowerType().isInterruptible()) {
      adjustedCapacity =
        adjustCapacityForCurtailments(timeslot, adjustedCapacity, subscription);
    }
    adjustedCapacity =
      adjustCapacityForPeriodicSkew(adjustedCapacity, service.getTimeService()
              .getCurrentDateTime(), true);
    adjustedCapacity = adjustCapacityForCurrentWeather(adjustedCapacity, true);

    // capacity for this subscription
    adjustedCapacity =
      adjustCapacityForSubscription(timeslot, adjustedCapacity, subscription);
    if (Double.isNaN(adjustedCapacity)) {
      throw new Error("Adjusted capacity is NaN for base capacity = "
                      + baseCapacity);
    }

    adjustedCapacity = truncateTo2Decimals(adjustedCapacity);
    actualCapacities.put(timeslot, adjustedCapacity);
    log.info(logIdentifier + ": Adjusted capacity for tariff "
             + subscription.getTariff().getId() + " = " + adjustedCapacity);
    return adjustedCapacity;
  }

  private double
    adjustCapacityForCurtailments (int timeslot, double capacity,
                                   TariffSubscription subscription)
  {
    double lastCurtailment = subscription.getCurtailment();
    if (Math.abs(lastCurtailment) > 0.01) { // != 0
      curtailedCapacities.put(timeslot - 1, lastCurtailment);
      if (capacityStructure.curtailmentShifts != null) {
        for (int i = 0; i < capacityStructure.curtailmentShifts.length; ++i) {
          double shiftingFactor = capacityStructure.curtailmentShifts[i];
          double shiftedCapacity = lastCurtailment * shiftingFactor;
          Double previousShifts = shiftedCurtailments.get(timeslot + i);
          if (previousShifts == null) {
            shiftedCurtailments.put(timeslot + i, shiftedCapacity);
          }
          else {
            shiftedCurtailments.put(timeslot + i, previousShifts
                                                  + shiftedCapacity);
          }
        }
      }
    }
    Double currentShift = shiftedCurtailments.get(timeslot);
    return (currentShift == null)? capacity: capacity + currentShift;
  }

  private double adjustCapacityForPeriodicSkew (double capacity, DateTime when,
                                                boolean verbose)
  {
    int day = when.getDayOfWeek(); // 1=Monday, 7=Sunday
    int hour = when.getHourOfDay(); // 0-23

    double periodicSkew =
      capacityStructure.dailySkew[day - 1] * capacityStructure.hourlySkew[hour];
    if (verbose)
      logCapacityDetails(logIdentifier + ": periodic skew = " + periodicSkew);
    return capacity * periodicSkew;
  }

  private double adjustCapacityForCurrentWeather (double capacity,
                                                  boolean verbose)
  {
    // unused code, just fixed it to compile 
    WeatherReport weatherReport = 
      //service.getWeatherReportRepo().currentWeatherReport();
        currentWeatherReport;
    return adjustCapacityForWeather(capacity, new Weather(weatherReport),
                                    verbose);
  }

  private double adjustCapacityForWeather (double capacity, Weather weather,
                                           boolean verbose)
  {
    if (verbose)
      logCapacityDetails(logIdentifier + ": weather = ("
                         + weather.getTemperature() + ", "
                         + weather.getWindSpeed() + ", "
                         + weather.getWindDirection() + ", "
                         + weather.getCloudCover() + ")");

    double weatherFactor = 1.0;
    if (capacityStructure.temperatureInfluence == InfluenceKind.DIRECT) {
      int temperature = (int) Math.round(weather.getTemperature());
      weatherFactor =
        weatherFactor * capacityStructure.temperatureMap.get(temperature);
      //log.info("weatherFactor after temperatureInfluence.DIRECT: " + weatherFactor);
    }
    else if (capacityStructure.temperatureInfluence == InfluenceKind.DEVIATION) {
      int curr = (int) Math.round(weather.getTemperature());
      int ref = (int) Math.round(capacityStructure.temperatureReference);
      double deviationFactor = 1.0;
      if (curr > ref) {
        for (int t = ref + 1; t <= curr; ++t) {
          deviationFactor += capacityStructure.temperatureMap.get(t);
        }
      }
      else if (curr < ref) {
        for (int t = curr; t < ref; ++t) {
          deviationFactor += capacityStructure.temperatureMap.get(t);
        }
      }
      weatherFactor = weatherFactor * deviationFactor;
      //log.info("weatherFactor after temperatureInfluence.DEVIATION: " + weatherFactor);
    }
    if (capacityStructure.windSpeedInfluence == InfluenceKind.DIRECT) {
      int windSpeed = (int) Math.round(weather.getWindSpeed());
      weatherFactor =
        weatherFactor * capacityStructure.windSpeedMap.get(windSpeed);
      //log.info("weatherFactor after windSpeedInfluence.DIRECT: " + weatherFactor);
      if (windSpeed > 0.0
          && capacityStructure.windDirectionInfluence == InfluenceKind.DIRECT) {
        int windDirection = (int) Math.round(weather.getWindDirection());
        weatherFactor =
          weatherFactor * capacityStructure.windDirectionMap.get(windDirection);
        //log.info("weatherFactor after windDirectionInfluence.DIRECT: " + weatherFactor);
      }
    }
    if (capacityStructure.cloudCoverInfluence == InfluenceKind.DIRECT) {
      int cloudCover = (int) Math.round(100 * weather.getCloudCover()); // [0,1]
                                                                        // to
                                                                        // ##%
      weatherFactor =
        weatherFactor * capacityStructure.cloudCoverMap.get(cloudCover);
      //log.info("weatherFactor after cloudCoverInfluence.DIRECT: " + weatherFactor);
    }
    if (verbose)
      logCapacityDetails(logIdentifier + ": weather factor = " + weatherFactor);
    //log.info("adjusted capacity " + capacity * weatherFactor);
    return capacity * weatherFactor;
  }

  @Override
  public double adjustCapacityForSubscription (int timeslot,
                                               double totalCapacity,
                                               TariffSubscription subscription)
  {
    //log.info("adjustCapacityForSubscription()");
    //log.info("totalCapacity=" + totalCapacity);
    double subCapacity =
      adjustCapacityForPopulationRatio(totalCapacity, subscription);
    //log.info("subCapacity=" + subCapacity);
    //log.info("adjustCapacityForTariffRates()=" + adjustCapacityForTariffRates(timeslot, subCapacity, subscription));
    return adjustCapacityForTariffRates(timeslot, subCapacity, subscription);
  }

  private double
    adjustCapacityForPopulationRatio (double capacity,
                                      TariffSubscription subscription)
  {
    //log.info("adjustCapacityForPopulationRatio()");
    //log.info("subscription.getCustomersCommitted()=" + subscription.getCustomersCommitted());
    //log.info("parentBundle.getPopulation()=" + parentBundle.getPopulation());
    double popRatio =
      getPopulationRatio(subscription.getCustomersCommitted(),
                         parentBundle.getPopulation());
    logCapacityDetails(logIdentifier + ": population ratio = " + popRatio);
    return capacity * popRatio;
  }

  private double getPopulationRatio (int customerCount, int population)
  {
    return ((double) customerCount) / ((double) population);
  }

  protected double
    adjustCapacityForTariffRates (int timeslot, double baseCapacity,
                                  TariffSubscription subscription)
  {
    if ((baseCapacity - 0.0) < 0.01)
      return baseCapacity;

    double chargeForBase =
      subscription.getTariff().getUsageCharge(service.getTimeslotRepo().getTimeForIndex(timeslot),
                                              baseCapacity,
                                              subscription.getTotalUsage());
    double rateForBase = chargeForBase / baseCapacity;

    double benchmarkRate =
      // changed, to use propagated 'timeslot' instead of current time,
      // in cases we are late 
      //
      // 
      //capacityStructure.benchmarkRates.get(service.getTimeService().getHourOfDay());
      capacityStructure.benchmarkRates.get(service.getTimeslotRepo().getTimeForIndex(timeslot).toDateTime(DateTimeZone.UTC).getHourOfDay());
    double rateRatio = rateForBase / benchmarkRate;

    double tariffRatesFactor = determineTariffRatesFactor(rateRatio);
    logCapacityDetails(logIdentifier + ": tariff rates factor = "
                       + tariffRatesFactor);
    return baseCapacity * tariffRatesFactor;
  }

  private double determineTariffRatesFactor (double rateRatio)
  {
    switch (capacityStructure.elasticityModelType) {
    case CONTINUOUS:
      return determineContinuousElasticityFactor(rateRatio);
    case STEPWISE:
      return determineStepwiseElasticityFactor(rateRatio);
    default:
      throw new Error("Unexpected elasticity model type: "
                      + capacityStructure.elasticityModelType);
    }
  }

  private double determineContinuousElasticityFactor (double rateRatio)
  {
    double percentChange = (rateRatio - 1.0) / 0.01;
    double elasticityRatio =
      Double.parseDouble(capacityStructure.elasticityModelXml
              .getAttribute("ratio"));

    String range = capacityStructure.elasticityModelXml.getAttribute("range");
    String[] minmax = range.split("~");
    double low = Double.parseDouble(minmax[0]);
    double high = Double.parseDouble(minmax[1]);

    return Math.max(low,
                    Math.min(high, 1.0 + (percentChange * elasticityRatio)));
  }

  private double determineStepwiseElasticityFactor (double rateRatio)
  {
    double[][] elasticity = null;
    if (elasticity == null) {
      elasticity =
        ParserFunctions
                .parseMapToDoubleArray(capacityStructure.elasticityModelXml
                        .getAttribute("map"));
    }
    if (Math.abs(rateRatio - 1) < 0.01 || elasticity.length == 0)
      return 1.0;
    PowerType powerType = parentBundle.getPowerType();
    if (powerType.isConsumption() && rateRatio < 1.0)
      return 1.0;
    if (powerType.isProduction() && rateRatio > 1.0)
      return 1.0;

    final int RATE_RATIO_INDEX = 0;
    final int CAPACITY_FACTOR_INDEX = 1;
    double rateLowerBound = Double.NEGATIVE_INFINITY;
    double rateUpperBound = Double.POSITIVE_INFINITY;
    double lowerBoundCapacityFactor = 1.0;
    double upperBoundCapacityFactor = 1.0;
    for (int i = 0; i < elasticity.length; ++i) {
      double r = elasticity[i][RATE_RATIO_INDEX];
      if (r <= rateRatio && r > rateLowerBound) {
        rateLowerBound = r;
        lowerBoundCapacityFactor = elasticity[i][CAPACITY_FACTOR_INDEX];
      }
      if (r >= rateRatio && r < rateUpperBound) {
        rateUpperBound = r;
        upperBoundCapacityFactor = elasticity[i][CAPACITY_FACTOR_INDEX];
      }
    }
    return (rateRatio < 1)? upperBoundCapacityFactor: lowerBoundCapacityFactor;
  }

  @Override
  public String getCapacityName ()
  {
    return capacityStructure.capacityName;
  }

  @Override
  public CapacityBundle getParentBundle ()
  {
    return parentBundle;
  }

  protected double truncateTo2Decimals (double x)
  {
    double fract, whole;
    if (x > 0) {
      whole = Math.floor(x);
      fract = Math.floor((x - whole) * 100) / 100;
    }
    else {
      whole = Math.ceil(x);
      fract = Math.ceil((x - whole) * 100) / 100;
    }
    return whole + fract;
  }

  protected void logCapacityDetails (String msg)
  {
    if (service.getCapacityDetailsLogging() == true) {
      log.info(msg);
    }
  }

  @Override
  public String toString ()
  {
    return this.getClass().getCanonicalName() + ":" + logIdentifier;
  }

  // Convenience class to unify the interface to
  // WeatherReport and WeatherForecastPrediction.
  private class Weather
  {
    final double temperature;
    final double windSpeed;
    final double windDirection;
    final double cloudCover;

    Weather (WeatherReport report)
    {
      temperature = report.getTemperature();
      windSpeed = report.getWindSpeed();
      windDirection = report.getWindDirection();
      cloudCover = report.getCloudCover();
    }

    Weather (WeatherForecastPrediction prediction)
    {
      temperature = prediction.getTemperature();
      windSpeed = prediction.getWindSpeed();
      windDirection = prediction.getWindDirection();
      cloudCover = prediction.getCloudCover();
    }

    double getTemperature ()
    {
      return temperature;
    }

    double getWindSpeed ()
    {
      return windSpeed;
    }

    double getWindDirection ()
    {
      return windDirection;
    }

    double getCloudCover ()
    {
      return cloudCover;
    }

  }
  
  
  // adding data access methods
  
  @Override
  public ArrayRealVector getPredictedEnergy(TariffSubscription subscription,
      int recordLength, int currentTimeslot) throws Exception {
    CapacityProfile predictedEnergyProfile = getCurrentForecast(currentTimeslot);
    // elasticity
    //if (ConfigServerBroker.useElasticity()) {
      predictedEnergyProfile = adjustCapacityProfileForTariffRates(predictedEnergyProfile, currentTimeslot, subscription);
    //}
    //log.info("defaultcaporig " + Arrays.toString(predictedEnergyProfile.values.toArray()));
    return convertEnergyProfileFromServerToBroker(predictedEnergyProfile, recordLength);
  }

  /**
   * Implementing elasticity adjustment
   * 
   * Note: TODO This is an other place where we assume that 
   * server's capacity profile starts from current timeslot
   *  
   * @param predictedEnergyProfile
   * @param currentTimeslot
   * @param subscription
   * @return
   */
  protected CapacityProfile adjustCapacityProfileForTariffRates(
      CapacityProfile predictedEnergyProfile, int currentTimeslot, 
      TariffSubscription subscription) {
    
    List<Double> adjustedProfile = new ArrayList<Double>();
    for (int i = 0; i < predictedEnergyProfile.NUM_TIMESLOTS; ++i) {
      double c = predictedEnergyProfile.getCapacity(i);
      double adjusted = adjustCapacityForTariffRates(currentTimeslot + i, c, subscription);
      adjustedProfile.add(adjusted);
    }
    
    return new CapacityProfile(adjustedProfile);
  }

  /**
   * NOTE: subtle conversion that caused a bug - we use predictions
   * starting next time-step, but forecastCapacities are assumed
   * to be from current - so to return data to broker we need to
   * offset the record by 1
   */
  protected ArrayRealVector convertEnergyProfileFromServerToBroker(
      CapacityProfile predictedEnergyProfile, int recordLength) throws Exception {
    //log.info("scaleEnergyProfile()");
    //log.info("predictedEnergyProfile" + Arrays.toString(predictedEnergyProfile.values.toArray()));
    int profileLength = predictedEnergyProfile.NUM_TIMESLOTS;
    // verify divides
    boolean divides = (recordLength / profileLength * profileLength) == recordLength; 
    if (!divides) {
      throw new Exception("How come profileLength doesn't divide recordLength");
    }
    //log.info("recordLength=" + recordLength);
    ArrayRealVector result = new ArrayRealVector(recordLength);
    
    for (int i = 0; i < recordLength; ++i) {
      result.setEntry(i, predictedEnergyProfile.getCapacity( (i + 1) % profileLength ));
      //log.info("appending " + predictedEnergyProfile.getCapacity( i % profileLength ) + " at " + i);
    }
    
    //log.info("result" + Arrays.toString(result.toArray()));
    return result;   
  }

  /**
   * NOTE: subtle conversion that caused a bug - we use predictions
   * starting next time-step, but forecastCapacities are assumed
   * to be from current - so to return data to broker we need to
   * offset the record by 1
   */
  @Override
  public void convertEnergyProfileFromBrokerToServer(RealVector originatorEnergy,
      int currentTimeslot) {
    
    final int brokerEnergyRecordLength = originatorEnergy.getDimension();
    for (int i = 0; i < CapacityProfile.NUM_TIMESLOTS; ++i) {
      //log.info("updateForecastCapacities(" + (currentTimeslot + i) + ")=" +  originatorEnergy.getEntry(i));
      int i_minus_1_that_will_work_for_modulo = CapacityProfile.NUM_TIMESLOTS + i - 1;
      forecastCapacities.put(currentTimeslot + i, originatorEnergy.getEntry( i_minus_1_that_will_work_for_modulo % CapacityProfile.NUM_TIMESLOTS ));
    }
  }

  @Override
  public void clearSubscriptionRelatedData() {
    // no such data here currently
  }

  private WeatherReport getCurrentWeatherReport(int currentTimeslot) {
    // we don't want to run into sync issues
    // currentWeatherReport = service.getWeatherReportRepo().currentWeatherReport();
    
    // if missing/outdated - update cache
    if (null == currentWeatherReport || 
        currentWeatherReport.getTimeslotIndex() != currentTimeslot) {
      List<WeatherReport> allWeatherReports = service.getWeatherReportRepo().allWeatherReports(currentTimeslot);
      for (WeatherReport report : allWeatherReports) {
        if (report.getTimeslotIndex() == currentTimeslot) {
          currentWeatherReport = report;
          break;
        }
      }
    }
    
    // if still missing/outdated - meaning we haven't found current
    if (null == currentWeatherReport || 
        currentWeatherReport.getTimeslotIndex() != currentTimeslot) {
      log.error("WeatherReport missing for timeslot " + currentTimeslot);
    }
    
    return currentWeatherReport;   
  }

  private WeatherForecast getCurrentWeatherForecast(int currentTimeslot) {
    // we don't want to run into sync issues
    // currentWeatherForecast = service.getWeatherReportRepo().currentWeatherForecast();
    
    // if missing/outdated - update cache
    if (null == currentWeatherForecast ||
        currentWeatherForecast.getTimeslotIndex() != currentTimeslot) {
      List<WeatherForecast> allWeatherForecasts = service.getWeatherForecastRepo().allWeatherForecasts(currentTimeslot);
      for (WeatherForecast forecast : allWeatherForecasts) {
        if (forecast.getTimeslotIndex() == currentTimeslot) {
          currentWeatherForecast = forecast;
          break;
        }
      }
    }
    
    // if still missing/outdated - meaning we haven't found current
    if (null == currentWeatherForecast ||
        currentWeatherForecast.getTimeslotIndex() != currentTimeslot) {
      log.error("WeatherForecast missing for timeslot " + currentTimeslot);
    }
    
    return currentWeatherForecast;
  }

} // end class



