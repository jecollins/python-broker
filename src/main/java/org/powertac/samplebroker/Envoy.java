/*
 * Copyright 2021 by John E. Collins.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.powertac.samplebroker;

import org.apache.logging.log4j.LogManager;
import py4j.GatewayServer;
import org.apache.logging.log4j.Logger;
import org.powertac.samplebroker.core.BrokerRunner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Provides access to Spring services and the ability to start the broker in a thread without
 * modifying the Java broker code.
 * 
 * @author John Collins
 */
public class Envoy
{
  private static Logger log = LogManager.getLogger(Envoy.class);
  
  private static Envoy instance;
  
  /**
   * main() method is how the py4j gateway gets set up.
   */
  public static void main (String[] args)
  {
    instance = new Envoy();
    GatewayServer gatewayServer = new GatewayServer(instance);
    gatewayServer.start();
  }

  public Envoy ()
  {
    super();
  }
  
  /**
   * Returns reference to Envoy instance to Python code
   */
  public Envoy getEnvoy ()
  {
    return instance;
  }

  public Object getSpringService (String classname)
  {
    try {
      Class<?> clazz = Class.forName(classname);
      AbstractApplicationContext context = BrokerRunner.getApplicationContext();
      return context.getBeansOfType(clazz).values().toArray()[0];
    } catch (ClassNotFoundException cnf) {
      System.out.println("Class " + classname + " not found");
      return null;
    }
  }

  /**
   * Starts and runs the agent in a new thread. This will work as long as the cli does not contain repeat-count or
   * repeat-hours options.
   */
  public void startSession (String[] args)
  {
    Runner runner = new Runner(args);
    log.info("runner created");
    runner.start();
    log.info("runner started");
  }

  /**
   * Thread wrapper for agent session. Create an instance and call start, which returns while
   * the thread runs.
   */
  class Runner extends Thread
  {
    String[] clArgs;
    Runner (String[] args)
    {
      clArgs = args;
    }
    
    public void run ()
    {
      log.info("Runner thread started");
      BrokerRunner bRunner = new BrokerRunner();
      bRunner.processCmdLine(clArgs);
    }
  }
  
  // Test thread access
  // Python process calls startDelay, which returns immediately,
  // then calls waitForDelay(), which returns the delayCount after the delay.
  DelayThread uut;
  
  public void startDelay ()
  {
    if (null == uut) {
      uut = new DelayThread();
      uut.start();
      // wait for thread to call waitForStart
      uut.waitForRunning();
    }
    log.info("start delay");
    uut.startDelay();
    
  }
  
  public int waitForDelay ()
  {
    if (null == uut)
      return -1; // nothing to see here
    return uut.waitForProceed();
  }

  class DelayThread extends Thread
  {
    // monitor objects
    Object threadMonitor;
    Object startMonitor;
    Object endMonitor;
    
    
    // status values
    boolean threadRunning = false;
    boolean delaying = false;
    int delayCount = 0;
    
    DelayThread ()
    {
      super();
      threadMonitor = new Object();
      startMonitor = new Object();
      endMonitor = new Object();
    }

    // wait for thread to start
    private void waitForRunning ()
    {
      synchronized(threadMonitor) {
        while (!threadRunning) {
          try {
            log.info("wait for thread running");
            threadMonitor.wait();
            log.info("running signaled");
          } catch (InterruptedException ie) {
            log.error("Interrupted waiting for thread to start");
          }
        }
      }
    }
    
    private void signalRunning ()
    {
      synchronized(threadMonitor) {
        log.info("signalRunning()");
        threadRunning = true;
        threadMonitor.notifyAll();
      }
    }

    // Start a delay only after any previous delay is completed
    private void startDelay ()
    {
      synchronized(startMonitor) {
        waitForRunning();
        if (!delaying) {
          // immediate start
          delaying = true;
          startMonitor.notifyAll();        
        }
        else {
          // wait for previous delay to finish
          while (delaying) {
            try {
              wait();
              // delaying should now be false
              if (delaying)
                log.error("delaying should be false");
              delaying = true;
              startMonitor.notifyAll();
            } catch (InterruptedException ie) {
              log.error("newDelay interrupted");
              break;
            }
          }
        }
      }
    }
    
    private void waitForStart ()
    {
      synchronized(startMonitor) {
        log.info("waitForStart, delaying={}", delaying);
        while (!delaying) {
          try {
            startMonitor.wait();
            delaying = true;
            delayCount += 1;
          } catch (InterruptedException ie) {
            log.error("waitForNewDelay interrupted");
          }
        }
      }
    }
    
    // communicate end of delay
    private int waitForProceed ()
    {
      synchronized(endMonitor) {
        while (delaying) {
          try {
            endMonitor.wait();
          } catch (InterruptedException ie) {
            log.error("waitForProceed interrupted");
          }
        }
      }
      return delayCount;
    }
    
    private void proceed ()
    {
      synchronized(endMonitor) {
        if (!delaying) {
          log.error("In proceed() {} delaying should be true", delayCount);
        }
        delaying = false;
        endMonitor.notifyAll();
      }
    }
    
    // here's the body of the thread
    @Override
    public void run()
    {
      log.info("Delay thread started");
      signalRunning();
      while (true) {
        waitForStart();
        try {
          Thread.sleep(2000);
        } catch (InterruptedException ie) {
          log.error("Sleep {} interrupted", delayCount);
        }
        proceed();
      }
    }
  }
}
