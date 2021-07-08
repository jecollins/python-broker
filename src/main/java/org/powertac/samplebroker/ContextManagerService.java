/*
 * Copyright (c) 2012 by the original author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powertac.samplebroker;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.powertac.common.BankTransaction;
import org.powertac.common.CashPosition;
import org.powertac.common.Competition;
import org.powertac.common.Timeslot;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles incoming context and bank messages with example behaviors. 
 * @author John Collins
 */
@Service
public class ContextManagerService
implements Initializable, Activatable
{
  static private Logger log = LogManager.getLogger(ContextManagerService.class);

  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;

  private BrokerContext broker;

  // current cash balance
  private double cash = 0;

  // Stored messages
  private Map<Integer, Map<String, Object>> pendingMessages;
  

//  @SuppressWarnings("unchecked")
  @Override
  public void initialize (BrokerContext broker)
  {
    this.broker = broker;
    propertiesService.configureMe(this);
    pendingMessages = new HashMap<>();
  }

  // -------------------- message handlers ---------------------
  //
  // Note that these arrive in JMS threads; If they share data with the
  // agent processing thread, they need to be synchronized.

  /**
   * BankTransaction represents an interest payment. Value is positive for 
   * credit, negative for debit. 
   */
  public void handleMessage (BankTransaction btx)
  {
    postSingletonMessage("BankTransaction", btx); // should be only one
  }

  /**
   * CashPosition updates our current bank balance.
   */
  public void handleMessage (CashPosition cp)
  {
    postSingletonMessage("CashPosition", cp);
    cash = cp.getBalance();
    log.info("Cash position: " + cash);
  }
  
  /**
   * DistributionReport gives total consumption and production for the timeslot,
   * summed across all brokers.
   */
  public void handleMessage (DistributionReport dr)
  {
    postSingletonMessage("DistributionReport", dr);
  }
  
  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture all the customer records so we can keep track of their
   * subscriptions and usage profiles.
   */
  public void handleMessage (Competition comp)
  {
    postSingletonMessage("Competition", comp);
  }

  public synchronized void handleMessage (CustomerBootstrapData cbd)
  {
    postSingletonMessage("CustomerBootstrapData", cbd);
  }

  public synchronized void handleMessage (MarketBootstrapData mbd)
  {
    postSingletonMessage("CustomerBootstrapData", mbd);
  }

  /**
   * Receives the server configuration properties.
   */
  public void handleMessage (java.util.Properties serverProps)
  {
    postSingletonMessage("Properties", serverProps);
  }

  private void postSingletonMessage(String type, Object msg)
  {
    pendingMessages.get(timeslotRepo.currentSerialNumber()).put(type, msg); // should be only one
  }
  
  /**
   * Returns the <type message> map for the current timeslot.
   */
  public Map<String, Object> getContextMessages ()
  {
    // Clean up old messages
    pendingMessages.remove(timeslotRepo.currentSerialNumber() - 3);
    return pendingMessages.get(timeslotRepo.currentTimeslot());
  }
  
  // per-timeslot activation
  /**
   * Come here to wait for activation
   */
  public void waitForActivation ()
  {
    try {
      wait();
    } catch (InterruptedException ie) {
      log.error("Interrupted during timeslot {}", timeslotRepo.currentSerialNumber());
    }
  }

  @Override
  public void activate (int timeslot)
  {
    notifyAll();    
  }

  /**
   * Sends a message to the server
   */
  public void sendMessage (Object message)
  {
    broker.sendMessage(message);
  }
}
