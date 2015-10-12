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

package com.googlecode.psiprobe.beans;

import com.googlecode.psiprobe.model.DataSourceInfo;

import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;

/**
 * 
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 * @author <a href="mailto:diogosantana@gmail.com">Diogo Sant'Ana</a>
 */
public class Tomcat8DbcpDatasourceAccessor implements DatasourceAccessor {

  @Override
  public DataSourceInfo getInfo(Object resource) throws Exception {
    DataSourceInfo dataSourceInfo = null;
    if (canMap(resource)) {
      BasicDataSource source = (BasicDataSource) resource;
      dataSourceInfo = new DataSourceInfo();
      dataSourceInfo.setBusyConnections(source.getNumActive());
      dataSourceInfo.setEstablishedConnections(source.getNumIdle() + source.getNumActive());
      dataSourceInfo.setMaxConnections(source.getMaxTotal());
      dataSourceInfo.setJdbcUrl(source.getUrl());
      dataSourceInfo.setUsername(source.getUsername());
      dataSourceInfo.setResettable(false);
      dataSourceInfo.setType("tomcat-dbcp2");
    }
    return dataSourceInfo;
  }

  @Override
  public boolean reset(Object resource) throws Exception {
    return false;
  }

  @Override
  public boolean canMap(Object resource) {
    return "org.apache.tomcat.dbcp.dbcp2.BasicDataSource".equals(resource.getClass().getName())
        && resource instanceof BasicDataSource;
  }

}
