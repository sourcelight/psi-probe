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

package com.googlecode.psiprobe;

import com.googlecode.psiprobe.model.ApplicationSession;
import com.googlecode.psiprobe.model.IpInfo;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Valve which inserts the client's IP address into the session for Tomcat 7.0.
 * 
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 */
public class Tomcat70AgentValve extends ValveBase {

  public Tomcat70AgentValve() {
    super(true);
  }

  @Override
  public String getInfo() {
    return info;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    getNext().invoke(request, response);

    HttpServletRequest servletRequest = request.getRequest();
    HttpSession session = servletRequest.getSession(false);
    if (session != null) {
      String ip = IpInfo.getClientAddress(servletRequest);
      session.setAttribute(ApplicationSession.LAST_ACCESSED_BY_IP, ip);
    }
  }

}
