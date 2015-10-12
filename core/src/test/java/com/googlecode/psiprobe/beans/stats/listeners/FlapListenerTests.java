/*
 * Licensed under the GPL License. You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE.
 */
package com.googlecode.psiprobe.beans.stats.listeners;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Mark Lewis
 */
public class FlapListenerTests {

  private final int defaultThreshold = 10;
  private final int defaultInterval = 10;
  private final float defaultStartThreshold = 0.29f;
  private final float defaultStopThreshold = 0.49f;
  private final float defaultLowWeight = 1.0f;
  private final float defaultHighWeight = 1.0f;

  private MockFlapListener listener = new MockFlapListener(defaultThreshold, defaultInterval,
      defaultStartThreshold, defaultStopThreshold, defaultLowWeight, defaultHighWeight);

  private StatsCollectionEvent belowThreshold = new StatsCollectionEvent("test", 0, 0);
  private StatsCollectionEvent aboveThreshold = new StatsCollectionEvent("test", 0, 20);

  protected void fill(StatsCollectionEvent sce) {
    listener.reset();
    add(sce, defaultInterval);
  }

  protected void add(StatsCollectionEvent sce, int quantity) {
    for (int i = 0; i < quantity; i++) {
      listener.statsCollected(sce);
    }
  }

  @Test
  public void testBelowThresholdNotFlapping() {
    listener.reset();
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    Assert.assertTrue(listener.isBelowThresholdNotFlapping());
  }

  @Test
  public void testAboveThresholdNotFlapping() {
    listener.reset();
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    Assert.assertTrue(listener.isAboveThresholdNotFlapping());
  }

  @Test
  public void testStillBelowThreshold() {
    listener.reset();
    listener.statsCollected(belowThreshold);
    for (int i = 0; i < defaultInterval; i++) {
      listener.statsCollected(belowThreshold);
      Assert.assertFalse(listener.isBelowThresholdNotFlapping());
    }
  }

  @Test
  public void testStillAboveThreshold() {
    listener.reset();
    listener.statsCollected(aboveThreshold);
    for (int i = 0; i < defaultInterval; i++) {
      listener.statsCollected(aboveThreshold);
      Assert.assertFalse(listener.isAboveThresholdNotFlapping());
    }
  }

  @Test
  public void testFlappingStarted() {
    fill(belowThreshold);
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    Assert.assertTrue(listener.isFlappingStarted());
  }

  @Test
  public void testFlappingStarted2() {
    fill(aboveThreshold);
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    Assert.assertTrue(listener.isFlappingStarted());
  }

  @Test
  public void testBelowThresholdFlappingStoppedBelow() {
    fill(belowThreshold);
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    Assert.assertTrue(listener.isFlappingStarted());
    add(belowThreshold, 5);
    Assert.assertTrue(listener.isBelowThresholdFlappingStopped());
  }

  @Test
  public void testBelowThresholdFlappingStoppedAbove() {
    fill(belowThreshold);
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    Assert.assertTrue(listener.isFlappingStarted());
    add(aboveThreshold, 5);
    Assert.assertTrue(listener.isAboveThresholdFlappingStopped());
  }

  @Test
  public void testAboveThresholdFlappingStoppedBelow() {
    fill(aboveThreshold);
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    Assert.assertTrue(listener.isFlappingStarted());
    add(belowThreshold, 5);
    Assert.assertTrue(listener.isBelowThresholdFlappingStopped());
  }

  @Test
  public void testAboveThresholdFlappingStoppedAbove() {
    fill(aboveThreshold);
    listener.statsCollected(belowThreshold);
    listener.statsCollected(aboveThreshold);
    listener.statsCollected(belowThreshold);
    Assert.assertTrue(listener.isFlappingStarted());
    add(aboveThreshold, 5);
    Assert.assertTrue(listener.isAboveThresholdFlappingStopped());
  }

  public static class MockFlapListener extends FlapListener {

    private final long threshold;

    private boolean flappingStarted;
    private boolean aboveThresholdFlappingStopped;
    private boolean belowThresholdFlappingStopped;
    private boolean aboveThresholdNotFlapping;
    private boolean belowThresholdNotFlapping;

    public MockFlapListener(long threshold, int flapInterval, float flapStartThreshold,
        float flapStopThreshold, float lowWeight, float highWeight) {

      this.threshold = threshold;
      setDefaultFlapInterval(flapInterval);
      setDefaultFlapStartThreshold(flapStartThreshold);
      setDefaultFlapStopThreshold(flapStopThreshold);
      setDefaultFlapLowWeight(lowWeight);
      setDefaultFlapHighWeight(highWeight);
    }

    @Override
    public void statsCollected(StatsCollectionEvent sce) {
      resetFlags();
      super.statsCollected(sce);
    }

    @Override
    protected void flappingStarted(StatsCollectionEvent sce) {
      flappingStarted = true;
    }

    @Override
    protected void aboveThresholdFlappingStopped(StatsCollectionEvent sce) {
      aboveThresholdFlappingStopped = true;
    }

    @Override
    protected void belowThresholdFlappingStopped(StatsCollectionEvent sce) {
      belowThresholdFlappingStopped = true;
    }

    @Override
    protected void aboveThresholdNotFlapping(StatsCollectionEvent sce) {
      aboveThresholdNotFlapping = true;
    }

    @Override
    protected void belowThresholdNotFlapping(StatsCollectionEvent sce) {
      belowThresholdNotFlapping = true;
    }

    @Override
    public long getThreshold(String name) {
      return threshold;
    }

    @Override
    public void reset() {
      resetFlags();
      super.reset();
    }

    public void resetFlags() {
      flappingStarted = false;
      aboveThresholdFlappingStopped = false;
      belowThresholdFlappingStopped = false;
      aboveThresholdNotFlapping = false;
      belowThresholdNotFlapping = false;
    }

    public boolean isAboveThresholdFlappingStopped() {
      return aboveThresholdFlappingStopped;
    }

    public boolean isAboveThresholdNotFlapping() {
      return aboveThresholdNotFlapping;
    }

    public boolean isBelowThresholdFlappingStopped() {
      return belowThresholdFlappingStopped;
    }

    public boolean isBelowThresholdNotFlapping() {
      return belowThresholdNotFlapping;
    }

    public boolean isFlappingStarted() {
      return flappingStarted;
    }

  }

}
