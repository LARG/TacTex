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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.powertac.common.TimeService;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.costcurve.CostCurvesDataProcessorTrade2Avgprice;
import edu.utexas.cs.tactex.interfaces.BalancingManager;
import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.CandidateTariffSubsPredictor;
import edu.utexas.cs.tactex.interfaces.ChargeEstimator;
import edu.utexas.cs.tactex.interfaces.ContextManager;
import edu.utexas.cs.tactex.interfaces.CostCurvesDataProcessor;
import edu.utexas.cs.tactex.interfaces.CustomerPredictionManager;
import edu.utexas.cs.tactex.interfaces.EnergyPredictionManager;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.interfaces.MarketManager;
import edu.utexas.cs.tactex.interfaces.MarketPredictionManager;
import edu.utexas.cs.tactex.interfaces.OpponentPredictor;
import edu.utexas.cs.tactex.interfaces.OptimizerWrapper;
import edu.utexas.cs.tactex.interfaces.PortfolioManager;
import edu.utexas.cs.tactex.interfaces.ShiftingPredictor;
import edu.utexas.cs.tactex.interfaces.TariffActionGenerator;
import edu.utexas.cs.tactex.interfaces.TariffOptimizer;
import edu.utexas.cs.tactex.interfaces.TariffOptimizerBase;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;
import edu.utexas.cs.tactex.interfaces.TariffRevoker;
import edu.utexas.cs.tactex.interfaces.TariffSuggestionMaker;
import edu.utexas.cs.tactex.interfaces.UtilityEstimator;
import edu.utexas.cs.tactex.interfaces.WithdrawFeesOptimizer;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherForecastRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherReportRepo;
import edu.utexas.cs.tactex.shiftingpredictors.ServerBasedShiftingPredictor;
import edu.utexas.cs.tactex.shiftingpredictors.ShiftingPredictorNoShifts;
import edu.utexas.cs.tactex.subscriptionspredictors.CustomerMigrationPredictor;
import edu.utexas.cs.tactex.subscriptionspredictors.NoopMigrationPredictor;
import edu.utexas.cs.tactex.subscriptionspredictors.PolyRegCust;
import edu.utexas.cs.tactex.subscriptionspredictors.RegressionBasedMigrationPredictor;
import edu.utexas.cs.tactex.subscriptionspredictors.ServerBasedMigrationPredictor;
import edu.utexas.cs.tactex.subscriptionspredictors.SingleCustomerMigrationPredictor;
import edu.utexas.cs.tactex.tariffoptimization.ConsumptionTariffRevokeSuggestionMaker;
import edu.utexas.cs.tactex.tariffoptimization.ConsumptionTariffSuggestionMakerFixedRates;
import edu.utexas.cs.tactex.tariffoptimization.OptimizerWrapperApacheAmoeba;
import edu.utexas.cs.tactex.tariffoptimization.OptimizerWrapperApacheBOBYQA;
import edu.utexas.cs.tactex.tariffoptimization.OptimizerWrapperApachePowell;
import edu.utexas.cs.tactex.tariffoptimization.OptimizerWrapperCoordinateAscent;
import edu.utexas.cs.tactex.tariffoptimization.OptimizerWrapperGradientAscent;
import edu.utexas.cs.tactex.tariffoptimization.ProductionTariffRevokeSuggestionMaker;
import edu.utexas.cs.tactex.tariffoptimization.ProductionTariffSuggestionMakerFixedRates;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerBinaryOneShot;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerCounterPeriodic;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerFirstTimeDifferent;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerIncremental;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerOneShot;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizerRevoke;
import edu.utexas.cs.tactex.tariffoptimization.TariffOptimizierTOUFixedMargin;
import edu.utexas.cs.tactex.utilityestimation.UtilityArchitectureActionGenerator;
import edu.utexas.cs.tactex.utilityestimation.UtilityEstimatorDefaultForConsumption;
import edu.utexas.cs.tactex.utilityestimation.WithdrawFeesOptimizerHalfAvgCharge;
import edu.utexas.cs.tactex.utils.ChargeEstimatorDefault;


/**
 * Implements a factory that configures polymorphic
 * types in our broker, application-wide constants, and more. 
 * 
 * @author urieli
 *
 */
@Service
public class ConfiguratorFactoryService
implements Initializable  
{
  static private Logger log = Logger.getLogger(ConfiguratorFactoryService.class);
  
  final static private Level CONFIG_BASED_LOG_LEVELS = null;

  // @Autowired is used here instead of in the classes
  // that ConfiguratorFactoryService creates - since
  // autowiring doesn't work for them (since they are 
  // non-services). Therefore, ConfiguratorFactoryService 
  // sends the @Autowired parameters they need to their
  // constructors
  @Autowired
  private PortfolioManager portfolioManager;

  @Autowired
  private MarketManager marketManager;

  @Autowired
  private EnergyPredictionManager energyPredictionManager;

  @Autowired
  private OpponentPredictor opponentPredictorService;

  @Autowired  
  private MarketPredictionManager marketPredictionManager;
  
  @Autowired
  private CustomerPredictionManager customerPredictionManager;

  @Autowired  
  private ContextManager contextManager;

  @Autowired
  private CustomerRepo customerRepo;

  @Autowired
  private TariffRepoMgr tariffRepoMgr;
  
  @Autowired
  private BalancingManager balancingMgr;
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private TimeService timeService;

  @Autowired
  private ServerBasedWeatherReportRepo weatherReportRepo; 

  @Autowired
  private ServerBasedWeatherForecastRepo weatherForecastRepo;



  public enum BidStrategy {
    BASE,
    MKT,
    DP14,
    DP13;

    //static private HashMap<String, BidStrategy> str2enum = new HashMap<String, ConfiguratorFactoryService.BidStrategy>();
    static BidStrategy parseEnum(String s) {
      if (s.equals("base")) {	  
        return BASE;		
      }
      if (s.equals("mkt")) {	  
        return MKT;		
      }
      if (s.equals("dp14")) {	  
        return DP14;		
      }
      if (s.equals("dp13")) {	  
        return DP13;		
      }

      // default
      log.warn("parseEnum(): falling back to DP13");
      return DP13;		  
    }
  }
  
  
  /////////////////////////////////////////////////////////////////////////////
  // application-wide constants - 
  // they are an exception in the sense that they are initialized at
  // construction and it's possible to use them from other class' initialize()
  /////////////////////////////////////////////////////////////////////////////
  
  public class GlobalConstants { 

    // fields are not final so we could test them
    // with ReflectionTestUtils.
    // However, this class should have only getters
    // so it is immutable in a normal application
    // run.

    ////////////////////////////////////////////////
    private double MARKET_TRADES_ALPHA = 0.3;
    //
    private int USAGE_RECORD_LENGTH = 7 * 24; // 7d * 24h
    //
    private int LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES = 6;// 24; // magic number. to cover 24 hours?
    ////////////////////////////////////////////////



    double MARKET_TRADES_ALPHA() {
      return MARKET_TRADES_ALPHA;
    }

    int USAGE_RECORD_LENGTH() {
      return USAGE_RECORD_LENGTH;
    }
    
    public int LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES() {
      return LARGE_ENOUGH_SAMPLE_FOR_MARKET_TRADES;
    }

  }  

  final GlobalConstants CONSTANTS = new GlobalConstants();



  ////////////////////////////////////////////////
  // application-wide strategy patterns, polymorphic 
  // types, global parameters, and so on. 
  ////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////
  private TariffSuggestionMaker consumptionTariffSuggestionMaker;
  //
  private TariffSuggestionMaker productionTariffSuggestionMaker;
  //
  private ChargeEstimator chargeEstimator;
  // 
  private ShiftingPredictor shiftingPredictor;
  //
  private UtilityEstimator utilityEstimator;
  // 
  private TariffActionGenerator consumptionTariffGenerator;
  //
  private TariffActionGenerator productionTariffGenerator;
  //
  private TariffRevoker consumptionTariffRevoker;
  //
  private TariffRevoker productionTariffRevoker;
  //
  private CustomerMigrationPredictor customerMigrationPredictor;
  //
  private CandidateTariffSubsPredictor candidateTariffSubsPredictor;
  //
  private CostCurvesDataProcessor costCurvesDataProcessor;
  //////////////////////////////////////////////////////////////////////////
  
  
  
  //////////////////////////////////////////////
  // important configuration options
  //
  // reset all logs to a specific level
  private Level loggingLevel              = CONFIG_BASED_LOG_LEVELS; //Level.WARN; // Level.DEBUG; //Level.INFO//Level.OFF;//Level.ERROR;
  //
  // use cooperative strategy if number of brokers is up to coopMaxBrokers 
  private int coopMaxBrkrs          = 2; // 4;  
  //
  // whether to use opponent prediction
  private boolean useOppPred        = false;
  //
  // whether to use stair bids for exploration instead of random policy
  private boolean useStairBidExplore= false;
  //
  // whether to use fudge factor for prediction
  private boolean useFudge          = false; 
  //
  // whether to use shifting prediction during wholesale buying
  private boolean useShiftPredMkt   = true; 
  //
  // whether to use tariff revoke
  private boolean useRevoke         = false;
  //
  // whether to use solar tariffs
  private boolean useSolar          = false;
  //
  // whether to use normalized tariff evaluation
  private boolean useNormEval       = true;
  //
  private int      pubInt           = 5 * 24;
  //
  // whether to use withdraw fees
  private boolean useFees           = true;
  //
  // (in old implementation, unused) number of samples per cost curve 
  private int     curvesSample      = 1;
  //
  // bidding strategy
  private BidStrategy bidStrategy   = BidStrategy.DP13;
  //
  // length of initial period with more frequent publishing
  private int     initPubPeriod     = 7 * 24 * 2;
  //
  // number of 'stairs' in bidding (currently 1, 2, or 24)
  private int     numStairs         = 24;
  //
  // (unused) fixed margin added to bids
  private double  bidMgn            = 0;//
  //
  // whether to use intercept in LWR subscription prediction
  private boolean useIcpt           = true;// false;
  //
  // whether to use cost curve predictors, or just used past avgs
  private boolean useCostCurves     = true; // true;
  //
  private boolean useBal            = false;// false; 
  //
  // whether to use <market-tx> or <trade> for predictions
  private boolean useMtx            = true; // true;
  //
  // whether to use LWR for subscription predictions (only checked when we use the regression based predictor)
  private boolean useLWR            = true; // true;
  //
  //private boolean useDP             = true; // true;
    //
    //private boolean useDP14         = false; // true; // true;
  //
  // (unused)
  private boolean useInitialTariffs = true; // true;
  //
  // (for controlled testing) whether to use our utility architrecture, or just a baseline undercutting tariff strategy
  private boolean useUtilityArch    = true; // true;
  //////////////////////////////////////////////



  private boolean shouldRandomizeSpecs = false; //true;


    
  public ConfiguratorFactoryService() {
    super();    
    // WE SHOULDN'T PUT CODE HERE - in this application
    // services are initialized in initialize() due to 
    // cyclic dependencies. Doing anything in the constructor
    // is dangerous

    // Note that only GlobalConstants should get initialized at this point
  }

  @Override
  public void initialize (BrokerContext broker)
  {
    //this.broker = broker;
   

    // NEVER CALL ANY SERVICE METHOD FROM HERE, SINCE THEY ARE NOT GUARANTEED
    // TO BE initalize()'d. 
    // Exception: it is OK to call configuratorFactory's public
    // (application-wide) constants

    
    // note that this class also defines application constants in a special
    // section above - they are an exception in the sense that they are
    // initialized at construction and it's possible to use them from other
    // classs' initialize()
    
    setLoggingLevel();
    

    //***************************************
    // Read params from file - if file exists
    //***************************************
    File params = new File("params.txt");
    if(params.exists()) {
      try {
        FileReader input = new FileReader(params);
        BufferedReader bufRead = new BufferedReader(input);
        String myLine = null;
        while ( (myLine = bufRead.readLine()) != null)
        {    
          String[] paramVal = myLine.split(" ");
          String param = paramVal[0];
          String value = paramVal[1];
          System.out.println("Parsing: " + myLine);
          if (param.equals("coopmaxbrkrs")) {
            coopMaxBrkrs = Integer.parseInt(value);		    	
          } 
          if (param.equals("useopppred")) {
            useOppPred = Boolean.parseBoolean(value);
          }
          if (param.equals("usestairbidexplore")) {
            useStairBidExplore = Boolean.parseBoolean(value);
          }
          if (param.equals("usefudge")) {
            useFudge = Boolean.parseBoolean(value);
          }
          if (param.equals("useshiftpredmkt")) {
            useShiftPredMkt = Boolean.parseBoolean(value);
          }
          if (param.equals("userevoke")) {
            useRevoke = Boolean.parseBoolean(value);
          }
          if (param.equals("usesolar")) {
            useSolar = Boolean.parseBoolean(value);
          }
          if (param.equals("usenormeval")) {
            useNormEval = Boolean.parseBoolean(value);
          }
          if (param.equals("pubint")) {
            pubInt = Integer.parseInt(value);		    	
          } 
          if (param.equals("usefees")) {
            useFees = Boolean.parseBoolean(value);
          }
          if (param.equals("curvessample")) {
            curvesSample = Integer.parseInt(value);		    	
          }
          if (param.equals("bidstrategy")) {
            bidStrategy = BidStrategy.parseEnum(value);		    	
          } 
          if (param.equals("initpubperiod")) {
            initPubPeriod = Integer.parseInt(value);		    	
          } 
          if (param.equals("numstairs")) {
            numStairs = Integer.parseInt(value);		    	
          }
          if (param.equals("bidmgn")) {
            bidMgn = Double.parseDouble(value);		    	
          }
          if (param.equals("useicpt")) {
            useIcpt = Boolean.parseBoolean(value);		    	
          }
          if (param.equals("usecostcurves")) {
            useCostCurves = Boolean.parseBoolean(value);		    	
          }
          if (param.equals("usebal")) {
            useBal = Boolean.parseBoolean(value);		    	
          }
          if (param.equals("usemtx")) {
            useMtx = Boolean.parseBoolean(value);		    	
          }
          if (param.equals("uselwr")) {
            useLWR = Boolean.parseBoolean(value);		    	
          }
          //if (param.equals("usedp")) {
          //  useDP = Boolean.parseBoolean(value);		    	
          //}
          //if (param.equals("usedp14")) {
          //  useDP14 = Boolean.parseBoolean(value);		    	
          //}
          if (param.equals("useinitialtariffs")) {
            useInitialTariffs = Boolean.parseBoolean(value);		    	
          }
          if (param.equals("useutilityarch")) {
            useUtilityArch = Boolean.parseBoolean(value);		    	
          }
        }
        bufRead.close();
      } catch (FileNotFoundException e) {
        // TODO Auto-generated catch block
        //e.printStackTrace();
      } catch (Exception e) {
        //e.printStackTrace();
      }
    }
    else {
      //System.out.println("params.txt doesn't exist!");
    }
    System.out.println("coopmaxbrkrs: " + coopMaxBrkrs);
    System.out.println("useopppred: " + useOppPred);
    System.out.println("usestairbidexplore: " + useStairBidExplore);
    System.out.println("usefudge: " + useFudge);
    System.out.println("useshiftpredmkt: " + useShiftPredMkt);
    System.out.println("userevoke: " + useRevoke);
    System.out.println("usesolar: " + useSolar);
    System.out.println("usenormeval: " + useNormEval);
    System.out.println("pubint: " + pubInt);
    System.out.println("usefees: " + useFees);
    System.out.println("curvessample: " + curvesSample);
    System.out.println("bidstrategy: " + bidStrategy);
    System.out.println("initpubperiod: " + initPubPeriod);
    System.out.println("numstairs: " + numStairs);
    System.out.println("bidmgn: " + bidMgn);
    System.out.println("useicpt: " + useIcpt);
    System.out.println("usecostcurves: " + useCostCurves);
    System.out.println("usebal: " + useBal);
    System.out.println("usemtx: " + useMtx);
    System.out.println("uselwr: " + useLWR);
    //System.out.println("usedp: " + useDP);
    //System.out.println("usedp14: " + useDP14);
    System.out.println("useinitialtariffs: " + useInitialTariffs);
    System.out.println("useutilityarch: " + useUtilityArch);
    
    log.info("coopmaxbrkrs: " + coopMaxBrkrs);
    log.info("useopppred: " + useOppPred);
    log.info("usestairbidexplore: " + useStairBidExplore);
    log.info("usefudge: " + useFudge);
    log.info("useshiftpredmkt: " + useShiftPredMkt);
    log.info("userevoke: " + useRevoke);
    log.info("usesolar: " + useSolar);
    log.info("usenormeval: " + useNormEval);
    log.info("pubint: " + pubInt);
    log.info("usefees: " + useFees);
    log.info("curvessample: " + curvesSample);
    log.info("bidstrategy: " + bidStrategy);
    log.info("initpubperiod: " + initPubPeriod);
    log.info("numstairs: " + numStairs);
    log.info("bidmgn: " + bidMgn);
    log.info("useicpt: " + useIcpt);
    log.info("usecostcurves: " + useCostCurves);
    log.info("usebal: " + useBal);
    log.info("usemtx: " + useMtx);
    log.info("uselwr: " + useLWR);
    //log.info("usedp: " + useDP);
    //log.info("usedp14: " + useDP14);
    log.info("useinitialtariffs: " + useInitialTariffs);
    log.info("useutilityarch: " + useUtilityArch);

    // Here we allocate polymorphic types, configure the broker
    // parameters. 
    // We also send 'this' to any non-service class needing "autowired" 
    // objects, since autowiring does not work for non-services, 
    // and we don't want to initialize them with many parameters.
    // Therefore they extract what they need from 'this'
    //
    // note that by now - 'this' is constructed, and autowired
    
    
    consumptionTariffSuggestionMaker = new ConsumptionTariffSuggestionMakerFixedRates();
    productionTariffSuggestionMaker = new ProductionTariffSuggestionMakerFixedRates();
    TariffSuggestionMaker consumptionTariffRevokeSuggestionMaker = new ConsumptionTariffRevokeSuggestionMaker();
    TariffSuggestionMaker productionTariffRevokeSuggestionMaker = new ProductionTariffRevokeSuggestionMaker();
    
    chargeEstimator = new ChargeEstimatorDefault(tariffRepoMgr);

    
    // consumption shifting predictor
    // ==============================
    //
    //shiftingPredictor = new ShiftingPredictorNoShifts();
    shiftingPredictor = new ServerBasedShiftingPredictor(new ShiftingPredictorNoShifts(), this, tariffRepoMgr, customerRepo, timeslotRepo, timeService, weatherReportRepo, weatherForecastRepo);
    
    // Note: we usually don't send ConfiguratorFactoryService in the
    // constructor, see constructor of UtilityEstimatorDefaultForConsumption
    // for explanation why
    utilityEstimator = new UtilityEstimatorDefaultForConsumption(
        contextManager, customerPredictionManager, this);


    WithdrawFeesOptimizer withdrawFeesOptimizer = new WithdrawFeesOptimizerHalfAvgCharge();

    
    /////////////////////////////////////////////////////////////////////////
    // 
    // First: (DON'T CHANGE) we build all possible fixed-rate optimizers (for configuration convenience )
    // Currently they can either be *the* optimizer, or be used to find a seed for the optimizer.
    // TariffOptimizerOneShot is the traditional optimizer we had in 2013-14. 
    // ======================================================================
    TariffOptimizer tariffOptimizerOneShot = new TariffOptimizerOneShot(withdrawFeesOptimizer, tariffRepoMgr, consumptionTariffSuggestionMaker, utilityEstimator, marketPredictionManager, chargeEstimator, shiftingPredictor, this);
    // A binary search verson of TariffOptimizerOneShot
    // ================================================
    TariffOptimizer tariffOptimizerBinaryOneShot = new TariffOptimizerBinaryOneShot(withdrawFeesOptimizer, tariffRepoMgr, consumptionTariffSuggestionMaker, utilityEstimator, marketPredictionManager, chargeEstimator, shiftingPredictor, this);
    // baseline, margin-based, with tariffOptimizerOneShot => 
    // ======================================================
    TariffOptimizer tariffOptimizerTOUFixedMargin = new TariffOptimizierTOUFixedMargin(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor, tariffOptimizerOneShot);
    // Apache Amoeba 
    // =============
    TariffOptimizer tariffOptimizerAmoeba = new TariffOptimizerIncremental(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor, tariffOptimizerBinaryOneShot, new OptimizerWrapperApacheAmoeba(), utilityEstimator, marketPredictionManager, this);
    // Apache BOBYQA 
    // =============
    TariffOptimizer tariffOptimizerBOBYQA = new TariffOptimizerIncremental(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor, tariffOptimizerBinaryOneShot, new OptimizerWrapperApacheBOBYQA(), utilityEstimator, marketPredictionManager, this);
    // Apache Powell's method
    // ======================
    TariffOptimizer tariffOptimizerPowell = new TariffOptimizerIncremental(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor, tariffOptimizerBinaryOneShot, new OptimizerWrapperApachePowell(), utilityEstimator, marketPredictionManager, this);
    // Coordinate-Ascent
    // =============================
    TariffOptimizer tariffOptimizerCoordinateAscent = new TariffOptimizerIncremental(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor, tariffOptimizerBinaryOneShot, new OptimizerWrapperCoordinateAscent(), utilityEstimator, marketPredictionManager, this);
    // Gradient-Ascent
    // =============================
    TariffOptimizer tariffOptimizerGradientAscent = new TariffOptimizerIncremental(withdrawFeesOptimizer, tariffRepoMgr, chargeEstimator, shiftingPredictor, tariffOptimizerBinaryOneShot, new OptimizerWrapperGradientAscent() , utilityEstimator, marketPredictionManager, this);
    // Composites
    // ==========
    TariffOptimizerFirstTimeDifferent tariffOptimizerFirstTimeDifferentOneShotAndBinaryOneShot = new TariffOptimizerFirstTimeDifferent(tariffOptimizerOneShot, tariffOptimizerBinaryOneShot);
    TariffOptimizerFirstTimeDifferent tariffOptimizerFirstTimeDifferentOneShotAndGradientAscent = new TariffOptimizerFirstTimeDifferent(tariffOptimizerOneShot, tariffOptimizerGradientAscent);
    TariffOptimizerCounterPeriodic tariffOptimizerCounterPeriodic = new TariffOptimizerCounterPeriodic(tariffOptimizerOneShot, tariffOptimizerFirstTimeDifferentOneShotAndBinaryOneShot);
    // Second: configure the *actual* TariffOptimizer. Select one:
    // ===========================================================
    //TariffOptimizer tariffOptimizer = tariffOptimizerOneShot;
    //TariffOptimizer tariffOptimizer = tariffOptimizerBinaryOneShot;
    //TariffOptimizer tariffOptimizer = tariffOptimizerTOUFixedMargin;
    //TariffOptimizer tariffOptimizer = tariffOptimizerAmoeba;
    //TariffOptimizer tariffOptimizer = tariffOptimizerBOBYQA;
    //TariffOptimizer tariffOptimizer = tariffOptimizerPowell;
    //TariffOptimizer tariffOptimizer = tariffOptimizerGradientAscent;
    //TariffOptimizer tariffOptimizer = tariffOptimizerGradientAscent;
    // TariffOptimizer tariffOptimizer = tariffOptimizerFirstTimeDifferentOneShotAndBinaryOneShot;
    // --- This is TOU's tariff optimizer (from AAAI'16)
    // TariffOptimizer tariffOptimizer = tariffOptimizerFirstTimeDifferentOneShotAndGradientAscent;
    // --- This is TacTex'15's (from AAMAS'16)
    TariffOptimizer tariffOptimizer = tariffOptimizerCounterPeriodic;
    //
    TariffOptimizerRevoke consumptionTariffRevokeOptimizer = 
        new TariffOptimizerRevoke( withdrawFeesOptimizer, tariffRepoMgr, consumptionTariffRevokeSuggestionMaker, 
              utilityEstimator, marketPredictionManager, chargeEstimator, shiftingPredictor, this);
    consumptionTariffGenerator = new UtilityArchitectureActionGenerator(        
        energyPredictionManager, 
        tariffOptimizer,
        consumptionTariffRevokeOptimizer
        );


    TariffOptimizer productionTariffOptimizerFixedRate = 
        new TariffOptimizerOneShot(withdrawFeesOptimizer, tariffRepoMgr, productionTariffSuggestionMaker, 
              utilityEstimator, marketPredictionManager, chargeEstimator, shiftingPredictor, this);
        //new TariffOptimizerBinaryOneShot(withdrawFeesOptimizer, tariffRepoMgr, productionTariffSuggestionMaker, 
        //    utilityEstimator, marketPredictionManager, chargeEstimator, shiftingPredictor, this);
    /////////////////////////////////////////////////////////////////////////
    //
    // Original tariffOptimizer
    // ========================
    TariffOptimizer productionTariffOptimizer = productionTariffOptimizerFixedRate;
    //
    //
    TariffOptimizerRevoke productionTariffRevokeOptimizer = 
        new TariffOptimizerRevoke( withdrawFeesOptimizer, tariffRepoMgr, productionTariffRevokeSuggestionMaker, 
              utilityEstimator, marketPredictionManager, chargeEstimator, shiftingPredictor, this);

    productionTariffGenerator = new UtilityArchitectureActionGenerator(        
        energyPredictionManager, 
        productionTariffOptimizer,
        productionTariffRevokeOptimizer
        );


    
    
    //    // UtilityArchitectureTariffRevoker is similar to (and adapted from) UtilityArchitectureActionGenerator
    //    consumptionTariffRevoker = new UtilityArchitectureTariffRevoker(
    //        energyPredictionManager, 
    //        consumptionTariffRevokeOptimizer
    //        );
    //    productionTariffRevoker = new UtilityArchitectureTariffRevoker(
    //        energyPredictionManager, 
    //        productionTariffRevokeOptimizer
    //        );
    //
    

    // DON'T COMMENT-OUT (building chain of responsibility)
    //
    SingleCustomerMigrationPredictor noopPredictor = new NoopMigrationPredictor(); 
    // 
    SingleCustomerMigrationPredictor regressionPredictor = new RegressionBasedMigrationPredictor(this); 
    regressionPredictor.setNext(noopPredictor); 
    //
    SingleCustomerMigrationPredictor serverBasedPredictor = new ServerBasedMigrationPredictor(tariffRepoMgr, customerRepo); 
    serverBasedPredictor.setNext(regressionPredictor);
    //
    // END DON'T COMMENT-OUT
    
    //// Here is where we select subscription-prediction method
    //customerMigrationPredictor = new CustomerMigrationPredictor(regressionPredictor, tariffRepoMgr);
    customerMigrationPredictor = new CustomerMigrationPredictor(serverBasedPredictor, tariffRepoMgr);


    // this predictor is used only for regression based
    // customer prediction
    // ===============================================
    //candidateTariffSubsPredictor = new LWRCustOldAppache(); 
    //candidateTariffSubsPredictor = new LWRCustNewEjml(this); 
    candidateTariffSubsPredictor = new PolyRegCust();


    // my-cons,total-cons => wholesale-price
    //costCurvesDataProcessor = new CostCurvesDataProcessorCons2Avgprice();
    // total-trades => wholesale-price
    costCurvesDataProcessor = new CostCurvesDataProcessorTrade2Avgprice();

  }

  /**
   * set log level in all loggers in the simulation, 
   * unless CONFIG_BASED_LOG_LEVELS is chosen, in that case the 
   * log4j.properties file determines logging level separately
   */
  private void setLoggingLevel() {
    if (this.loggingLevel != CONFIG_BASED_LOG_LEVELS) {
      List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
      loggers.add(LogManager.getRootLogger());
      for ( Logger logger : loggers ) {
        logger.setLevel(this.loggingLevel);        
      }
    }
  }

  
  // getters for autowired variables
  
  public PortfolioManager getPortfolioManager() {
    return portfolioManager;
  }

  public MarketManager getMarketManager() {
    return marketManager;
  }

  public EnergyPredictionManager getEnergyPredictionManager() {
    return energyPredictionManager;
  }

  public OpponentPredictor getOpponentPredictorService() {
    return opponentPredictorService;
  }

  public MarketPredictionManager getMarketPredictionManager() {
    return marketPredictionManager;
  }

  public CustomerPredictionManager getCustomerPredictionManager() {
    return customerPredictionManager;
  }

  public ContextManager getContextManager() {
    return contextManager;
  }

  public CustomerRepo getCustomerRepo() {
    return customerRepo;
  }

  public TariffRepoMgr getTariffRepoMgr() {
    return tariffRepoMgr;
  }

  public BalancingManager getBalancingManager() {
    return balancingMgr;
  }

  
  // getters for strategy patterns and polymorphic types

  public TariffSuggestionMaker getConsumptionTariffSuggestionMaker() {
    return consumptionTariffSuggestionMaker;
  }
  
  public TariffSuggestionMaker getProductionTariffSuggestionMaker() {
    return productionTariffSuggestionMaker;
  }
  
  public ChargeEstimator getChargeEstimator() {
    return chargeEstimator;
  }

  public ShiftingPredictor getShiftingPredictor() {
    return shiftingPredictor;
  }

  public UtilityEstimator getUtilityEstimaor() {
    return utilityEstimator;
  }

  public TariffActionGenerator getConsumptionTariffActionGenerator() {
    return consumptionTariffGenerator ;
  }
  
  public TariffActionGenerator getProductionTariffActionGenerator() {
    return productionTariffGenerator ;
  }
  
  public TariffRevoker getConsumptionTariffRevoker() {
    return consumptionTariffRevoker;
  }

  public TariffRevoker getProductionTariffRevoker() {
    return productionTariffRevoker;
  }
  
  public CustomerMigrationPredictor getCustomerMigrationPredictor() {   
    return customerMigrationPredictor;
  }

  public CandidateTariffSubsPredictor getCandidateTariffSubsPredictor() {   
    return candidateTariffSubsPredictor;
  }

  public CostCurvesDataProcessor getCostCurvesDataProcessor() {
    return costCurvesDataProcessor;
  }

  
  // getters for configuration options

  public int getCoopMaxBrkrs() {
    return coopMaxBrkrs;
  }

  public boolean isUseOppPred() {
    return useOppPred; 
  }

  public boolean isUseStairBidExplore() {
    return useStairBidExplore;
  }

  public boolean isUseFudge() {
    return useFudge;
  }

  public boolean isUseShiftPredMkt() {
    return useShiftPredMkt;
  }

  public boolean isUseRevoke() {
    return useRevoke;
  }

  public boolean isUseSolar() {
    return useSolar;
  }

  public boolean isUseNormEval() {
    return useNormEval;	
  }

  public int getPubInt() {
    return pubInt;
  }

  public boolean isUseFees() {
    return useFees;	
  }

  public int getCurvesSample() {
    return curvesSample;
  }

  public BidStrategy getBidStrategy() {
    return bidStrategy;
  }

  public int initialPublishingPeriod() {
    return initPubPeriod;
  }

  public int getNumStairs() {	
    return numStairs;
  }

  public double getBidMgn() {
    return bidMgn;
  }

  public boolean isUseIcpt() {
    return useIcpt;
  } 

  public boolean isUseCostCurves() {
    return useCostCurves;
  } 

  public boolean isUseBal() {
    return useBal;
  } 

  public boolean isUseMtx() {
    return useMtx;
  } 

  public boolean isUseLWR() {
    return useLWR;
  } 

  //public boolean isUseDP() {
  //  return useDP;
  //} 

  //public boolean isUseDP14() {
  //  return useDP14;
  //} 
  
  public boolean isUseInitialTariffs() {
    return useInitialTariffs;
  } 
  
  public boolean isUseUtilityArch() {
    return useUtilityArch;
  } 

  public boolean randomizeSpecs() {
    return shouldRandomizeSpecs;
  }
}
