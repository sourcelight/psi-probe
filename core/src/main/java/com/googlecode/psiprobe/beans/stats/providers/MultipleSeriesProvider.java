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

package com.googlecode.psiprobe.beans.stats.providers;

import com.googlecode.psiprobe.model.stats.StatsCollection;

import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.XYDataItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Retrieves stats series with names that start with the statNamePrefix. Either all matching series
 * or only "top" N ones can be retrieved. Determines top series by comparing max moving avg values.
 * Derrives legend entries from series names by removing the statNamePrefix. Ignores series param
 * (sp) and legend (s...l) request parameters.
 *
 * @author Andy Shapoval
 */
public class MultipleSeriesProvider extends AbstractSeriesProvider {
  private String statNamePrefix;
  private int top = 0;
  private int movingAvgFrame = 0;

  public String getStatNamePrefix() {
    return statNamePrefix;
  }

  /**
   * @param statNamePrefix - only series with names that start with statNamePrefix are retrieved.
   */
  public void setStatNamePrefix(String statNamePrefix) {
    this.statNamePrefix = statNamePrefix;
  }

  public int getTop() {
    return top;
  }

  /**
   * @param top - the number of top series to retrieve. If this value is greater than 0, only this
   *        many series with the greatest max moving avg values are retrieved.
   */
  public void setTop(int top) {
    this.top = top;
  }

  public int getMovingAvgFrame() {
    return movingAvgFrame;
  }

  /**
   * @param movingAvgFrame - if this value is greater than 0, a moving avg value is calculated for
   *        every series using every Nth value, where N % movingAvgFrame == 0. Top series are
   *        identified based on a max moving avg value of each series. If the movingAvgFrame equals
   *        to 0, top series are determined based on a simple avg of all series values.
   */
  public void setMovingAvgFrame(int movingAvgFrame) {
    this.movingAvgFrame = movingAvgFrame;
  }

  @Override
  public void populate(DefaultTableXYDataset dataset, StatsCollection statsCollection,
      HttpServletRequest request) {

    Map<String, List<XYDataItem>> statMap = statsCollection.getStatsByPrefix(statNamePrefix);
    boolean useTop = getTop() > 0 && getTop() < statMap.size();
    List<Series> seriesList = new ArrayList<Series>();

    for (Map.Entry<String, List<XYDataItem>> entry : statMap.entrySet()) {
      Series ser = new Series(entry);
      if (useTop) {
        ser.calculateAvg();
      }
      seriesList.add(ser);
    }

    if (useTop) {
      // sorting stats by the avg value to identify the top series
      Collections.sort(seriesList, new Comparator<Series>() {
        @Override
        public int compare(Series s1, Series s2) {
          return s1.avg == s2.avg ? s1.key.compareTo(s2.key) : (s1.avg > s2.avg ? -1 : 1);
        }
      });

      // keeping only the top series in the list
      for (ListIterator<Series> i = seriesList.listIterator(getTop()); i.hasNext();) {
        i.next();
        i.remove();
      }
    }

    // sorting the remaining series by name
    Collections.sort(seriesList, new Comparator<Series>() {
      @Override
      public int compare(Series s1, Series s2) {
        return (s1.key).compareTo(s2.key);
      }
    });

    for (Series ser : seriesList) {
      dataset.addSeries(toSeries(ser.key, ser.stats));
    }
  }

  // a helper class that holds series and calculates an avg value
  private class Series {
    final String key;
    final List<XYDataItem> stats;
    double avg = 0;

    Series(Map.Entry<String, List<XYDataItem>> en) {
      key = en.getKey().substring(statNamePrefix.length());
      stats = en.getValue();
    }

    // calculating an avg value that is used for identifying the top series
    void calculateAvg() {
      long sum = 0;
      int count = 1;

      synchronized (stats) {
        boolean useMovingAvg = getMovingAvgFrame() > 0 && getMovingAvgFrame() < stats.size();

        for (ListIterator<XYDataItem> it = stats.listIterator(); it.hasNext();) {
          XYDataItem xy = it.next();
          sum += xy.getY().longValue();

          if ((useMovingAvg && count % getMovingAvgFrame() == 0) || !it.hasNext()) {
            double thisAvg = (double) sum / count;
            if (thisAvg > avg) {
              avg = thisAvg;
            }
            sum = 0;
            count = 1;
          } else {
            count++;
          }
        }
      }
    }
  }
}
