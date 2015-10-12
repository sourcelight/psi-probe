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

import com.googlecode.psiprobe.model.Connector;
import com.googlecode.psiprobe.model.RequestProcessor;
import com.googlecode.psiprobe.model.ThreadPool;
import com.googlecode.psiprobe.model.jmx.ThreadPoolObjectName;
import com.googlecode.psiprobe.tools.JmxTools;

import net.sf.javainetlocator.InetAddressLocator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;

/**
 * This class interfaces Tomcat JMX functionality to read connection status. The class essentially
 * provides and maintains the list of connection ThreadPools.
 * 
 * @author Vlad Ilyushchenko
 * @author Mark Lewis
 */
public class ContainerListenerBean implements NotificationListener {

  private Log logger = LogFactory.getLog(getClass());
  private List<ThreadPoolObjectName> poolNames = null;
  private List<ObjectName> executorNames = null;

  /**
   * Used to obtain required {@link MBeanServer} instance.
   */
  private ContainerWrapperBean containerWrapper;

  public ContainerWrapperBean getContainerWrapper() {
    return containerWrapper;
  }

  public void setContainerWrapper(ContainerWrapperBean containerWrapper) {
    this.containerWrapper = containerWrapper;
  }

  private boolean isInitialized() {
    return poolNames != null && poolNames.size() > 0;
  }

  /**
   * Finds ThreadPoolObjectName by its string name.
   * 
   * @param name - pool name
   *
   * @return null if the input name is null or ThreadPoolObjectName is not found
   */
  private ThreadPoolObjectName findPool(String name) {
    if (name != null && isInitialized()) {
      for (ThreadPoolObjectName threadPoolObjectName : poolNames) {
        if (name.equals(threadPoolObjectName.getThreadPoolName().getKeyProperty("name"))) {
          return threadPoolObjectName;
        }
      }
    }
    return null;
  }

  /**
   * Handles creation and deletion of new "worker" threads.
   */
  @Override
  public synchronized void handleNotification(Notification notification, Object object) {
    if (notification instanceof MBeanServerNotification) {
      ObjectName objectName = ((MBeanServerNotification) notification).getMBeanName();

      if (notification.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {

        if ("RequestProcessor".equals(objectName.getKeyProperty("type"))) {
          ThreadPoolObjectName threadPoolObjectName = findPool(objectName.getKeyProperty("worker"));
          if (threadPoolObjectName != null) {
            threadPoolObjectName.getRequestProcessorNames().add(objectName);
          }
        }

      } else if (notification.getType()
          .equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {

        if ("RequestProcessor".equals(objectName.getKeyProperty("type"))) {
          ThreadPoolObjectName threadPoolObjectName = findPool(objectName.getKeyProperty("worker"));
          if (threadPoolObjectName != null) {
            threadPoolObjectName.getRequestProcessorNames().remove(objectName);
          }
        }
      }
    }
  }

  /**
   * Load ObjectNames for the relevant MBeans so they can be queried at a later stage without
   * searching MBean server over and over again.
   *
   * @throws Exception - this method does not handle any of the exceptions that may be thrown when
   *         querying MBean server.
   */
  private synchronized void initialize() throws Exception {

    MBeanServer server = getContainerWrapper().getResourceResolver().getMBeanServer();
    String serverName = getContainerWrapper().getTomcatContainer().getName();
    Set<ObjectInstance> threadPools =
        server.queryMBeans(new ObjectName(serverName + ":type=ThreadPool,*"), null);
    poolNames = new ArrayList<ThreadPoolObjectName>(threadPools.size());
    for (ObjectInstance threadPool : threadPools) {

      ThreadPoolObjectName threadPoolObjectName = new ThreadPoolObjectName();
      ObjectName threadPoolName = threadPool.getObjectName();

      String name = threadPoolName.getKeyProperty("name");

      threadPoolObjectName.setThreadPoolName(threadPoolName);
      ObjectName grpName =
          server.getObjectInstance(
              new ObjectName(threadPoolName.getDomain() + ":type=GlobalRequestProcessor,name="
                  + name)).getObjectName();
      threadPoolObjectName.setGlobalRequestProcessorName(grpName);

      /*
       * unfortunately exact workers could not be found at the time of testing so we filter out the
       * relevant workers within the loop
       */
      Set<ObjectInstance> workers =
          server.queryMBeans(
              new ObjectName(threadPoolName.getDomain() + ":type=RequestProcessor,*"), null);

      for (ObjectInstance worker : workers) {
        ObjectName wrkName = worker.getObjectName();
        if (name.equals(wrkName.getKeyProperty("worker"))) {
          threadPoolObjectName.getRequestProcessorNames().add(wrkName);
        }
      }

      poolNames.add(threadPoolObjectName);
    }

    Set<ObjectInstance> executors =
        server.queryMBeans(new ObjectName(serverName + ":type=Executor,*"), null);
    executorNames = new ArrayList<ObjectName>(executors.size());
    for (ObjectInstance executor : executors) {
      ObjectName executorName = executor.getObjectName();
      executorNames.add(executorName);
    }

    // Register with MBean server
    server.addNotificationListener(new ObjectName("JMImplementation:type=MBeanServerDelegate"),
        this, null, null);

  }

  public synchronized List<ThreadPool> getThreadPools() throws Exception {
    if (!isInitialized()) {
      initialize();
    }

    List<ThreadPool> threadPools = new ArrayList<ThreadPool>(poolNames.size());

    MBeanServer server = getContainerWrapper().getResourceResolver().getMBeanServer();

    for (ObjectName executorName : executorNames) {
      ThreadPool threadPool = new ThreadPool();
      threadPool.setName(executorName.getKeyProperty("name"));
      threadPool.setMaxThreads(JmxTools.getIntAttr(server, executorName, "maxThreads"));
      threadPool.setMaxSpareThreads(JmxTools.getIntAttr(server, executorName, "largestPoolSize"));
      threadPool.setMinSpareThreads(JmxTools.getIntAttr(server, executorName, "minSpareThreads"));
      threadPool.setCurrentThreadsBusy(JmxTools.getIntAttr(server, executorName, "activeCount"));
      threadPool.setCurrentThreadCount(JmxTools.getIntAttr(server, executorName, "poolSize"));
      threadPools.add(threadPool);
    }

    for (ThreadPoolObjectName threadPoolObjectName : poolNames) {
      try {
        ObjectName poolName = threadPoolObjectName.getThreadPoolName();

        ThreadPool threadPool = new ThreadPool();
        threadPool.setName(poolName.getKeyProperty("name"));
        threadPool.setMaxThreads(JmxTools.getIntAttr(server, poolName, "maxThreads"));

        if (JmxTools.hasAttribute(server, poolName, "maxSpareThreads")) {
          threadPool.setMaxSpareThreads(JmxTools.getIntAttr(server, poolName, "maxSpareThreads"));
          threadPool.setMinSpareThreads(JmxTools.getIntAttr(server, poolName, "minSpareThreads"));
        }

        threadPool.setCurrentThreadsBusy(JmxTools
            .getIntAttr(server, poolName, "currentThreadsBusy"));
        threadPool.setCurrentThreadCount(JmxTools
            .getIntAttr(server, poolName, "currentThreadCount"));

        /*
         * Tomcat 6.0.21+ will return -1 for maxThreads if the connector uses an executor for its
         * threads. In this case, don't add its ThreadPool to the results.
         */
        if (threadPool.getMaxThreads() > -1) {
          threadPools.add(threadPool);
        }
      } catch (InstanceNotFoundException e) {
        logger.error("Failed to query entire thread pool " + threadPoolObjectName);
        logger.debug("  Stack trace:", e);
      }
    }
    return threadPools;
  }

  public synchronized List<Connector> getConnectors(boolean includeRequestProcessors)
      throws Exception {

    boolean workerThreadNameSupported = true;

    if (!isInitialized()) {
      initialize();
    }

    List<Connector> connectors = new ArrayList<Connector>(poolNames.size());

    MBeanServer server = getContainerWrapper().getResourceResolver().getMBeanServer();

    for (ThreadPoolObjectName threadPoolObjectName : poolNames) {
      boolean remoteAddrAvailable = true;
      try {
        ObjectName poolName = threadPoolObjectName.getThreadPoolName();

        Connector connector = new Connector();
        connector.setName(poolName.getKeyProperty("name"));

        ObjectName grpName = threadPoolObjectName.getGlobalRequestProcessorName();

        connector.setMaxTime(JmxTools.getLongAttr(server, grpName, "maxTime"));
        connector.setProcessingTime(JmxTools.getLongAttr(server, grpName, "processingTime"));
        connector.setBytesReceived(JmxTools.getLongAttr(server, grpName, "bytesReceived"));
        connector.setBytesSent(JmxTools.getLongAttr(server, grpName, "bytesSent"));
        connector.setRequestCount(JmxTools.getIntAttr(server, grpName, "requestCount"));
        connector.setErrorCount(JmxTools.getIntAttr(server, grpName, "errorCount"));

        if (includeRequestProcessors) {
          List<ObjectName> wrkNames = threadPoolObjectName.getRequestProcessorNames();
          for (ObjectName wrkName : wrkNames) {
            try {
              RequestProcessor rp = new RequestProcessor();
              rp.setName(wrkName.getKeyProperty("name"));
              rp.setStage(JmxTools.getIntAttr(server, wrkName, "stage"));
              rp.setProcessingTime(JmxTools.getLongAttr(server, wrkName, "requestProcessingTime"));
              rp.setBytesSent(JmxTools.getLongAttr(server, wrkName, "requestBytesSent"));
              rp.setBytesReceived(JmxTools.getLongAttr(server, wrkName, "requestBytesReceived"));
              try {
                String remoteAddr = JmxTools.getStringAttr(server, wrkName, "remoteAddr");
                rp.setRemoteAddr(remoteAddr);
                rp.setRemoteAddrLocale(InetAddressLocator.getLocale(InetAddress.getByName(
                    remoteAddr).getAddress()));
              } catch (RuntimeOperationsException ex) {
                /*
                 * if it's not available for this request processor, then it's not available for any
                 * request processor in this thread pool
                 */
                remoteAddrAvailable = false;
              }
              rp.setVirtualHost(JmxTools.getStringAttr(server, wrkName, "virtualHost"));
              rp.setMethod(JmxTools.getStringAttr(server, wrkName, "method"));
              rp.setCurrentUri(JmxTools.getStringAttr(server, wrkName, "currentUri"));
              rp.setCurrentQueryString(JmxTools
                  .getStringAttr(server, wrkName, "currentQueryString"));
              rp.setProtocol(JmxTools.getStringAttr(server, wrkName, "protocol"));

              // Relies on https://issues.apache.org/bugzilla/show_bug.cgi?id=41128
              if (workerThreadNameSupported
                  && JmxTools.hasAttribute(server, wrkName, "workerThreadName")) {

                rp.setWorkerThreadName(JmxTools.getStringAttr(server, wrkName, "workerThreadName"));
                rp.setWorkerThreadNameSupported(true);
              } else {
                /*
                 * attribute should consistently either exist or be missing across all the workers
                 * so it does not make sense to check attribute existence if we have found once that
                 * it is not supported
                 */
                rp.setWorkerThreadNameSupported(false);
                workerThreadNameSupported = false;
              }
              connector.addRequestProcessor(rp);
            } catch (InstanceNotFoundException e) {
              logger.info("Failed to query RequestProcessor " + wrkName);
              logger.debug("  Stack trace:", e);
            }
          }
        }

        connectors.add(connector);
      } catch (InstanceNotFoundException e) {
        logger.error("Failed to query entire thread pool " + threadPoolObjectName);
        logger.debug("  Stack trace:", e);
      }
    }
    return connectors;
  }

}
