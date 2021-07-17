/**
 * 
 */
package org.powertac.samplebroker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author jcollins
 *
 */
class EnvoyTest
{
  Envoy envoy;
  
  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp () throws Exception
  {
    envoy = new Envoy();
  }

  /**
   * Test method for {@link org.powertac.samplebroker.Envoy#startDelay()}.
   */
  @Test
  void testStartDelay ()
  {
    envoy.startDelay();
    int result = envoy.waitForDelay();
    assertEquals(1, result);
  }

  /**
   * Test method for {@link org.powertac.samplebroker.Envoy#waitForDelay()}.
   */
  //@Test
  //void testWaitForDelay ()
  //{
  //  fail("Not yet implemented");
  //}
}
