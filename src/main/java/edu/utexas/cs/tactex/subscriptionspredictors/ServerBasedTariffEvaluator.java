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
package edu.utexas.cs.tactex.subscriptionspredictors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
//import org.powertac.common.ConfigServerBroker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;
import org.powertac.common.TariffEvaluationHelper;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TimeService;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.spring.SpringApplicationContext;

import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;

/**
 * Tariff evaluator that is based on the simulator's code.
 * Comments in brackets "[...]" point out major diffs between our code and the
 * simulator's code.
 *
 * Tariff evaluation process intended to simplify customer models.
 * There should be one of these created for each CustomerInfo
 * instance within a customer model, since
 * tariff cost values are cached, and are dependent on PowerType.
 * 
 * @author John Collins
 */
public class ServerBasedTariffEvaluator 
{
  static private Logger log = Logger.getLogger(ServerBasedTariffEvaluator.class.getName());

  // component dependencies
  //TariffRepo tariffRepo;

  // access to customer model
  private ServerCustomerModelAccessor accessor;
  private CustomerInfo customerInfo;
  
  private TariffRepoMgr tariffRepoMgr;

  // inconvenience factors
  //private double touFactor = Math.min(0.2, ConfigServerBroker.getTOUFactorCap()); //0.2; // uncomment to use ConfigServerBroker
  private double touFactorCap = 0.0;// disabling touFactor since it's incorrect
  private double touFactor = Math.min(0.2, touFactorCap); // 0.2;
  private double tieredRateFactor = 0.1;
  private double variablePricingFactor = 0.5;
  private double interruptibilityFactor = 0.2;

  // amortization period for negative signup payments
  private long signupFeePeriod = 6 * TimeService.HOUR;

  // profile cost analyzer
  private TariffEvaluationHelper helper;

  // per-customer parameter settings
  private int chunkSize = 1; // max size of allocation chunks
  private int maxChunkCount = 200; // max number of chunks
  private int tariffEvalDepth = 5; // # of tariffs/powerType to eval
  private boolean autonomous = false; // ConfigServerBroker.isAutonomous(); // false; // true; // false;
  private double inertia = 0.8;
  private double rationality = 0.9;
  private double inconvenienceWeight = 0.2;
  private double tariffSwitchFactor = 0.04;
  private double preferredDuration = 6;
  private boolean evaluateAllTariffs = false;

  // state
  private int evaluationCounter = 0;
  private HashMap<Tariff, EvalData> evaluatedTariffs;
  private HashMap<Tariff, Integer> allocations;

  // algorithm parameters - needed for numerical stablity
  private double lambdaMax = 50.0;
  private double maxLinearUtility = 7.0;
  private int stdDuration = 2; // two-day standardized profile length
  private int profileLength = 7 * 24; // length of customer-supplied profile



  public ServerBasedTariffEvaluator (ServerCustomerModelAccessor cma, TariffRepoMgr tariffRepoMgr)
  {
    accessor = cma;
    customerInfo = cma.getCustomerInfo();
    this.tariffRepoMgr = tariffRepoMgr;
    helper = new TariffEvaluationHelper();
    evaluatedTariffs = new HashMap<Tariff, EvalData>();
    allocations = new HashMap<Tariff, Integer>();
    if (autonomous) {
      this.inertia = 0; // 0.8;
      this.rationality = 1.0; // 0.0; // 1.0; 
    }
  }


  // convenience method for logging support
  private String getName()
  {
    return customerInfo.getName();
  }


  /**
   * Delegates profile cost factors to helper.
   */
  public void initializeCostFactors (double wtExpected, double wtMax,
                                     double wtRealized, double soldThreshold)
  {
    helper.initializeCostFactors(wtExpected, wtMax, wtRealized, soldThreshold);
  }


  /**
   * Initializes the per-tariff inconvenience factors.
   * These are not normalized; it is up to the customer model to normalize the
   * per-tariff and cross-tariff factors as appropriate
   */
  public void initializeInconvenienceFactors (double touFactor,
                                              double tieredRateFactor,
                                              double variablePricingFactor,
                                              double interruptibilityFactor)
  {
    //this.touFactor = Math.min(touFactor, ConfigServerBroker.getTOUFactorCap()); //touFactor; // uncomment to use ConfigServerBroker
    this.touFactor = Math.min(touFactor, touFactorCap); //touFactor;
    this.tieredRateFactor = tieredRateFactor;
    this.variablePricingFactor = variablePricingFactor;
    this.interruptibilityFactor = interruptibilityFactor;
  }


  /**
   * Initializes the per-timeslot regulation-capacity estimates.
   * All three represent per-timeslot estimates of exercised regulation
   * capacity, and are applicable only for tariffs with regulation rates.
   * Note that the expectedDischarge parameter only applies to
   * storage devices that can be discharged (batteries, pumped storage).
   * Values are from the customer's viewpoint, so curtailment and discharge
   * are negative (less energy for the customer) and down-regulation
   * is positive.
   * Default value for each of these factors is zero.
   */
  public void initializeRegulationFactors (double expectedCurtailment,
                                           double expectedDischarge,
                                           double expectedDownRegulation)
  {
    double expCurtail = expectedCurtailment;
    if (expCurtail > 0.0) {
      log.error(getName() + ": expectedCurtailment " + expCurtail
                + " must be non-positive");
      expCurtail = 0.0;
    }
    double expDis = expectedDischarge;
    if (expDis > 0.0) {
      log.error(getName() + ": expectedDischarge " + expDis
                + " must be non-positive");
      expDis = 0.0;
    }
    double expDown = expectedDownRegulation;
    if (expDown < 0.0) {
      log.error(getName() + ": expectedDownRegulation " + expDown
                + " must be non-negative");
      expDown = 0.0;
    }
    helper.initializeRegulationFactors(expCurtail, expDis, expDown);
  }


  // parameter settings
  /**
   * Sets the target size of allocation chunks. Default is 1. Actual
   * chunk size will be at least 0.5% of the population size.
   */
  public ServerBasedTariffEvaluator withChunkSize (int size)
  {
    if (size > 0)
      chunkSize = size;
    else
      log.error("chunk size " + size + " < 0");
    return this;
  }


  /**
   * Sets the number of tariffs/broker of each applicable PowerType
   * to consider. Default is 5, which means that only the 5 most recent
   * tariffs of each applicable PowerType from each broker are considered.
   * So if there are 10 brokers, and the PowerType is ELECTRIC_VEHICLE,
   * the applicable PowerTypes would be CONSUMPTION, INTERRUPTIBLE_CONSUMPTION,
   * and ELECTRIC_VEHICLE. If each broker has published at least five tariffs
   * for each of these types, the default value of 5 would result in evaluating
   * up to 150 alternative tariffs in addition to the currently subscribed
   * tariff(s).
   */
  public ServerBasedTariffEvaluator withTariffEvalDepth (int depth)
  {
    tariffEvalDepth = depth;
    return this;
  }


  /**
   * If true, then tariff evaluations are not saved; instead, all tariffs
   * are evaluated each time. This is needed for customers that generate
   * usage profiles that are sensitive to current conditions or state.
   */
  public ServerBasedTariffEvaluator withEvaluateAllTariffs (boolean value)
  {
    evaluateAllTariffs = value;
    return this;
  }


  /**
   * Sets the steady-state evaluation inertia for the customer. This is a
   * value in [0,1], where 0 is no inertia (always evaluates), and 1 is
   * total couch-potato inertia (never bothers to evaluate). The instantaneous
   * value starts at zero, so customers will have a chance to switch away
   * from the default tariff at the beginning of a sim, and is temporarily
   * set to zero when a tariff is revoked. Default value is 0.8.
   */
  public ServerBasedTariffEvaluator withInertia (double inertia)
  {
    this.inertia = inertia;
    if (autonomous) {
      this.inertia = 0;
    }
    return this;
  }


  /**
   * Sets the level of rationality for this customer.
   * Household customers are expected to have lower rationality than
   * business/industrial/institutional customers.
   * 1.0 is fully rational, 0.5 is quite irrational. A value of zero will
   * result in random choices. Default value is 0.9.
   */
  public ServerBasedTariffEvaluator withRationality (double rationality)
  {
    this.rationality = rationality;
    if (rationality < 0.0) {
      log.error("Rationality " + rationality + "< 0.0");
      this.rationality = 0.01;
    }
    else if (rationality > 1.0) {
      log.error("Rationality " + rationality + "> 1.0");
      this.rationality = 1.0;
    }
    if (autonomous) {
      this.rationality = 1.0; // 0.0; // 1.0;
    }
    return this;
  }


  /**
   * Sets the weight given to inconvenience (as opposed to cost)
   * in computing tariff utility. Must be
   * in the range [0,1]. Default value is 0.2.
   */
  public ServerBasedTariffEvaluator withInconvenienceWeight (double weight)
  {
    this.inconvenienceWeight = weight;
    return this;
  }


  /**
   * Sets the inconvenience of switching tariffs. Default value is 0.04.
   */
  public ServerBasedTariffEvaluator withTariffSwitchFactor(double factor)
  {
    this.tariffSwitchFactor = factor;
    return this;
  }


  /**
   * Sets the preferred maximum contract duration in days. For tariffs
   * having a non-zero early-withdrawal fee, this is the period after which
   * the cost of withdrawal is discounted. It is also the standard period
   * over which usage cost is compared against signup/withdrawal payments.
   * Default value is 6 days.
   */
  public ServerBasedTariffEvaluator withPreferredContractDuration (double days)
  {
    this.preferredDuration  = days;
    return this;
  }


  /**
   * Sets the length of the customer-supplied profile. Used internally and
   * for test support.
   */
  void setProfileLength (int length)
  {
    profileLength = length;
  }


  int getProfileLength ()
  {
    return profileLength;
  }


  /**
   * Returns the eval scale factor, the ratio of the stdDuration to the
   * preferredDuration.
   */
  double getScaleFactor ()
  {
    return (double)stdDuration * 24.0 / (double)getProfileLength();
  }


  /**
   * Evaluates tariffs and updates subscriptions
   * for a single customer model with a single power type.
   * Also handles tariff revoke/supersede. This requires that each
   * Customer model call this method once on each tariff publication cycle.
   * @param tariffSubscriptions 
   * @param defaultSpec 
   * @param defaultSpec 
   * @param competingTariffs 
   * @param customer 
   * @param candidateSpec 
   * @param customer2tariffEvaluations 
   * @return 
   */
  public HashMap<TariffSpecification, Double> evaluateTariffs (
      HashMap<TariffSpecification,Integer> tariffSubscriptions_,
      TariffSpecification                  defaultSpec, 
      HashMap<TariffSpecification,Double>  tariffEvaluations, 
      List<TariffSpecification>            competingTariffs, 
      CustomerInfo                         customer, 
      TariffSpecification candidateSpec)
  {
    //log.info("evaluateTariffs(), candidateSpec " + candidateSpec);
    
    HashMap<TariffSpecification, Double> result = new HashMap<TariffSpecification, Double>();

    evaluatedTariffs.clear(); 
    
    // initialize to Double subscriptions
    HashMap<TariffSpecification, Double> tariffSubscriptions = new HashMap<TariffSpecification, Double>();
    for ( Entry<TariffSpecification, Integer> entry : tariffSubscriptions_.entrySet()) {
      tariffSubscriptions.put(entry.getKey(), entry.getValue().doubleValue());
    }
    
    allocations.clear();
    HashSet<Tariff> newTariffs =
      new HashSet<Tariff>(tariffRepoMgr
              .findRecentActiveTariffs(tariffEvalDepth,
                                       customerInfo.getPowerType()));
    
    // make sure all superseding tariffs are in the set
    // Assumption: ignoring superceding tariffs
    // addSupersedingTariffs(newTariffs);

    // adjust inertia for BOG, accounting for the extra
    // evaluation cycle at ts 0
    double actualInertia =
            Math.max(0.0,
                     (1.0 - Math.pow(2, 1 - evaluationCounter)) * inertia);
    evaluationCounter += 1;
    //log.info("actualInertia " + actualInertia + " evaluationCounter " + evaluationCounter);

    Tariff defaultTariff = tariffRepoMgr.findTariffById(defaultSpec.getId());


    // scale tariffEvaluations according to TariffEvaluator.forecastCost()
    HashMap<TariffSpecification,Double>  
        scaledTariffEvaluations = scaleTariffEvaluations(tariffEvaluations);
    
    // Get the cost eval for the appropriate default tariff
    EvalData defaultEval = getDefaultTariffEval(defaultTariff, scaledTariffEvaluations.get(defaultSpec));
    
    // ensure we have the cost eval for each of the new tariffs
    // [it's ok to clear, since it's quick to fill the eval structure since
    // costs are computed]
    //evaluatedTariffs.clear(); // TODO: commenting out since now the server doesn't always recompute - is that correct?
    HashSet<Tariff> consideredTariffs = consideredTariffsSet(tariffSubscriptions, newTariffs);
    for (Tariff tariff : consideredTariffs) {
      EvalData eval = evaluatedTariffs.get(tariff);
      // Should we cache there? maybe not, since we need to always eval since
      // our estimations may change
      if (true || evaluateAllTariffs || null == eval) {
        // using try/catch because long delays (on condor) caused that new
        // tariff was added to repo after tariff-evaluations were computed, so
        // we got a null pointer here
        try {
          // compute the projected cost for this tariff
          double cost = scaledTariffEvaluations.get(tariffRepoMgr.findSpecificationById(tariff.getId()));
          double hassle = computeInconvenience(tariff);
          //log.info("Evaluated tariff " + tariff.getId()
          //         + ": cost=" + cost
          //         + ", inconvenience=" + hassle);
          eval = new EvalData(cost, hassle);
          evaluatedTariffs.put(tariff, eval);
        } catch (Exception e) {
          log.error("exception-recovery: ignoring " + tariff.getId(), e);
        }
      }
    }
    
    //// JUST FOR PRINTING
    //TreeMap<Double,Double> e2n = new TreeMap<Double, Double>();
    //TreeMap<Double, TariffSpecification> e2t = new TreeMap<Double, TariffSpecification>();
    //for (TariffSpecification spec : tariffSubscriptions.keySet()) {
    //  if ( ! competingTariffs.contains(spec) ) {
    //    double eval = evaluatedTariffs.get(tariffRepoMgr.findTariffById(spec.getId())).costEstimate;
    //    double subs = tariffSubscriptions.get(spec);
    //    e2n.put(eval, subs);
    //    e2t.put(eval, spec);
    //  }
    //}      
    //log.debug(" lwr Customer now: " + customer + " " + e2n.toString());
    //log.debug(" lwr customer tariffevals " + customer + " " + e2t.toString());
    //e2n = new TreeMap<Double, Double>();  
    //for (TariffSpecification spec : tariffSubscriptions.keySet()) {
    //  if ( competingTariffs.contains(spec) ) {
    //    double eval = evaluatedTariffs.get(tariffRepoMgr.findTariffById(spec.getId())).costEstimate;
    //    double subs = tariffSubscriptions.get(spec);
    //    e2n.put(eval, subs);
    //  }
    //}      
    //log.debug(" lwr Customer competing: " + customer + " " + e2n.toString());

    // Assumption: we ignore inertia and predict subscriptions "in the limit". 
    // Here we approximate it by running for 12 iterations (3 days prediction).
    int numIterations = 12; // 3 days
    for (int i = 0; i < numIterations; ++i) {
      if (i != 0) {
        tariffSubscriptions = new HashMap<TariffSpecification, Double>(result);
        result.clear();
      }
      // Assumption: not using the server's subscription information
      //for (TariffSubscription subscription
      //        : getTariffSubscriptionRepo().
      //        findActiveSubscriptionsForCustomer(customerInfo)) {
      for (Entry<TariffSpecification, Double> entry : tariffSubscriptions.entrySet()) {
        TariffSpecification existingSpec = entry.getKey();
        Double subs = entry.getValue();      

        // don't process 0 subs, since no migration is going to happen
        if (0 == subs)
          continue;

        // find out how many of these customers can withdraw without penalty
        double withdrawCost = existingSpec.getEarlyWithdrawPayment(); 
        double committedCount;
        double expiredCount;
        // Assumption: we don't know how many are committed, so assume the extreme case
        if (competingTariffs.contains(existingSpec)) {
          committedCount = subs;
          expiredCount = subs;
        }
        else {
          committedCount = subs;
          expiredCount = subs;
        }
        if (withdrawCost == 0.0 || expiredCount == committedCount) {
          // no need to worry about expiration
          evaluateAlternativeTariffs(/* not using server's subscription information* subscription,*/existingSpec, 
              actualInertia,
              0.0, committedCount,
              defaultTariff ,
              defaultEval, newTariffs, result);
        }
        else {
          // Evaluate expired and unexpired subsets separately
          evaluateAlternativeTariffs(/* not using server's subscription information*  subscription,*/ existingSpec, 
              actualInertia,
              0.0, expiredCount,
              defaultTariff,
              defaultEval, newTariffs, result);
          evaluateAlternativeTariffs(/* not using server's subscription information* subscription,*/ existingSpec,
              actualInertia,
              withdrawCost, committedCount - expiredCount,
              defaultTariff, 
              defaultEval, newTariffs,
              result);
        }
      }
      for (TariffSpecification spec : result.keySet()) {
        //log.info("BrokerNames in result: " + spec.getBroker().getUsername());
      }

      //// JUST FOR PRINTING
      //e2n = new TreeMap<Double, Double>();  
      //for (TariffSpecification spec : result.keySet()) {
      //  double eval = evaluatedTariffs.get(tariffRepoMgr.findTariffById(spec.getId())).costEstimate;
      //  Double subs = result.get(spec);
      //  e2n.put(eval, subs);
      //}      
      //log.debug(" lwr Customer predicted: " + customer + " " + e2n.toString()); 

      //// just for visualization
      //if (null != candidateSpec) {
      //  double candidateEvaluation = evaluatedTariffs.get(tariffRepoMgr.findTariffById(candidateSpec.getId())).costEstimate;
      //  Double subs = result.get(candidateSpec);
      //  log.debug(" lwr Customer candidate: " + customer + " {" + candidateEvaluation + "=" + subs + "}" );
      //}

    }
    
    //filtering out competitors
    for (TariffSpecification spec : competingTariffs) {
        result.remove(spec);
    }

    return result;
    // [Commented out since we predict in expactation]
    //updateSubscriptions();
  }


  public HashSet<Tariff> consideredTariffsSet(
      HashMap<TariffSpecification, Double> tariffSubscriptions,
      HashSet<Tariff> newTariffs) {
    HashSet<Tariff> consideredTariffs = new HashSet<Tariff>();
    consideredTariffs.addAll(newTariffs);
    for (TariffSpecification spec : tariffSubscriptions.keySet()){
      consideredTariffs.add(tariffRepoMgr.findTariffById(spec.getId()));
    }
    return consideredTariffs;
  }


  // Ensures that superseding tariffs are evaluated by adding them
  // to the newTariffs list
  // Assumption: ignoring superceding tariffs
  //  private void addSupersedingTariffs (HashSet<Tariff> newTariffs)
  //  {
  //    List<TariffSubscription> revokedSubscriptions =
  //            getTariffSubscriptionRepo().getRevokedSubscriptionList(customerInfo);
  //    for (TariffSubscription sub : revokedSubscriptions) {
  //      Tariff supTariff = sub.getTariff().getIsSupersededBy();
  //      if (null != supTariff && supTariff.isSubscribable())
  //        newTariffs.add(supTariff);
  //    }
  //  }


  // evaluate alternatives
  private void evaluateAlternativeTariffs (/* *Assumption: not using server's subscription information* TariffSubscription current,*/ TariffSpecification existingSpec,
                                           double inertia,
                                           double withdraw0,
                                           double population, // 'double' to have fractional expected values
                                           Tariff defaultTariff,
                                           EvalData defaultEval,
                                           Set<Tariff> initialTariffs, 
                                           HashMap<TariffSpecification,Double> result)
  {
    //log.info("evaluateAlternativeTariffs(" + existingSpec.getId() + ',' + inertia + ',' + withdraw0 + ',' + population + ',' + defaultTariff.getId() + "...");
    //log.info("evaluateAlternativeTariffs(" + existingSpec.getId() + ")");
    
    if (population == 0) 
      return;

    // Associate each alternate tariff with its utility value
    TreeSet<TariffUtility> evals = new TreeSet<TariffUtility>();
    HashSet<Tariff> tariffs = new HashSet<Tariff>(initialTariffs);
    tariffs.add(defaultTariff);

    // Check whether the current tariff is revoked, add it if not
    Tariff currentTariff = tariffRepoMgr.findTariffById(existingSpec.getId());
    boolean revoked = false;
    Tariff replacementTariff = null;
    if (currentTariff.getState() == Tariff.State.KILLED) {
      revoked = true;
      replacementTariff = currentTariff.getIsSupersededBy(); 
      if (null == replacementTariff) {
        replacementTariff = defaultTariff;
      }

      // withdraw without penalty
      withdraw0 = 0.0; 
    }
    else
      tariffs.add(currentTariff);

    //for (Tariff t : tariffs) {
      //log.info(t.getId());
    //}

    //EvalData eval0 = evaluatedTariffs.get(currentTariff);
    //double inconvenience0 = eval0.inconvenience;
    //double cost0 = eval0.costEstimate;
    //double normalizedCostBasedValue0 = computeNormalizedDifference(cost0,
    //                                             defaultEval.costEstimate);
    //double currentTariffUtility = normalizedCostBasedValue0 - inconvenienceWeight * inconvenience0;

    // for each tariff, including the current and default tariffs,
    // compute the utility
    for (Tariff tariff: tariffs) {
      try {
        //log.info("UTILITYFORTARIFF " + tariff.getId());
        EvalData eval = evaluatedTariffs.get(tariff);
        if (null == eval) {
          // some error happened earlier, ignore tariff (I don't print so log doesn't blow up in the competition)
          continue;
        }
        double inconvenience = eval.inconvenience;
        // TODO: tmp fix, scale cost - I estimate over 1 week, but this
        // might be different than preferredDuration. See all calls to 
        // scaledCost. 
        // Assumption: this scaling is dangerous, sinces it assumes 
        // the current values for my horizon length estimation and the
        // customer's preferred duration 
        double cost = eval.costEstimate;
        //log.info("cost=" + cost + " inconvenience=" + inconvenience);
        if (tariff != currentTariff
                && tariff != replacementTariff) { 
          if (!autonomous) {
            inconvenience += tariffSwitchFactor;
            //log.info("tariffSwitchFactor " + tariffSwitchFactor);
            if (tariff.getBroker() != currentTariff.getBroker()) {
              inconvenience +=
                  accessor.getBrokerSwitchFactor(revoked);
            }
          }
          cost += computeSignupCost(tariff);
          cost += withdraw0; // withdraw from current tariff
          cost += computeWithdrawCost(tariff);
          //log.info("withdraw0=" + withdraw0 + " withdrawFactor=" + withdrawFactor + " withdraw-cost=" + withdrawFactor * tariff.getEarlyWithdrawPayment());
          if (Double.isNaN(cost)) {
            log.error(getName() + ": cost is NaN for tariff " + tariff.getId());
          }
        }
        // don't consider current tariff if it's revoked
        if (!revoked || tariff != currentTariff) {
          double normalizedCostBasedValue = 
            computeNormalizedDifference(cost, defaultEval.costEstimate);
          //if (tariff.isTimeOfUse()) {
          //  log.warn("tariff " + tariff.getId() + " utility=" + utility + ", inconvenience=" + inconvenience + ", w*inc=" + inconvenienceWeight * inconvenience + ", total=" + (utility - inconvenienceWeight * inconvenience) + " instead of inconvenience=" + (inconvenience - eval.inconvenience) + ", w*inc=" + (inconvenienceWeight * (inconvenience - eval.inconvenience))+ ", total=" + (utility - (inconvenienceWeight * (inconvenience - eval.inconvenience))));
          //}
          double utility = normalizedCostBasedValue - inconvenienceWeight * inconvenience;
          //log.info("adding TariffUtility(" + tariff.getId() + ", " + constrainUtility(utility) + " (" + utility + ")");
          if (Double.isNaN(utility)) {
            log.error(getName() + ": utility is NaN for tariff "
                      + tariff.getId());
          }
          //if ( ! ConfigServerBroker.eliminateParetoDominatedTariffs() || 
          //     (normalizedCostBasedValue > normalizedCostBasedValue0 || inconvenience < inconvenience0) )
            evals.add(new TariffUtility(tariff, constrainUtility(utility)));
        }
      } catch (Throwable e) {
        log.error("exception-recovery: TariffUtility", e);
      }
    }
    
    
    // We now have utility values for each possible tariff.
    // Time to make some choices -
    // -- first, compute lambda from rationality
    // -- second, we have to compute the sum of transformed utilities
    
    if (autonomous && (rationality > 1 - 1e-6) ) { // enabling perfect rationality
      // find the maximum utility
      double maxUtil = -Double.MAX_VALUE;
      for (TariffUtility util : evals) {        
        if (util.utility > maxUtil)
          maxUtil = util.utility;
      }      
      // in case there are more than max util - divide evenly
      int numMaxUtil = 0; 
      for (TariffUtility util : evals) {
        boolean isMaxUtil = util.utility == maxUtil; // > maxUtil - 1e-6;
        if (isMaxUtil) 
          numMaxUtil += 1;
      }
      // uniform probabilities to all with maxUtil value
      for (TariffUtility util : evals) {
        boolean isMaxUtil = util.utility == maxUtil; // > maxUtil - 1e-6;
        util.probability = isMaxUtil ? (1.0 / numMaxUtil) : 0;        
      } 
    }
    else {
      double logitDenominator = 0.0;
      double lambda = Math.pow(lambdaMax, rationality) - 1.0;
      for (TariffUtility util : evals) {
        try {
          logitDenominator +=
                  Math.exp(lambda * util.utility);
        } catch (Throwable e) {
          log.error("except-recovery", e);
        }
      }
      // then we can compute the probabilities
      for (TariffUtility util : evals) {
        try {
          util.probability =
                  Math.exp(lambda * util.utility)
                  / logitDenominator;
          //log.info("util " + util.probability + ", " + util.utility);
          if (Double.isNaN(util.probability)) {
            log.error(getName() + ": Probability NAN, util=" + util.utility
                      + ", denom=" + logitDenominator
                      + ", tariff " + util.tariff);
            util.probability = 0.0;
          }
        } catch (Throwable e) {
          log.error("except-recovery", e);
        }
      }
    }

    // Assumption: (1) expected allocation, (2) ignoring inertia
    for (TariffUtility tu : evals) {
      try {
        TariffSpecification newspec = tariffRepoMgr.findSpecificationById(tu.tariff.getId());
        Double subs = result.get(newspec);
        if (null == subs) {
          subs = 0.0;        
        }
        double expectedMigration = population * tu.probability;
        subs += expectedMigration;
        result.put(newspec, subs);
      } catch (Throwable e) {
        log.error("except-recovery", e);
      }
    }
    

    // [Commented out the actual allocation and doing the expected
    // one above here]

    // int remainingPopulation = population;
    // int chunk = remainingPopulation;
    // if (customerInfo.isMultiContracting()) {
    //   // Ideally, each individual customer makes a choice.
    //   // For large populations, we do it in chunks.
    //   chunk = getChunkSize(population);
    // }
    // while (remainingPopulation > 0) {
    //   int count = (int)Math.min(remainingPopulation, chunk);
    //   remainingPopulation -= count;
    //   // allocate a chunk
    //   double inertiaSample = accessor.getInertiaSample();
    //   if (!revoked && inertiaSample < inertia) {
    //     // skip this one if not processing revoked tariff
    //     continue;
    //   }
    //   double tariffSample = accessor.getTariffChoiceSample();
    //   // walk down the list until we run out of probability
    //   boolean allocated = false;
    //   for (TariffUtility tu : evals) {
    //     if (tariffSample <= tu.probability) {
    //       addAllocation(currentTariff, tu.tariff, count);
    //       allocated = true;
    //       break;
    //     }
    //     else {
    //       tariffSample -= tu.probability;
    //     }
    //   }
    //   if (!allocated) {
    //     log.error("Failed to allocate: P=" + tariffSample);
    //   }
    // }
  }
  

  /**
   * Our evaluations (costs) are for 1 week. TariffEvaluator
   * scales them to stdDuration, so we scale accordingly
   * @param tariffEvaluations
   * @return
   */
  private HashMap<TariffSpecification, Double> scaleTariffEvaluations(
      HashMap<TariffSpecification, Double> tariffEvaluations) {
    
    HashMap<TariffSpecification, Double> scaledTariffEvaluations = 
        new HashMap<TariffSpecification, Double>();
    
    for (Entry<TariffSpecification, Double> entry : tariffEvaluations.entrySet()){
      scaledTariffEvaluations.put(
          entry.getKey(), 
          scaledCost(entry.getValue()));
    }
    
    return scaledTariffEvaluations;
  }


  /*
   * TODO: tmp fix, scale cost - I estimate over 1 week, and this
   * might be different than preferredDuration
   * Note: this scaling is not robust. myhorizon is a hard-code duplicate
   * (7) rather than taking it from a global 'Horizon' variable. 1.2 scaling is
   * also dangerous, determined only by manual inspection of the errors
   */
  private double scaledCost(double costEstimate) {
	  double cost = costEstimate;
	  cost *= ((double)stdDuration * 24.0 / getProfileLength());
    //
    //TODO: not sure the following is needed with server-based consumption
    //predictions (when I predicted from avg-arrays, predictions underestimated
    //(since tariff prices were going down => consumption going up) so the ratio
    //with fixed numbers like withdraw fees needed to be scaled a little.
    //
    // perhaps keep since non factored-customers still use array predictions?
    //
    // TODO: this is a magic number, based on approximate manual inspections, (update it after change to stdDuration?)
    cost *= 1.2; 
	  return cost;
  }


  // Customers really, really don't like paying to sign up. This computation
  // inflates the cost of signup fees by the ratio of the customer's
  // preferred duration to the duration of one tariff-publication cycle.
  // On the other hand, positive signup payments are scaled to amortize over
  // just the standard eval duration.
  double computeSignupCost (Tariff tariff)
  {
    if (tariff.getSignupPayment() < 0.0) {
      // penalize negative signup fees
      return tariff.getSignupPayment() *
          preferredDuration * TimeService.DAY / signupFeePeriod;
    }
    else {
      return tariff.getSignupPayment() * getScaleFactor();
    }
  }


  // If the tariff has a non-zero minDuration and a negative
  // earlyWithdrawPayment, then we prefer shorter values for minDuration.
  double computeWithdrawCost (Tariff tariff)
  {
    if (0 == tariff.getMinDuration()
        || 0.0 == tariff.getEarlyWithdrawPayment()) {
      return 0.0;
    }
    double annoyance = 1.0;
    if (tariff.getEarlyWithdrawPayment() < 0.0) {
      annoyance =
          (double)tariff.getMinDuration()
          / (double)(preferredDuration * TimeService.DAY);
    }
    double scale = annoyance * getScaleFactor();
    return tariff.getEarlyWithdrawPayment() * scale;
  }


  // Ensures numeric stability by constraining range of utility values.
  private double constrainUtility (double utility)
  {
    if (utility > maxLinearUtility) {
      double compressed = Math.log10(utility - maxLinearUtility);
      return Math.min(maxLinearUtility + compressed,
                      maxLinearUtility * 2);
    }
    else if (utility < -maxLinearUtility) {
      return -maxLinearUtility; // these will never be chosen anyway
    }
    else
      return utility;
  }


  // Computes the normalized difference between the cost of the default tariff
  // and the cost of a proposed tariff
  private double computeNormalizedDifference (double cost, double defaultCost)
  {
    if (defaultCost == 0) {
      // this means that capacity is 0, so we don't want any changes
      // so return small utility
      return 0;
    }
    double ndiff = (defaultCost - cost) / defaultCost;
    if (customerInfo.getPowerType().isProduction())
      ndiff = -ndiff;
    return ndiff;
  }


  // Retrieves default tariff
  // [Commented out, getting it in evaluateTariffs()]
  //private Tariff getDefaultTariff ()
  //{
  //  return ...
  //}

  private EvalData getDefaultTariffEval (Tariff defaultTariff, Double defaultTariffCost)
  {
    EvalData defaultEval = evaluatedTariffs.get(defaultTariff);
    if (null == defaultEval) {
      defaultEval =
              new EvalData(defaultTariffCost,
                           0.0);
      evaluatedTariffs.put(defaultTariff, defaultEval);
    }
    return defaultEval;
  }

  
  // [Commented out, getting this info in evaluateTariffs()]
  // Cost forecaster
  //private double forecastCost (Tariff tariff)
  //{
  //  CapacityProfile profile = accessor.getCapacityProfile(tariff);
  //  if (0 == profile.getProfile().length) {
  //    log.error("Zero-length profile for " + customerInfo.getName());
  //    return 0.0;
  //  }
  //  setProfileLength(profile.getProfile().length);
  //  // NOTE: must call the next function after the previous, since the previous writes inconv. factors
  //  double inconv = accessor.getShiftingInconvenienceFactor(tariff); // always 0 except for AdaptiveCapacityOriginator
  //  double profileCost = helper.estimateCost(tariff,
  //                                           profile.getProfile(),
  //                                           profile.getStart());
  //  if (Double.isNaN(profileCost)) {
  //    log.error(getName() + ": profile cost NaN for tariff "
  //              + tariff.getId());
  //  }
  //  //double scale = preferredDuration * 24.0 / profile.length;
  //  double scale = stdDuration * 24 / getProfileLength();
  //  if (Double.isNaN(scale)) {
  //    log.error(getName() + ": scale NaN for tariff " + tariff.getId());
  //  }
  //  log.debug("inconv profileCost=" + profileCost + " inconv=" + inconv + " scaled-charge=" + profileCost * scale + " scaled (cost+inconv)=" + (profileCost + inconv) * scale + " ratio= " + (profileCost + inconv) * scale / (profileCost * scale));
  //  return (profileCost + inconv) * scale;
  //}


  // tracks additions and deletions for tariff subscriptions
  private void addAllocation (Tariff current, Tariff newTariff, int count)
  {
    if (current == newTariff)
      // ignore no-change allocations
      return;
    // decrement the old
    Integer ac = allocations.get(current);
    if (null == ac)
      ac = -count;
    else
      ac -= count;
    allocations.put(current, ac);
    // increment the new
    ac = allocations.get(newTariff);
    if (null == ac)
      // first time on this one
      ac = count;
    else
      ac += count;
    allocations.put(newTariff, ac);
  }
  
  // updates subscriptions based on computed allocations
  // [Commented out since currently we use a different method to estimate migration]
  //  private void updateSubscriptions ()
  //  {
  //    int check = 0;
  //    for (Tariff tariff : allocations.keySet()) {
  //      int count = allocations.get(tariff);
  //      check += count;
  //      if (count < 0) {
  //        //unsubscribe
  //        TariffSubscription sub =
  //                getTariffSubscriptionRepo().findSubscriptionForTariffAndCustomer
  //                  (tariff, customerInfo);
  //        sub.unsubscribe(-count);
  //        log.info("customer " + customerInfo.getName()
  //                 + " unsubscribes " + -count
  //                 + " from tariff " + tariff.getId());
  //      }
  //      else if (count > 0) {
  //        // subscribe
  //        getTariffMarket().subscribeToTariff(tariff, customerInfo, count);
  //        log.info("customer " + customerInfo.getName()
  //                 + " subscribes " + count
  //                 + " to tariff " + tariff.getId());
  //      }
  //    }
  //    // sanity check
  //    if (check != 0) {
  //      log.error("Subscription updates do not add up for "
  //                + customerInfo.getName() + ": " + check);
  //    }
  //  }


  // inconvenience computation
  /**
   * Returns inconvenience of time-of-use rate.
   */
  public double getTouFactor ()
  {
    return touFactor;
  }


  /**
   * Returns inconvenience of tiered rate.
   */
  public double getTieredRateFactor ()
  {
    return tieredRateFactor;
  }


  /**
   * Returns inconvenience of variable pricing.
   */
  public double getVariablePricingFactor ()
  {
    return variablePricingFactor;
  }


  /**
   * Returns inconvenience of interruptibility.
   */
  public double getInterruptibilityFactor ()
  {
    return interruptibilityFactor;
  }


  /**
   * Computes composite per-tariff inconvenience of a tariff.
   */
  public double computeInconvenience (Tariff tariff)
  {
    double result = 0.0;
    // Time-of-use tariffs have multiple Rates, at least one of which
    // has a daily or weekly begin/end
    if (tariff.isTimeOfUse())
      result += touFactor;

    // Tiered tariffs have multiple Rates, at least one having
    // a non-zero tier threshold.
    if (tariff.isTiered())
      result += tieredRateFactor;

    // Variable-rate tariffs have at least one non-fixed Rate
    if (tariff.isVariableRate())
      result += variablePricingFactor;

    // Interruptible tariffs are for an interruptible PowerType, and
    // have a Rate with a maxCurtailment != 0
    if (tariff.isInterruptible())
      result += interruptibilityFactor;
    return result;
  }
  
  
  // returns the correct chunk size for a given population
  private int getChunkSize (int population)
  {
    if (population <= chunkSize)
      return population;
    else
      return Math.max(population / maxChunkCount, chunkSize);
  }


  // Spring component access ------------------------------------------
  // [Commented out since I don't have access to these]
  //  private TariffRepo getTariffRepo ()
  //  {
  //    if (null != tariffRepo)
  //      return tariffRepo;
  //    tariffRepo =
  //            (TariffRepo) SpringApplicationContext.getBean("tariffRepo");
  //    return tariffRepo;
  //  }
  //
  //  private TariffSubscriptionRepo getTariffSubscriptionRepo ()
  //  {
  //    if (null != tariffSubscriptionRepo)
  //      return tariffSubscriptionRepo;
  //    tariffSubscriptionRepo =
  //            (TariffSubscriptionRepo) SpringApplicationContext.getBean("tariffSubscriptionRepo");
  //    return tariffSubscriptionRepo;
  //  }
  //
  //  private TariffMarket getTariffMarket ()
  //  {
  //    if (null != tariffMarket)
  //      return tariffMarket;
  //    tariffMarket =
  //            (TariffMarket) SpringApplicationContext.getBean("tariffMarketService");
  //    return tariffMarket;
  //  }


  // Container for tariff-utility recording ------------------------------
  class TariffUtility implements Comparable<TariffUtility>
  {
    Tariff tariff;
    double utility;
    double probability = 0.0;

    TariffUtility (Tariff tariff, double utility)
    {
      super();
      this.tariff = tariff;
      this.utility = utility;
    }

    @Override
    // natural ordering is by decreasing utility values
      public
      int compareTo (TariffUtility other)
    {
      double result = other.utility - utility;
      if (result == 0.0)
        // consistent with equals...
        return (int) (other.tariff.getId() - tariff.getId());
      else if (result > 0.0)
        return 1;
      else
        return -1;
    }
  }
  

  // Container for tariff-evaluation data
  class EvalData
  {
    double costEstimate;
    double inconvenience;

    EvalData (double cost, double inconvenience)
    {
      super();
      this.costEstimate = cost;
      this.inconvenience = inconvenience;
    }
  }
}


