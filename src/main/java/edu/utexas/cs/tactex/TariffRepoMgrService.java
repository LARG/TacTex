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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.Tariff;
import org.powertac.common.Tariff.State;
import org.powertac.common.TariffSpecification;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.utexas.cs.tactex.interfaces.BrokerContext;
import edu.utexas.cs.tactex.interfaces.Initializable;
import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;


/**
 * A wrapper around TariffRepo
 * @author urieli
 */
@Service
public class TariffRepoMgrService 
implements TariffRepoMgr, Initializable 
{

  static private Logger log = Logger.getLogger(TariffRepoMgrService.class);

  // this is the only place in the application that
  // tariffRepo is supposed to be used/autowired
  @Autowired
  private TariffRepo tariffRepo;
  
  //@Autowired 
  //private TimeslotRepo timeslotRepo;
  


  // ///////////////////////////////////////////////////
  // FIELDS THAT NEED TO BE INITIALIZED IN initialize()
  // EACH FIELD SHOULD BE ADDED TO test_initialize() !!!
  // ///////////////////////////////////////////////////
  private HashSet<Long> deletedTariffs;

  //private HashMap<TariffSpecification, Integer> spec2publishingTs;


  public TariffRepoMgrService ()
  {
    super();
  }


  @Override
  public void initialize (BrokerContext brokerContext)
  {


    // NEVER CALL ANY SERVICE METHOD FROM HERE, SINCE THEY ARE NOT GUARANTEED
    // TO BE initalize()'d. 
    // Exception: it is OK to call configuratorFactory's public
    // (application-wide) constants

    deletedTariffs = new HashSet<Long>(); // TODO add to test initialize    
    //spec2publishingTs = new HashMap<TariffSpecification, Integer>();// TODO add to test initialize
    
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.TariffRepoMgr#addToRepo(org.powertac.common.TariffSpecification)
   */
  @Override
  public boolean addToRepo(TariffSpecification spec) {
    // defensive programming
    if (null == spec || null == tariffRepo) {
      log.error("Cannot add spec to repo using null parameters");      
      return false;
    }
    
    if (isRemoved(spec) 
        || null != tariffRepo.findSpecificationById(spec.getId()) 
        || null != tariffRepo.findTariffById(spec.getId())) {
      log.error("Attempt to insert tariff spec with duplicate ID " + spec.getId());
      return false;
    }
    
    tariffRepo.addSpecification(spec);
    Tariff tariff = new Tariff(spec);
    // init() adds a *Tariff* to the repo as well
    boolean success = tariff.init();
    if (!success) {
      log.warn("failed to add to tariffRepo: " + spec.getId());
      tariffRepo.removeTariff(tariff);      
    }
    else {
      tariff.setState(State.ACTIVE);
    }
    return success;
  }


  @Override
  public void removeRevokedSpec(TariffSpecification spec) {
    // remove spec and tariff from repo
    long id = spec.getId();
    //tariffRepo.removeSpecification(id); // called from removeTariff() below
    //
    // server-like remove: 
    Tariff tariff = tariffRepo.findTariffById(id);
    tariffRepo.removeTariff(tariff);
    tariff.setState(State.KILLED);
    //
    // add id to 'deleted' list that i manage instead of repo
    deletedTariffs.add(id);
  }


  /**
   * Should remove what is added by addToRepo. Would make more sense
   * to send 'spec' to both of them rather then 'spec' when adding
   * and 'tariff' when removing, but there is no interface for removing a spec 
   * there
   *
   * (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.TariffRepoMgr#removeTmpSpecsFromRepo(java.util.List)
   * 
   */
  @Override
  public void removeTmpSpecsFromRepo(HashSet<TariffSpecification> specsToRemove) {
    for (TariffSpecification spec : specsToRemove) {
      removeTmpSpecFromRepo(spec);
    } 
  }

  @Override
  public void removeTmpSpecFromRepo(TariffSpecification spec) {
    Tariff tariff = tariffRepo.findTariffById(spec.getId());
    tariff.setState(State.KILLED); 
    tariffRepo.deleteTariff(tariff);
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.TariffRepoMgr#findSpecificationById(long)
   */
  @Override
  public TariffSpecification findSpecificationById(long id) {
    return tariffRepo.findSpecificationById(id);
  }


  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.TariffRepoMgr#findTariffById(long)
   */
  @Override
  public Tariff findTariffById(long id) {
    return tariffRepo.findTariffById(id);
  }

  /* (non-Javadoc)
   * @see edu.utexas.cs.tactex.interfaces.TariffRepoMgr#findTariffSpecificationsByPowerType(org.powertac.common.enumerations.PowerType)
   */
  @Override
  public List<TariffSpecification> findTariffSpecificationsByPowerType(PowerType pt) {
    return tariffRepo.findTariffSpecificationsByPowerType(pt);
  }

  @Override
  public List<TariffSpecification> findTariffSpecificationsByBroker(
      Broker broker) {
    return tariffRepo.findTariffSpecificationsByBroker(broker);
  }


  @Override
  public List<Tariff> findRecentActiveTariffs(
      int tariffEvalDepth, PowerType powerType) {
    return tariffRepo.findRecentActiveTariffs(tariffEvalDepth, powerType);
  }


  boolean isRemoved(TariffSpecification spec) {
    return deletedTariffs.contains(spec.getId());
  }


}
