TacTex 2013-2015
=================
Code release of UT Austin's TacTex (2013-2015) Power Trading Agent 


About: 
======
This release provides the complete source code of the TacTex agent, which was a
top-performing agent in the Power Trading Agent Competition (Power TAC,
www.powertac.org) in the years 2013, 2015. The code is based off the TacTex-15
agent, which competed in the 2015 Power TAC finals. 

The code implements the agents and algorithms described in Daniel Urieli's Ph.D
dissertation "Autonomous Trading in Modern Electricity Markets", which can be
found in the following link:
http://www.cs.utexas.edu/~urieli/thesis

Related research papers can be found here:
http://www.cs.utexas.edu/~urieli/#publications

The goals of this code release is to 1) help new Power TAC researchers to get
past the initial stages of developing an agent, 2) help the Power TAC community
to use TacTex's contributions in their research on autonomous power trading
agents and future electricity markets.

You may use the code, but please keep the copyright notices attached and keep
track of what ideas you are using from this code.  

We would be interested in hearing from you regarding how easily you
were able to compile, run, and use the code. Please use our contact details
below.


Related Publications - citation information
===========================================
If you use this code for research purposes, please consider citing one or more
research papers that can be found (with corresponding Bibtex entries) in the
following links:
http://www.cs.utexas.edu/~urieli/thesis
http://www.cs.utexas.edu/~urieli/
and which include the following:

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


More information - TacTex homepage
==================================
http://www.cs.utexas.edu/users/TacTex/


TacTex team contacts:
=====================

Daniel Urieli (urieli@cs.utexas.edu)

Peter Stone (pstone@cs.utexas.edu)

Computer Science Department 
The University of Texas at Austin
Austin, TX, USA


Requirements
============
* Power TAC 2015 server (version 1.2.3)

TacTex communicates with a running instance of the the Power TAC server.
Instructions to download, install, and run the Power TAC server can 
be found at www.powertac.org. TacTex's code is based off the TacTex-15
agent, which competed in the 2015 Power TAC finals. This code can be built and
run with the 2015 version of the Power TAC simulator (version 1.2.3).  To use
the code with later versions of the simulator, some modifications to the source
code may be required.




=======================
=======================
Build/Run instructions 
=======================
=======================


TacTex was written in java, was built using maven, and was tested on Ubuntu
Linux. The build/run instructions for this code release are similar to the
build/run instructions provided with the sample broker that is provided with
the Power TAC server (we use the terms 'simulator' and 'server'
interchangably). 

For completeness, the sample-broker build/run instructions (created by John
Collins' team at UMN) are copied next, with minor adaptations.


The TacTex Broker
=================

The TacTex broker interfaces with the Power TAC simulator. It handles all
message types and operates in both wholesale and retail markets. TacTex's
operation is detailed in the publications that are mentioned above.

Without changing anything, the current version assumes the 2015 server (version
1.2.3) is running on localhost, and is not picky about passwords. You can
change the server URL by editing the broker.properties file, or by using your
own properties file.  Passwords are generally ignored outside a tournament
environment.

Import into IDE
---------------

Most developers will presumably want to work with the code using an IDE such as
[STS](http://www.springsource.org/sts). The TacTex package is a maven
project, so it works to just do File->Import->Existing Maven Projects and
select the TacTex directory (the directory containing the pom.xml file).
You can set up a simple "run configuration" to allow you to run it
from the IDE. It is an AspectJ/Java app, the main class is
`edu.utexas.cs.tactex.core.BrokerMain`, and there are no arguments
required unless you wish to specify an alternate config file or pass other
options (see below). See a high-level documentation of the code in the 
"Documentation" section.

Run from command line
---------------------

This is a maven project. You can run the broker from the command line using
maven, as

`mvn compile exec:exec [-Dexec.args="<arguments>"]`

where arguments can include:

* `--config config-file.properties` specifies an optional properties file that can set username, password, server URL, and other broker properties. If not given, the file broker.properties in the current working directory will be used. 
* `--jms-url tcp://host.name:61616` overrides the JMS URL for the sim server. In a tournament setting this value is supplied by the tournament infrastructure, but this option can be handy for local testing.
* `--repeat-count n` instructs the broker to run n sessions, completely reloading its context and restarting after each session completes. Default value is 1.
* `--repeat-hours h` instructs the broker to attempt to run sessions repeatedly for h hours. This is especially useful in a tournament situation, where the number of games may not be known, but the duration of the tournament can be approximated. If repeat-count is given, this argument will be ignored.
* `--no-ntp` if given, tells the broker to not rely on system clock synchronization, but rather to estimate the clock offset between server and broker. Note that this will be an approximation, but should at least get the broker into the correct timeslot.
* `--queue-name name` tells the broker to listen on the named queue for messages from the server. This is really only useful for testing, since the queue name defaults to the broker name, and in a tournament situation is provided by the tournament manager upon successful login.
* `--server-queue name` tells the broker the name of the JMS input queue for the server. This is also needed only for testing, because the queue name defaults to 'serverInput' and in a tournament situation is provided by the tournament manager upon successful login.

If there are no non-default arguments, and if the broker has already been compiled, then it is enough to simply run the broker as `mvn exec:exec`.

Run-time log is by default written into the file "log/broker1.trace" under the broker's directory. 

Prepare an executable jar
---------------------------

This package comes with an ability to create an "executable jar" file from source that includes all dependencies, and typically needs only a configuration file to work. You can create an executable jar as

`mvn clean package`

which will produce a file `target/name.jar`, where `name` is the "name" element near the top of the pom.xml (i.e. 'TacTex'). All classpath resources will be included (files in `src/main/resources`) in addition to the compiled classes and all dependencies. 

You can then run the agent as

`java -jar TacTex.jar [args]`

