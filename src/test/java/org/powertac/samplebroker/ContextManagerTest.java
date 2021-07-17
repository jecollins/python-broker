package org.powertac.samplebroker;

import static org.junit.jupiter.api.Assertions.*;
import static org.powertac.util.MessageDispatcher.dispatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powertac.common.msg.SimStart;

class ContextManagerTest
{
  ContextManagerService uut;

  @BeforeEach
  void setUp () throws Exception
  {
    uut = new ContextManagerService();
  }

  // call waitForStart, then handleMessage(SimStart)
  @Test
  void testHandleMessageSimStartA ()
  {
    SimStart msg = new SimStart(null);
    MessageSender sender = new MessageSender(msg, 3000);
    System.out.println("starting");
    sender.start();
    System.out.println("started");
    uut.waitForStart();
    System.out.println("startSync complete");
  }

  class MessageSender extends Thread
  {
    Object targetMessage;
    int msDelay;
    
    MessageSender (Object message, int delay)
    {
      super();
      targetMessage = message;
      msDelay = delay;
    }
    
    public void run ()
    {
      try {
        Thread.sleep(msDelay);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      dispatch(uut, "handleMessage", targetMessage);
    }
  }
}
