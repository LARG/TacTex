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
/**
 * 
 */
package edu.utexas.cs.tactex.utils;

import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TariffTransaction.Type;
import org.powertac.common.repo.CustomerRepo;

import edu.utexas.cs.tactex.interfaces.TariffRepoMgr;

/**
 * static methods for verifying incoming messages
 * 
 * @author urieli
 *
 */
public class MsgVerification {
  static private Logger log = Logger.getLogger(MsgVerification.class);


  // supported transaction types
  public final static EnumSet<TariffTransaction.Type> allTransactionTypes = 
    EnumSet.of(TariffTransaction.Type.PUBLISH,
        TariffTransaction.Type.PRODUCE,
        TariffTransaction.Type.CONSUME,
        TariffTransaction.Type.PERIODIC,
        TariffTransaction.Type.SIGNUP,
        TariffTransaction.Type.WITHDRAW,
        TariffTransaction.Type.REVOKE);


  // transaction types that should contain customers
  public final static EnumSet<TariffTransaction.Type> customerTransactionTypes = 
    EnumSet.of(TariffTransaction.Type.PRODUCE,
        TariffTransaction.Type.CONSUME,
        TariffTransaction.Type.PERIODIC,
        TariffTransaction.Type.SIGNUP,
        TariffTransaction.Type.WITHDRAW);


  /**
   * @param ttx
   * @param tariffRepoMgr 
   * @param customerRepo 
   * @return
   */
  public static boolean isLegalTransaction(TariffTransaction ttx, TariffRepoMgr tariffRepoMgr, CustomerRepo customerRepo) {
    Type txType = ttx.getTxType();
    TariffSpecification tariffSpec = ttx.getTariffSpec();
    CustomerInfo customerInfo = ttx.getCustomerInfo();
    int customerCount = ttx.getCustomerCount();
    
    // make sure no new transactions are introduced without
    // being taken care of in our code    
     if ( ! allTransactionTypes.contains(txType)) {
      log.error("Unknown transaction type" + txType);
      return false;
    }

    // transaction should have a tariff spec, which is matched
    // in our repository, unless its a PUBLISH tx - which might
    // not have a spec yet in the repository
     if (txType != TariffTransaction.Type.PUBLISH) {
       if (tariffSpec == null) {
         log.error("TariffTransaction type=" + txType
             + " for unknown spec");
         return false;
       }
       else {
         TariffSpecification oldSpec =
           tariffRepoMgr.findSpecificationById(tariffSpec.getId());
         if (oldSpec != tariffSpec) {
           log.error("Incoming spec " + tariffSpec.getId() + " not matched in repo");
           return false;
         }
       }
     }

    // some transactions are expected to have customer data
    if ( customerTransactionTypes.contains(txType) ) {
      // make sure we have this customer
      if (customerInfo == null || 
          customerRepo.findByNameAndPowerType(customerInfo.getName(), tariffSpec.getPowerType()) == null) {
        log.error("TariffTransaction " + ttx.getId() + " with null customer");
        return false;
      }

      // make sure customer count is positive
      if (customerCount <= 0) {
        log.error("Customer count must be positive");
        return false;
      }
    }
    return true;
  }
}
