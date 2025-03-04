/*
 * Copyright (c) 2012-2014 by the original author
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.CapacityTransaction;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.DistributionTransaction;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles market interactions on behalf of the broker.
 * @author John Collins
 */
@Service
public class MarketManagerService 
implements MarketManager, Initializable, Activatable
{
  static private Logger log = LogManager.getLogger(MarketManagerService.class);
  
  private BrokerContext broker; // broker

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private PortfolioManager portfolioManager;

  // ------------ Configurable parameters --------------
  // max and min offer prices. Max means "sure to trade"
  //@ConfigurableValue(valueType = "Double",
  //        description = "Upper end (least negative) of bid price range")
  //private double buyLimitPriceMax = -1.0;  // broker pays

  //@ConfigurableValue(valueType = "Double",
  //        description = "Lower end (most negative) of bid price range")
  //private double buyLimitPriceMin = -70.0;  // broker pays

  //@ConfigurableValue(valueType = "Double",
  //        description = "Upper end (most positive) of ask price range")
  //private double sellLimitPriceMax = 70.0;    // other broker pays

  //@ConfigurableValue(valueType = "Double",
  //        description = "Lower end (least positive) of ask price range")
  //private double sellLimitPriceMin = 0.5;    // other broker pays

  //@ConfigurableValue(valueType = "Double",
  //        description = "Minimum bid/ask quantity in MWh")
  //private double minMWh = 0.001; // don't worry about 1 KWh or less

  //@ConfigurableValue(valueType = "Integer",
  //        description = "If set, seed the random generator")
  //private Integer seedNumber = null;

  // ---------------- local state ------------------
  //private Random randomGen; // to randomize bid/ask prices

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;
  
  // Map for recording per-timeslot messages
  private Map<String, List<Object>> pendingMessages;

  public MarketManagerService ()
  {
    super();
  }

  /* (non-Javadoc)
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.SampleBroker)
   */
  @Override
  public void initialize (BrokerContext broker)
  {
    this.broker = broker;
    lastOrder = new HashMap<>();
    propertiesService.configureMe(this);
    Envoy envoy = Envoy.getInstance();
    envoy.registerService("MarketManager", this);
  }

  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market during the bootstrap session.
   */
  @Override
  public double getMeanMarketPrice ()
  {
    return meanMarketPrice;
  }
  
  // --------------- message handling -----------------
  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture minimum order size to avoid running into the limit
   * and generating unhelpful error messages.
   */
  //public synchronized void handleMessage (Competition comp)
  //{
  //  minMWh = Math.max(minMWh, comp.getMinimumOrderQuantity());
  //}

  /**
   * Handles a BalancingTransaction message.
   */
  public synchronized void handleMessage (BalancingTransaction tx)
  {
    log.info("Balancing tx: " + tx.getCharge());
    addPendingMessage("BalancingTransaction", tx);
  }

  /**
   * Handles a ClearedTrade message - this is where you would want to keep
   * track of market prices.
   */
  public synchronized void handleMessage (ClearedTrade ct)
  {
    addPendingMessage("ClearedTrade", ct);
  }

  /**
   * Handles a DistributionTransaction - charges for transporting power
   */
  public synchronized void handleMessage (DistributionTransaction dt)
  {
    log.info("Distribution tx: " + dt.getCharge());
    addPendingMessage("DistributionTransaction", dt);
  }

  /**
   * Handles a CapacityTransaction - a charge for contribution to overall
   * peak demand over the recent past.
   */
  public synchronized void handleMessage (CapacityTransaction ct)
  {
    log.info("Capacity tx: " + ct.getCharge());
    addPendingMessage("CapacityTransaction", ct);
  }

  /**
   * Receives a MarketBootstrapData message, reporting usage and prices
   * for the bootstrap period. We record the overall weighted mean price,
   * as well as the mean price and usage for a week.
   * 
   * Note that this message is passed through in the ContextManager.
   */
  public synchronized void handleMessage (MarketBootstrapData data)
  {
    marketMWh = new double[broker.getUsageRecordLength()];
    marketPrice = new double[broker.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;
    for (int i = 0; i < data.getMwh().length; i++) {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < broker.getUsageRecordLength()) {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      }
      else {
        // subsequent passes, accumulate mean values
        int pass = i / broker.getUsageRecordLength();
        int index = i % broker.getUsageRecordLength();
        marketMWh[index] =
            (marketMWh[index] * pass + data.getMwh()[i]) / (pass + 1);
        marketPrice[index] =
            (marketPrice[index] * pass + data.getMarketPrice()[i]) / (pass + 1);
      }
    }
    meanMarketPrice = totalValue / totalUsage;
  }

  /**
   * Receives a MarketPosition message, representing our commitments on 
   * the wholesale market
   */
  public synchronized void handleMessage (MarketPosition posn)
  {
    broker.getBroker().addMarketPosition(posn, posn.getTimeslotIndex());
    addPendingMessage("MarketPosition", posn);
  }
  
  /**
   * Receives a new MarketTransaction. We look to see whether an order we
   * have placed has cleared.
   */
  public synchronized void handleMessage (MarketTransaction tx)
  {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslotIndex());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslotIndex(), null);
    addPendingMessage("MarketTransaction", tx);
  }
  
  /**
   * Receives market orderbooks. These list un-cleared bids and asks,
   * from which a broker can construct approximate supply and demand curves
   * for the following timeslot.
   */
  public synchronized void handleMessage (Orderbook orderbook)
  {
    addPendingMessage("Orderbook", orderbook);
  }

  /**
   * Receives a BalanceReport containing information about imbalance in the
   * current timeslot.
   */
  public synchronized void handleMessage (BalanceReport report)
  {
    addPendingMessage("BalanceReport", report);
  }

  // Adds a message to the correct pendingMessage list
  private void addPendingMessage (String type, Object msg)
  {
    if (null == pendingMessages) {
      pendingMessages = new HashMap<String, List<Object>>();
    }
    List<Object> msgs = pendingMessages.get(type);
    if (null == msgs) {
      msgs = new ArrayList<Object>();
      pendingMessages.put(type, msgs);
    }
    msgs.add(msg);
  }

  /**
   * Retrieves pending messages for the current timeslot.
   */
  public Map<String, List<Object>> getPendingMessageLists ()
  {
    // Save result, clean up old messages
    log.info("getMarketMessages {}", (pendingMessages != null) ? pendingMessages.size(): 0);
    Map<String, List<Object>> result = pendingMessages;
    pendingMessages = null;
    return result;
  }

  // ----------- per-timeslot activation ---------------

  /**
   * Not sure we need this?
   *
   * @see org.powertac.samplebroker.interfaces.Activatable#activate(int)
   */
  @Override
  public synchronized void activate (int timeslotIndex)
  {
    //double neededKWh = 0.0;
    //log.debug("Current timeslot is " + timeslotRepo.currentTimeslot().getSerialNumber());
    //for (Timeslot timeslot : timeslotRepo.enabledTimeslots()) {
    //  int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
    //  neededKWh = portfolioManager.collectUsage(index);
    //  submitOrder(neededKWh, timeslot.getSerialNumber());
    //}
  }

//  /**
//   * Composes and submits the appropriate order for the given timeslot.
//   */
//  private void submitOrder (double neededKWh, int timeslot)
//  {
//    double neededMWh = neededKWh / 1000.0;
//
//    MarketPosition posn =
//        broker.getBroker().findMarketPositionByTimeslot(timeslot);
//    if (posn != null)
//      neededMWh -= posn.getOverallBalance();
//    if (Math.abs(neededMWh) <= minMWh) {
//      log.info("no power required in timeslot " + timeslot);
//      return;
//    }
//    Double limitPrice = computeLimitPrice(timeslot, neededMWh);
//    log.info("new order for " + neededMWh + " at " + limitPrice +
//             " in timeslot " + timeslot);
//    Order order = new Order(broker.getBroker(), timeslot, neededMWh, limitPrice);
//    lastOrder.put(timeslot, order);
//    broker.sendMessage(order);
//  }
//
//  /**
//   * Computes a limit price with a random element. 
//   */
//  private Double computeLimitPrice (int timeslot,
//                                    double amountNeeded)
//  {
//    log.debug("Compute limit for " + amountNeeded + 
//              ", timeslot " + timeslot);
//    // start with default limits
//    Double oldLimitPrice;
//    double minPrice;
//    if (amountNeeded > 0.0) {
//      // buying
//      oldLimitPrice = buyLimitPriceMax;
//      minPrice = buyLimitPriceMin;
//    }
//    else {
//      // selling
//      oldLimitPrice = sellLimitPriceMax;
//      minPrice = sellLimitPriceMin;
//    }
//    // check for escalation
//    Order lastTry = lastOrder.get(timeslot);
//    if (lastTry != null)
//      log.debug("lastTry: " + lastTry.getMWh() +
//                " at " + lastTry.getLimitPrice());
//    if (lastTry != null
//        && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh())) {
//      oldLimitPrice = lastTry.getLimitPrice();
//      log.debug("old limit price: " + oldLimitPrice);
//    }
//
//    // set price between oldLimitPrice and maxPrice, according to number of
//    // remaining chances we have to get what we need.
//    double newLimitPrice = minPrice; // default value
//    int current = timeslotRepo.currentSerialNumber();
//    int remainingTries = (timeslot - current
//                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
//    log.debug("remainingTries: " + remainingTries);
//    if (remainingTries > 0) {
//      double range = (minPrice - oldLimitPrice) * 2.0 / (double)remainingTries;
//      log.debug("oldLimitPrice=" + oldLimitPrice + ", range=" + range);
//      double computedPrice = oldLimitPrice + randomGen.nextDouble() * range; 
//      return Math.max(newLimitPrice, computedPrice);
//    }
//    else
//      return null; // market order
//  }
}
