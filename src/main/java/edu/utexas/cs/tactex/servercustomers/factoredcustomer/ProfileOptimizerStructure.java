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

import org.w3c.dom.*;

import edu.utexas.cs.tactex.servercustomers.factoredcustomer.CapacityProfile.PermutationRule;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.CapacityBundle;

import org.apache.log4j.Logger;
import org.springframework.util.Log4jConfigurer;

/**
 * Data-holder class for parsed configuration elements that control the 
 * capacity profile optimization of a capacity bundle. Relevant members 
 * are declared final in the package scope.
 *
 * @author Prashant Reddy
 */
public final class ProfileOptimizerStructure
{
    enum UsageChargeStance { NEUTRAL, BENEFIT, THRESHOLD }
    enum ProfileSelectionMethod { BEST_UTILITY, LOGIT_CHOICE }
    
    private static final double DEFAULT_REACTIVITY_FACTOR = 1.0; 
    private static final double DEFAULT_RECEPTIVITY_FACTOR = 1.0; 
    private static final double DEFAULT_RATIONALITY_FACTOR = 1.0; 
    
    private static final UsageChargeStance DEFAULT_USAGE_CHARGE_STANCE = UsageChargeStance.BENEFIT;   
    private static final double DEFAULT_USAGE_CHARGE_PERCENT_BENEFIT = 0.01;  // 1% improvement 
    
    private static final double DEFAULT_PROFILE_CHANGE_WEIGHT = -1.0;
    private static final double DEFAULT_BUNDLE_VALUE_WEIGHT = +10.0;
    
    private final CustomerStructure customerStructure;
    private final CapacityBundle capacityBundle;
    
    final boolean receiveRecommendations;
    final PermutationRule permutationRule;
    final boolean raconcileRecommendations;
    
    //final ProfileSelectionMethod profileSelectionMethod = ProfileSelectionMethod.LOGIT_CHOICE;
    final ProfileSelectionMethod profileSelectionMethod = 
        //(ConfigServerBroker.getProfileSelectionMethod() == ConfigServerBroker.ProfileSelectionMethod.BEST_UTILITY) 
        //    ? ProfileSelectionMethod.BEST_UTILITY 
        //    : ProfileSelectionMethod.LOGIT_CHOICE; // 
            ProfileSelectionMethod.BEST_UTILITY;

    // factors controlling responsiveness to recommendation
    final double reactivityFactor;  // [0.0, 1.0]
    final double receptivityFactor;  // [0.0, 1.0]
    final double rationalityFactor;  // [0.0, 1.0]
    
    // required percent benefit in usage charge vs. forecast profile
    final UsageChargeStance usageChargeStance;
    final double usageChargePercentBenefit;
    final double usageChargeThreshold;  // [0.0, +inf)

    // scoring weights of other factors relative to fixed usage charge weight of +/-1.
    final double profileChangeWeight;  // (-inf, 0.0]
    final double bundleValueWeight;  //  [0.0, inf]
    static private Logger log = Logger.getLogger(ProfileOptimizerStructure.class);


    
    ProfileOptimizerStructure(CustomerStructure structure, CapacityBundle bundle, Element xml)
    {
        customerStructure = structure;
        capacityBundle = bundle;        
        
        if (xml == null) {
            //log.info("xml is null");
            receiveRecommendations = false;
            raconcileRecommendations = false;
            permutationRule = null;
            reactivityFactor = DEFAULT_REACTIVITY_FACTOR; 
            receptivityFactor = DEFAULT_RECEPTIVITY_FACTOR; 
            rationalityFactor = DEFAULT_RATIONALITY_FACTOR; 
            usageChargeStance = DEFAULT_USAGE_CHARGE_STANCE;   
            usageChargePercentBenefit = DEFAULT_USAGE_CHARGE_PERCENT_BENEFIT;   
            usageChargeThreshold = Double.NaN;
            profileChangeWeight = DEFAULT_PROFILE_CHANGE_WEIGHT;
            bundleValueWeight = DEFAULT_BUNDLE_VALUE_WEIGHT;
        } else {
            //log.info("xml is not null");
            receiveRecommendations = Boolean.parseBoolean(xml.getAttribute("recommendation"));
            //log.info("receiveRecommendations " + receiveRecommendations);
            raconcileRecommendations = Boolean.parseBoolean(xml.getAttribute("reconcile"));
            permutationRule = Enum.valueOf(PermutationRule.class, xml.getAttribute("permutationRule"));
            
            Element responseFactorsElement = (Element) xml.getElementsByTagName("responseFactors").item(0);
            reactivityFactor = Double.parseDouble(responseFactorsElement.getAttribute("reactivity"));
            receptivityFactor = Double.parseDouble(responseFactorsElement.getAttribute("receptivity"));
            rationalityFactor = Double.parseDouble(responseFactorsElement.getAttribute("rationality"));
            
            Element constraintsElement = (Element) xml.getElementsByTagName("constraints").item(0);
            usageChargeStance = Enum.valueOf(UsageChargeStance.class, constraintsElement.getAttribute("usageChargeStance"));
            String percentBenefitString = constraintsElement.getAttribute("percentBenefit");
            usageChargePercentBenefit = percentBenefitString.isEmpty() ? Double.NaN : Double.parseDouble(percentBenefitString);
            String thresholdString = constraintsElement.getAttribute("threshold");
            usageChargeThreshold = thresholdString.isEmpty() ? Double.NaN : Double.parseDouble(thresholdString);
            
            Element scoringWeightsElement = (Element) xml.getElementsByTagName("scoringWeights").item(0);
            profileChangeWeight = Double.parseDouble(scoringWeightsElement.getAttribute("profileChange"));
            bundleValueWeight = Double.parseDouble(scoringWeightsElement.getAttribute("bundleValue"));
        }
    }
    
    CustomerStructure getCustomerStructure()
    {
        return customerStructure;
    }
    
    CapacityBundle getCapacityBundle()
    {
        return capacityBundle;
    }
    
} // end class

