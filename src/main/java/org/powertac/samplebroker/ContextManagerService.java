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
import org.powertac.common.TimeService;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.msg.SimEnd;
import org.powertac.common.msg.SimStart;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.common.repo.WeatherForecastRepo;
import org.powertac.common.repo.WeatherReportRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.core.BrokerRunner;
import org.powertac.samplebroker.core.PowerTacBroker;
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
  private PowerTacBroker powerTacBroker;

  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeService timeService;

  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private WeatherReportRepo weatherReportRepo;
  
  @Autowired
  private WeatherForecastRepo weatherForecastRepo;

  private BrokerContext broker;

  // current cash balance
  private double cash = 0;

  // Stored messages
  private Map<String, List<Object>> pendingMessages;
  
  // synchronizing objects for session start, timeslot complete
  private Object startSync;
  private Object tcSync;
  private boolean started;
  private boolean ended;
  
  public ContextManagerService ()
  {
    super();
    startSync = new Object();
    tcSync = new Object();
    started = false;
  }

//  @SuppressWarnings("unchecked")
  @Override
  public void initialize (BrokerContext broker)
  {
    this.broker = broker;
    propertiesService.configureMe(this);
    pendingMessages = new HashMap<>();
    Envoy envoy = Envoy.getInstance();
    envoy.registerService("ContextManager", this);
  }

  // -------------------- message handlers ---------------------
  //
  // Note that these arrive in JMS threads; If they share data with the
  // agent processing thread, they need to be synchronized.
  /**
   * Start-of-session message
   */
  public void handleMessage (SimStart ss)
  {
    synchronized(startSync) {
      log.info("SimStart");
      started = true;
      startSync.notifyAll();
      log.info("startSync.notifyAll(), started = {}", started);
    }
    log.info("After SimStart started = {}", started);
  }

  /**
   * Python comes here to wait for sim-start
   */
  public void waitForStart ()
  {
    log.info("Waiting for start");
    synchronized(startSync) {
      while (!started) {
        try {
          log.info("Sync waiting for start");
          startSync.wait();
          log.info("startSync.wait() returns");
        } catch (InterruptedException ie) {
          log.error("Sync interrupted");
          System.exit(1);
        }
      }
    }
    log.info("Started");
  }

  /**
   * Python comes here to check for sim-start
   */
  public boolean checkForStart ()
  {
    log.info("checkForStart {}", started);
    return started;
  }
  
  /**
   * End-of-session message
   */
  public void handleMessage (SimEnd se)
  {
    log.info("SimEnd");
    addPendingMessage("SimEnd", se);
    ended = true;
  }
  
  /**
   * Returns true if SimEnd has been received
   */
  public boolean isEnded ()
  {
    return ended;
  }

  /**
   * BankTransaction represents an interest payment. Value is positive for 
   * credit, negative for debit. 
   */
  public void handleMessage (BankTransaction btx)
  {
    addPendingMessage("BankTransaction", btx); // should be only one
  }

  /**
   * CashPosition updates our current bank balance.
   */
  public void handleMessage (CashPosition cp)
  {
    addPendingMessage("CashPosition", cp);
    cash = cp.getBalance();
    log.info("Cash position: " + cash);
  }
  
  /**
   * DistributionReport gives total consumption and production for the timeslot,
   * summed across all brokers.
   */
  public void handleMessage (DistributionReport dr)
  {
    addPendingMessage("DistributionReport", dr);
  }
  
  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture all the customer records so we can keep track of their
   * subscriptions and usage profiles.
   */
  public void handleMessage (Competition comp)
  {
    log.info("Competition {}", comp.getId());
    addPendingMessage("Competition", comp);
  }

  public synchronized void handleMessage (CustomerBootstrapData cbd)
  {
    log.info("CustomerBootstrapData");
    addPendingMessage("CustomerBootstrapData", cbd);
  }

  public synchronized void handleMessage (MarketBootstrapData mbd)
  {
    log.info("MarketBootstrapData");
    addPendingMessage("MarketBootstrapData", mbd);
  }

  /**
   * Receives the server configuration properties.
   */
  public void handleMessage (java.util.Properties serverProps)
  {
    log.info("ServerProps");
    addPendingMessage("Properties", serverProps);
  }
  
  public void handleMessage (WeatherReport report)
  {
    log.info("Weather report {}", report.getTimeslotIndex());
    addPendingMessage("WeatherReport", report);
  }
  
  public void handleMessage (WeatherForecast fcst)
  {
    log.info("Weather forecast");
    addPendingMessage("WeatherForecast", fcst);
  }

  private void addPendingMessage(String type, Object msg)
  {
    if (null == pendingMessages) {
      pendingMessages = new HashMap<String, List<Object>>();
    }
    List<Object> msgs = pendingMessages.get(type);
    if (null == msgs) {
      msgs = new ArrayList<Object>();
      pendingMessages.put(type, msgs);
    }
    log.info("Adding {}", type);
    msgs.add(msg);
  }
  
  /**
   * Waits for timeslot-complete, then returns the <type message> map for the current timeslot after
   * clearing out the pending message list. So you can only do this once/timeslot.
   */
  public Map<String, List<Object>> getContextMessages ()
  {
    log.info("getContextMessages");
    Map<String, List<Object>> result = pendingMessages;
    log.info("Returning {} message lists", result.size());
    pendingMessages = null;
    return result;
  }
  
  // Test connection by posting a log message
  public void logTest (String msg)
  {
    log.info("Log test: {}", msg);
  }

  // called on TimeslotComplete
  int lastCompleteTimeslot = -1;
  @Override
  public void activate (int timeslot)
  {
    log.info("activate {}", timeslot);
    synchronized(tcSync) {
      lastCompleteTimeslot = timeslotRepo.currentSerialNumber();
      tcSync.notifyAll();
    }
  }
  
  
  public int waitForTimeslotComplete (int lastTimeslotIndex)
  {
    int result = 0;
    synchronized(tcSync) {
      while (lastTimeslotIndex >= lastCompleteTimeslot) {
        try {
          tcSync.wait();
          result = lastCompleteTimeslot;
        } catch (InterruptedException ie) {
          log.error("Interrupted during timeslot {}", timeslotRepo.currentSerialNumber());
        }
      }
    }
    return result;
  }

  /**
   * Sends a message to the server
   */
  public void sendMessage (Object message)
  {
    broker.sendMessage(message);
  }

  // ================== Access to Spring services ===================
  public TimeService getTimeService ()
  {
    return timeService;
  }

  public TimeslotRepo getTimeslotRepo ()
  {
    return timeslotRepo;
  }

  public WeatherReportRepo getWeatherReportRepo ()
  {
    return weatherReportRepo;
  }

  public WeatherForecastRepo getWeatherForecastRepo ()
  {
    return weatherForecastRepo;
  }
}
