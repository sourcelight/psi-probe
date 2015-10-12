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

package com.googlecode.psiprobe.controllers.datasources;

import com.googlecode.psiprobe.controllers.TomcatContainerController;
import com.googlecode.psiprobe.model.ApplicationResource;
import com.googlecode.psiprobe.model.DataSourceInfo;
import com.googlecode.psiprobe.model.DataSourceInfoGroup;

import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Produces a list of all datasources configured within the container grouped by JDBC URL.
 * 
 * @author Andy Shapoval
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 */
public class ListAllJdbcResourceGroups extends TomcatContainerController {

  @Override
  protected ModelAndView handleRequestInternal(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    
    List<DataSourceInfoGroup> dataSourceGroups = new ArrayList<DataSourceInfoGroup>();
    List<DataSourceInfo> dataSources = new ArrayList<DataSourceInfo>();

    List<ApplicationResource> privateResources = getContainerWrapper().getPrivateDataSources();
    List<ApplicationResource> globalResources = getContainerWrapper().getGlobalDataSources();

    // filter out anything that is not a datasource
    // and use only those datasources that are properly configured
    // as aggregated totals would not make any sense otherwise
    filterValidDataSources(privateResources, dataSources);
    filterValidDataSources(globalResources, dataSources);

    // sort datasources by JDBC URL
    Collections.sort(dataSources, new Comparator<DataSourceInfo>() {
      @Override
      public int compare(DataSourceInfo ds1, DataSourceInfo ds2) {
        String jdbcUrl1 = ds1.getJdbcUrl();
        String jdbcUrl2 = ds2.getJdbcUrl();

        // here we rely on the the filter not to add any datasources with a null jdbcUrl to the list

        return jdbcUrl1.compareToIgnoreCase(jdbcUrl2);
      }
    });

    // group datasources by JDBC URL and calculate aggregated totals
    DataSourceInfoGroup dsGroup = null;
    for (DataSourceInfo ds : dataSources) {
      if (dsGroup == null || !dsGroup.getJdbcUrl().equalsIgnoreCase(ds.getJdbcUrl())) {
        dsGroup = new DataSourceInfoGroup(ds);
        dataSourceGroups.add(dsGroup);
      } else {
        dsGroup.addDataSourceInfo(ds);
      }
    }

    return new ModelAndView(getViewName(), "dataSourceGroups", dataSourceGroups);
  }

  protected void filterValidDataSources(List<ApplicationResource> resources,
      List<DataSourceInfo> dataSources) {

    for (ApplicationResource res : resources) {
      if (res.isLookedUp() && res.getDataSourceInfo() != null
          && res.getDataSourceInfo().getJdbcUrl() != null) {
        dataSources.add(res.getDataSourceInfo());
      }
    }
  }

}
