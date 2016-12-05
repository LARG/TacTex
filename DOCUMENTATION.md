# Documentation
---
The following is a high level documentation of the TacTex code.

### General Code Structure
TacTex's code can be found under src/main/java/edu/utexas/cs/tactex, containing the following files and modules:

* "core" directory: Provided by Power TAC developers and mostly unmodified by us, it contains the infrastructure for controlling the broker's computation flow and communication with the server. 

* "<service-name>Service" files: Spring services containing entry points to the main modules of the broker. A service typically processes messages that arrive (asyncronously) from the server in callback functions named "handle<msg-name>()". If a service implements the "Activatable" interface, its "activate()" function is invoked at every timeslot and invokes its corresponding module. Important services to look at:

..* ConfiguratorFactoryService: A special service that 1) configures the broker's parameters, and 2) plugs-in concrete classes that define the broker's runtime behaviors. To explore with different implemented strategies/components, comment/uncomment initialization lines in this file.
..* PortfolioManagerService: The main service responsible to the broker's behavior in the tariff market. This service extends the PortfolioManagerService template provided by Power TAC's sample broker.
..* MarketManagerService: The main service responsible to the broker's behavior in the wholesale market. This service extends the MarketManagerService template provided by Power TAC's sample broker.

* "interfaces" directory: Java interfaces. 

* "utilityestimation" directory: Contains classes implementing actions' utility-prediction, which is the core of TacTex's LATTE algorithm.

* "tariffoptimization" directory: Contains classes that optimize tariffs.

* "subscriptionspredictors" directory: Contains classes that predict customer subscriptions for candidate tariffs.

* "shiftingpredictors" directory: Contains classes for predicting how customers would shift consumption in response to Time-Of-Use tariffs.

* "costcurve" directory: Contains classes for predicting the cost curve of procuring energy in the wholesale market. 

* "servercustomers" directory: Contains code of factored-customers from the servers, used by TacTex.

* "utils" directory: Contains general utilities used by TacTex. 


### Flow of Control
The simulation progresses in timeslots, each representing 1 hour in the real world. Each timeslot takes 5 seconds in simulation. The sequence of event in each timeslots is roughly: 

* A TimeslotUpdate message sent from the server to all brokers (represented in the broker's runtime log, named broker1.trace, as an xml message named "timeslot-update").

* Server simulates events such as weather conditions and customers consumption/production, and sends corresponding messages to brokers. These messages are incepted by brokers in functions named "handle<msg-name>()". 

* When the server finishes the timeslot processing, it sends a <ts-done> message to the brokers (see broker runtime log, Broker1.trace), in response to which the broker sequentially calls the "activate()" functions in its Activatable services. Each "activate()" function activates one of the broker's modules and typically results in one or more actions sent back as messages to the server.  


### Tariff Publication/Revocation Strategies

TacTex's main algorithm, named LATTE, is executed at every timestep. 
LATTE is described in detail in Daniel Urieli's Ph.D dissertation (see below).
At the core of LATTE is a tariff publication/revocation strategy, which determines
the tariffs published/revoked in the tariff market. LATTE, and correspondingly
TacTex's code, allows for easily plugging-in and exploring different tariff
publication strategies. The main strategies for publishing consumption tariffs
are briefly described next (strategies for production tariffs work similarly, starting
with the method PortfolioManagerService.checkAndPossiblyPublishProductionTariff()).
The main flow is as follows:
```
  PortfolioManagerService.checkAndPossiblyPublishConsumptionTariff()  // calls:
    UtilityArchitectureActionGenerator.selectTariffActions()          // calls:
      TariffOptimizer.Optimizetariffs()
```

TariffOptimizer is an interface that is implemented by different concrete classes
that instantiate specific tariff publication strategies. The pseudo code of these
classes' implementation appears next, where an indented method is called by the 
first method above it with a lower indentation level.
```
        // generate and evaluate a set of candidate tariffs
        class TariffOptimizerOneshot: 
          suggestTariffs
          computeShiftedEnergy (for customer-tariff pairs) // how customers will shift energy per tariff
          estimateRelevantTariffCharges // customer charges per tariff
          estimateUtilities // predict tariff utilities
            predictUtility // predict utility of a single tariff
              predictCustomerMigration // predict how customers will migrate between tariffs
              estimateUtility // estimate the resulting tariff utility for TacTex
        
        // binary search based tariff optimization (assumes convexity), efficient version of TariffOptimizerOneshot
        class TariffOptimizerBinaryOneshot: 
          suggestTariffs // suggest candidate tariffs 
          computeShiftedEnergy (for customer-tariff pairs) // how customers will shift energy per tariff
          estimateRelevantTariffCharges // customer charges per tariff
          binarySearchOptimize // assuming convexity, estimate utilities of a subset of tariffs using binary search

        // iteratively generate and evaluate tariffs; a framework for optimization algorithms 
        // such as gradient ascent, coordinate ascent, Amoeba, BOBYQA, Powell's and others.
        class TariffOptimizerIncremental:
          tariffOptimizerOneshot.findFixedRateSeed // find best fixed-rate tariff, use it as a seed
          optimizerWrapper.findOptimum // (e.g. gradient ascent, coordinate ascent)
            setStepSize // optimization step size
            Loop:
              generateNextPoint // a point is a d-dimensional tariff 
                evaluatePoint // evaluate tariff
                  value
                    computeShiftedEnergy // similarly to above
                    estimateRelevantTariffCharges // similarly to above
                    estimateUtilities // similarly to above
                      predictUtility // similarly to above
                        predictCustomerMigration // similarly to above
                        estimateUtility // similarly to above

        // a baseline method for TOU optimization, see our AAAI-16 paper below
        class TariffOptimizerTOUFixedMargin: 
          tariffOptimizerOneshot.findFixedRateSeed // find best fixed-rate tariff
          computeShiftedEnergy // how customers will shift per tariff
          predictEnergyCosts // predict TacTex's energy costs in the wholesale market
          createTariffFromFixedMargin // set selling price to a fixed margin above costs

        // optimizing tariff revocations
        class TariffOptimizerRevoke:
          removeEachTariff // simulate tariff removal
          predictUtility // predict resulting utility
            predictCustomerMigration // predict how customers will migrate
            estimateUtility // predict utility of specific removal
```


### More Documentation 
More information about the TacTex broker can be found in the following
references, that can be found in http://www.cs.utexas.edu/~urieli/:

1) "Autonomous Trading in Modern Electricity Markets"
Daniel Urieli
Ph.D. Dissertation, The University of Texas at Austin, Austin, Texas, USA, 2015.

2) "An MDP-Based Winning Approach to Autonomous Power Trading: Formalization and Empirical Analysis"
Daniel Urieli, Peter Stone
In Proc. of the 15th International Conference on Autonomous Agents and Multiagent Systems, 2016 (AAMAS-16).

3) "Autonomous Electricity Trading using Time-Of-Use Tariffs in a Competitive Market"
Daniel Urieli, Peter Stone
In Proc. of the 30th Conference on Artificial Intelligence, 2016 (AAAI-16).

4) "TacTex'13: A Champion Adaptive Power Trading Agent."
Daniel Urieli, Peter Stone
In Proc. of the 28th Conference on Artificial Intelligence, 2014 (AAAI-14).

