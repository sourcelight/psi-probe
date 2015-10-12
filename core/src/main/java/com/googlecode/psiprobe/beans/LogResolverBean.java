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

import com.googlecode.psiprobe.model.Application;
import com.googlecode.psiprobe.model.DisconnectedLogDestination;
import com.googlecode.psiprobe.tools.ApplicationUtils;
import com.googlecode.psiprobe.tools.Instruments;
import com.googlecode.psiprobe.tools.logging.FileLogAccessor;
import com.googlecode.psiprobe.tools.logging.LogDestination;
import com.googlecode.psiprobe.tools.logging.catalina.CatalinaLoggerAccessor;
import com.googlecode.psiprobe.tools.logging.commons.CommonsLoggerAccessor;
import com.googlecode.psiprobe.tools.logging.jdk.Jdk14LoggerAccessor;
import com.googlecode.psiprobe.tools.logging.jdk.Jdk14ManagerAccessor;
import com.googlecode.psiprobe.tools.logging.log4j.Log4JLoggerAccessor;
import com.googlecode.psiprobe.tools.logging.log4j.Log4JManagerAccessor;
import com.googlecode.psiprobe.tools.logging.logback.LogbackFactoryAccessor;
import com.googlecode.psiprobe.tools.logging.logback.LogbackLoggerAccessor;
import com.googlecode.psiprobe.tools.logging.slf4jlogback.TomcatSlf4jLogbackFactoryAccessor;
import com.googlecode.psiprobe.tools.logging.slf4jlogback.TomcatSlf4jLogbackLoggerAccessor;

import org.apache.catalina.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Mark Lewis
 */
public class LogResolverBean {

  protected final Log logger = LogFactory.getLog(getClass());

  private ContainerWrapperBean containerWrapper;
  private List<String> stdoutFiles = new ArrayList<String>();

  public ContainerWrapperBean getContainerWrapper() {
    return containerWrapper;
  }

  public void setContainerWrapper(ContainerWrapperBean containerWrapper) {
    this.containerWrapper = containerWrapper;
  }

  public List<String> getStdoutFiles() {
    return stdoutFiles;
  }

  public void setStdoutFiles(List<String> stdoutFiles) {
    this.stdoutFiles = stdoutFiles;
  }

  public List<LogDestination> getLogDestinations(boolean all) {
    List<LogDestination> allAppenders = getAllLogDestinations();

    if (allAppenders != null) {
      //
      // this list has to guarantee the order in which elements are added
      //
      List<LogDestination> uniqueList = new LinkedList<LogDestination>();
      LogComparator cmp = new LogDestinationComparator(all);

      Collections.sort(allAppenders, cmp);
      for (LogDestination dest : allAppenders) {
        if (Collections.binarySearch(uniqueList, dest, cmp) < 0) {
          if (all || dest.getFile() == null || dest.getFile().exists()) {
            uniqueList.add(new DisconnectedLogDestination(dest));
          }
        }
      }
      return uniqueList;
    }
    return null;
  }

  public List<LogDestination> getLogSources(File logFile) {
    List<LogDestination> filtered = new LinkedList<LogDestination>();
    List<LogDestination> sources = getLogSources();
    for (LogDestination dest : sources) {
      if (logFile.equals(dest.getFile())) {
        filtered.add(dest);
      }
    }
    return filtered;
  }

  public List<LogDestination> getLogSources() {
    List<LogDestination> sources = new LinkedList<LogDestination>();

    List<LogDestination> allAppenders = getAllLogDestinations();
    if (allAppenders != null) {
      LogComparator cmp = new LogSourceComparator();

      Collections.sort(allAppenders, cmp);
      for (LogDestination dest : allAppenders) {
        if (Collections.binarySearch(sources, dest, cmp) < 0) {
          sources.add(new DisconnectedLogDestination(dest));
        }
      }
    }
    return sources;
  }

  private List<LogDestination> getAllLogDestinations() {
    if (Instruments.isInitialized()) {
      List<LogDestination> allAppenders = new ArrayList<LogDestination>();

      //
      // interrogate classloader hierarchy
      //
      ClassLoader cl2 = Thread.currentThread().getContextClassLoader().getParent();
      while (cl2 != null) {
        interrogateClassLoader(cl2, null, allAppenders);
        cl2 = cl2.getParent();
      }

      //
      // check for known stdout files, such as "catalina.out"
      //
      interrogateStdOutFiles(allAppenders);

      //
      // interrogate webapp classloaders and available loggers
      //
      List<Context> contexts = getContainerWrapper().getTomcatContainer().findContexts();
      for (Context ctx : contexts) {
        interrogateContext(ctx, allAppenders);
      }

      return allAppenders;
    }
    return null;
  }

  public LogDestination getLogDestination(String logType, String webapp, boolean context,
      boolean root, String logName, String logIndex) {

    Context ctx = null;
    Application application = null;
    if (webapp != null) {
      ctx = getContainerWrapper().getTomcatContainer().findContext(webapp);
      if (ctx != null) {
        application = ApplicationUtils.getApplication(ctx, getContainerWrapper());
      }
    }

    if ("stdout".equals(logType) && logName != null) {
      return getStdoutLogDestination(logName);
    } else if ("catalina".equals(logType) && ctx != null) {
      return getCatalinaLogDestination(ctx, application);
    } else if (logIndex != null
        && ("jdk".equals(logType) || "log4j".equals(logType) || "logback".equals(logType))
        || "tomcatSlf4jLogback".equals(logType)) {
      if (context && ctx != null) {
        return getCommonsLogDestination(ctx, application, logIndex);
      }
      ClassLoader cl;
      ClassLoader prevCl = null;
      if (ctx != null) {
        cl = ctx.getLoader().getClassLoader();
        prevCl = ClassUtils.overrideThreadContextClassLoader(cl);
      } else {
        cl = Thread.currentThread().getContextClassLoader().getParent();
      }
      try {
        if ((root || logName != null) && logIndex != null) {
          if ("jdk".equals(logType)) {
            return getJdk14LogDestination(cl, application, root, logName, logIndex);
          } else if ("log4j".equals(logType)) {
            return getLog4JLogDestination(cl, application, root, logName, logIndex);
          } else if ("logback".equals(logType)) {
            return getLogbackLogDestination(cl, application, root, logName, logIndex);
          } else if ("tomcatSlf4jLogback".equals(logType)) {
            return getLogbackTomcatJuliLogDestination(cl, application, root, logName, logIndex);
          }
        }
      } finally {
        if (prevCl != null) {
          ClassUtils.overrideThreadContextClassLoader(prevCl);
        }
      }
    }
    return null;
  }

  private void interrogateContext(Context ctx, List<LogDestination> allAppenders) {
    Application application = ApplicationUtils.getApplication(ctx, getContainerWrapper());
    ClassLoader cl = ctx.getLoader().getClassLoader();

    try {
      Object contextLogger = getContainerWrapper().getTomcatContainer().getLogger(ctx);
      if (contextLogger != null) {
        if (contextLogger.getClass().getName().startsWith("org.apache.commons.logging")) {
          CommonsLoggerAccessor commonsAccessor = new CommonsLoggerAccessor();
          commonsAccessor.setTarget(contextLogger);
          commonsAccessor.setApplication(application);
          allAppenders.addAll(commonsAccessor.getDestinations());
        } else if (contextLogger.getClass().getName().startsWith("org.apache.catalina.logger")) {
          CatalinaLoggerAccessor catalinaAccessor = new CatalinaLoggerAccessor();
          catalinaAccessor.setApplication(application);
          catalinaAccessor.setTarget(contextLogger);
          allAppenders.add(catalinaAccessor);
        }
      }
    } catch (Throwable e) {
      logger.error("Could not interrogate context logger for " + ctx.getName()
          + ". Enable debug logging to see the trace stack");
      logger.debug("  Stack trace:", e);
      // make sure we always re-throw ThreadDeath
      if (e instanceof ThreadDeath) {
        throw (ThreadDeath) e;
      }
    }

    if (application.isAvailable()) {
      ClassLoader prevCl = ClassUtils.overrideThreadContextClassLoader(cl);
      try {
        interrogateClassLoader(cl, application, allAppenders);
      } catch (Exception e) {
        logger.error("Could not interrogate classloader loggers for " + ctx.getName()
            + ". Enable debug logging to see the trace stack");
        logger.debug("  Stack trace:", e);
      } finally {
        if (prevCl != null) {
          ClassUtils.overrideThreadContextClassLoader(prevCl);
        }
      }
    }
  }

  private void interrogateClassLoader(ClassLoader cl, Application application,
      List<LogDestination> appenders) {

    String applicationName =
        (application != null ? "application \"" + application.getName() + "\"" : "server");

    // check for JDK loggers
    try {
      Jdk14ManagerAccessor jdk14accessor = new Jdk14ManagerAccessor(cl);
      jdk14accessor.setApplication(application);
      appenders.addAll(jdk14accessor.getHandlers());
    } catch (Throwable t) {
      // make sure we always re-throw ThreadDeath
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("Could not resolve JDK loggers for " + applicationName, t);
    }

    // check for Log4J loggers
    try {
      Log4JManagerAccessor log4JAccessor = new Log4JManagerAccessor(cl);
      log4JAccessor.setApplication(application);
      appenders.addAll(log4JAccessor.getAppenders());
    } catch (Throwable t) {
      //
      // make sure we always re-throw ThreadDeath
      //
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("Could not resolve log4j loggers for " + applicationName, t);
    }

    // check for Logback loggers
    try {
      LogbackFactoryAccessor logbackAccessor = new LogbackFactoryAccessor(cl);
      logbackAccessor.setApplication(application);
      appenders.addAll(logbackAccessor.getAppenders());
    } catch (Throwable t) {
      //
      // make sure we always re-throw ThreadDeath
      //
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("Could not resolve logback loggers for " + applicationName, t);
    }

    // check for Logback loggers for tomcat-slf4j-logback
    try {
      TomcatSlf4jLogbackFactoryAccessor tomcatSlf4jLogbackAccessor =
          new TomcatSlf4jLogbackFactoryAccessor(cl);
      tomcatSlf4jLogbackAccessor.setApplication(application);
      appenders.addAll(tomcatSlf4jLogbackAccessor.getAppenders());
    } catch (Throwable t) {
      //
      // make sure we always re-throw ThreadDeath
      //
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("Could not resolve tomcat-slf4j-logback loggers for " + applicationName, t);
    }
  }

  private void interrogateStdOutFiles(List<LogDestination> appenders) {
    for (String fileName : stdoutFiles) {
      FileLogAccessor fla = resolveStdoutLogDestination(fileName);
      if (fla != null) {
        appenders.add(fla);
      }
    }
  }

  private LogDestination getStdoutLogDestination(String logName) {
    for (String fileName : stdoutFiles) {
      if (fileName.equals(logName)) {
        FileLogAccessor fla = resolveStdoutLogDestination(fileName);
        if (fla != null) {
          return fla;
        }
      }
    }
    return null;
  }

  private FileLogAccessor resolveStdoutLogDestination(String fileName) {
    File stdout = new File(System.getProperty("catalina.base"), "logs/" + fileName);
    if (stdout.exists()) {
      FileLogAccessor fla = new FileLogAccessor();
      fla.setName(fileName);
      fla.setFile(stdout);
      return fla;
    }
    return null;
  }

  private LogDestination getCatalinaLogDestination(Context ctx, Application application) {
    Object log = getContainerWrapper().getTomcatContainer().getLogger(ctx);
    if (log != null) {
      CatalinaLoggerAccessor logAccessor = new CatalinaLoggerAccessor();
      logAccessor.setTarget(log);
      logAccessor.setApplication(application);
      if (logAccessor.getFile().exists()) {
        return logAccessor;
      }
    }
    return null;
  }

  private LogDestination getCommonsLogDestination(Context ctx, Application application,
      String logIndex) {

    Object contextLogger = getContainerWrapper().getTomcatContainer().getLogger(ctx);
    CommonsLoggerAccessor commonsAccessor = new CommonsLoggerAccessor();
    commonsAccessor.setTarget(contextLogger);
    commonsAccessor.setApplication(application);
    return commonsAccessor.getDestination(logIndex);
  }

  private LogDestination getJdk14LogDestination(ClassLoader cl, Application application,
      boolean root, String logName, String handlerIndex) {

    try {
      Jdk14ManagerAccessor manager = new Jdk14ManagerAccessor(cl);
      manager.setApplication(application);
      Jdk14LoggerAccessor log = (root ? manager.getRootLogger() : manager.getLogger(logName));
      if (log != null) {
        return log.getHandler(handlerIndex);
      }
    } catch (Throwable t) {
      // always re-throw ThreadDeath when catching Throwable
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("getJdk14LogDestination failed", t);
    }
    return null;
  }

  private LogDestination getLog4JLogDestination(ClassLoader cl, Application application,
      boolean root, String logName, String appenderName) {

    try {
      Log4JManagerAccessor manager = new Log4JManagerAccessor(cl);
      manager.setApplication(application);
      Log4JLoggerAccessor log = (root ? manager.getRootLogger() : manager.getLogger(logName));
      if (log != null) {
        return log.getAppender(appenderName);
      }
    } catch (Throwable t) {
      // always re-throw ThreadDeath when catching Throwable
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("getLog4JLogDestination failed", t);
    }
    return null;
  }

  private LogDestination getLogbackLogDestination(ClassLoader cl, Application application,
      boolean root, String logName, String appenderName) {

    try {
      LogbackFactoryAccessor manager = new LogbackFactoryAccessor(cl);
      manager.setApplication(application);
      LogbackLoggerAccessor log = (root ? manager.getRootLogger() : manager.getLogger(logName));
      if (log != null) {
        return log.getAppender(appenderName);
      }
    } catch (Throwable t) {
      // always re-throw ThreadDeath when catching Throwable
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("getLogbackLogDestination failed", t);
    }
    return null;
  }

  private LogDestination getLogbackTomcatJuliLogDestination(ClassLoader cl,
      Application application, boolean root, String logName, String appenderName) {

    try {
      TomcatSlf4jLogbackFactoryAccessor manager = new TomcatSlf4jLogbackFactoryAccessor(cl);
      manager.setApplication(application);
      TomcatSlf4jLogbackLoggerAccessor log =
          (root ? manager.getRootLogger() : manager.getLogger(logName));
      if (log != null) {
        return log.getAppender(appenderName);
      }
    } catch (Throwable t) {
      // always re-throw ThreadDeath when catching Throwable
      if (t instanceof ThreadDeath) {
        throw (ThreadDeath) t;
      }
      logger.debug("getTomcatSlf4jLogbackLogDestination failed", t);
    }
    return null;
  }

  private abstract static class LogComparator implements Comparator<LogDestination> {

    protected static final char DELIM = '!';

    @Override
    public final int compare(LogDestination o1, LogDestination o2) {
      String name1 = convertToString(o1);
      String name2 = convertToString(o2);
      return name1.compareTo(name2);
    }

    protected abstract String convertToString(LogDestination d1);

  }

  private static class LogDestinationComparator extends LogComparator {

    private boolean all;

    public LogDestinationComparator(boolean all) {
      this.all = all;
    }

    @Override
    protected String convertToString(LogDestination dest) {
      File file = dest.getFile();
      String fileName = (file == null ? "" : file.getAbsolutePath());
      String name;
      if (all) {
        Application app = dest.getApplication();
        String appName = (app == null ? "" + DELIM : app.getName());
        String context = (dest.isContext() ? "is" : "not");
        String root = (dest.isRoot() ? "is" : "not");
        String logType = dest.getLogType();
        name = appName + DELIM + context + DELIM + root + DELIM + logType + DELIM + fileName;
      } else {
        name = fileName;
      }
      return name;
    }

  }

  private static class LogSourceComparator extends LogComparator {

    @Override
    protected String convertToString(LogDestination dest) {
      File file = dest.getFile();
      String fileName = (file == null ? "" : file.getAbsolutePath());
      Application app = dest.getApplication();
      String appName = (app == null ? "" + DELIM : app.getName());
      String logType = dest.getLogType();
      String context = (dest.isContext() ? "is" : "not");
      String root = (dest.isRoot() ? "is" : "not");
      String logName = dest.getName();
      return appName + DELIM + logType + DELIM + context + DELIM + root + DELIM + logName + DELIM
          + fileName;
    }

  }

}
