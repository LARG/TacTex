/*
 * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
 * Copyright (c) 2013-2016 Daniel Urieli and Peter Stone {urieli,pstone}@cs.utexas.edu               
 *
 *
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *
 *     Copyright 2012 the original author or authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an
 *     "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *     either express or implied. See the License for the specific language
 *     governing permissions and limitations under the License.
 */
package edu.utexas.cs.tactex.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Multi-session broker runner. The Spring context is re-built for each
 * session.
 * @author John Collins
 */
public class BrokerRunner
{
  private AbstractApplicationContext context;
  private PowerTacBroker broker;
  
  public BrokerRunner ()
  {
    super();
  }
  
  public void processCmdLine (String[] args)
  {
    OptionParser parser = new OptionParser();
    OptionSpec<String> jmsUrlOption =
            parser.accepts("jms-url").withRequiredArg().ofType(String.class);
    OptionSpec<File> configOption = 
            parser.accepts("config").withRequiredArg().ofType(File.class);
    OptionSpec<Integer> repeatCountOption = 
            parser.accepts("repeat-count").withRequiredArg().ofType(Integer.class);
    OptionSpec<Integer> repeatHoursOption = 
            parser.accepts("repeat-hours").withRequiredArg().ofType(Integer.class);
    OptionSpec<String> queueNameOption =
            parser.accepts("queue-name").withRequiredArg().ofType(String.class);
    OptionSpec<String> serverQueueOption =
            parser.accepts("server-queue").withRequiredArg().ofType(String.class);
    parser.accepts("no-ntp");

    // do the parse
    OptionSet options = parser.parse(args);

    File configFile = null;
    String jmsUrl = null;
    boolean noNtp = false;
    String queueName = null;
    String serverQueue = null;
    Integer repeatCount = 1;
    long end = 0l;
    
    try {
      // process broker options
      System.out.println("Options: ");
      if (options.has(configOption)) {
        configFile = options.valueOf(configOption);
        System.out.println("  config=" + configFile.getName());
      }
      if (options.has(jmsUrlOption)) {
        jmsUrl = options.valueOf(jmsUrlOption);
        System.out.println("  jms-url=" + jmsUrl);
      }
      if (options.has("no-ntp")) {
        noNtp = true;
        System.out.println("  no ntp - estimate offset");
      }
      if (options.has(repeatCountOption)) {
        repeatCount = options.valueOf(repeatCountOption);
        System.out.println("  repeat " + repeatCount + " times");
      }
      else if (options.has(repeatHoursOption)) {
        Integer repeatHours = options.valueOf(repeatCountOption);
        System.out.println("  repeat for " + repeatHours + " hours");
        long now = new Date().getTime();
        end = now + 1000 * 3600 * repeatHours;
      }
      if (options.has(queueNameOption)) {
        queueName = options.valueOf(queueNameOption);
        System.out.println("  queue-name=" + queueName);
      }
      if (options.has(serverQueueOption)) {
        serverQueue = options.valueOf(serverQueueOption);
        System.out.println("  server-queue=" + serverQueue);
      }
      
      // at this point, we are either done, or we need to repeat
      int counter = 0;
      while ((null != repeatCount && repeatCount > 0) ||
    		      (new Date().getTime() < end)) {
        counter += 1;

        counter = updateCounterFromExistingTraceFiles(counter);
    
        // Re-open the logfiles
        reopenLogs(counter);
        
        // initialize and run
        if (null == context) {
          context = new ClassPathXmlApplicationContext("broker.xml");
        }
        else {
          context.close();
          context.refresh();
        }
        // get the broker reference and delegate the rest
        context.registerShutdownHook();
        broker = (PowerTacBroker)context.getBeansOfType(PowerTacBroker.class).values().toArray()[0];
        System.out.println("Starting session " + counter);
        broker.startSession(configFile, jmsUrl, noNtp, queueName, serverQueue, end);
        if (null != repeatCount)
          repeatCount -= 1;
      }
    }
    catch (OptionException e) {
      System.err.println("Bad command argument: " + e.toString());
    }
  }

  /**
   * Finds existing trace files and advance the counter
   * to not override them
   * @param counter
   * @return
   */
  private int updateCounterFromExistingTraceFiles(int counter) {
    //String pattern = "broker*trace";
    //PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    try {    
      DirectoryStream<Path> directoryStream = null;
      String directory = new String("log");
      String pattern = new String("broker*trace");
      directoryStream = Files.newDirectoryStream(Paths.get(directory ), pattern);
      for(Path path : directoryStream){
        System.out.println("Files/Directories matching "+ pattern +": "+ path.toString());
        Pattern rgx = Pattern.compile("broker(\\d+).trace");
        Matcher matcher = rgx.matcher(path.toString());
        if (matcher.find()) {
          int i = Integer.parseInt(matcher.group(1));
          if (i >= counter) {
            counter = i + 1;
            System.out.println("updating to " + counter);
          }
        }
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return counter;
  }

  // reopen the logfiles for each session
  private void reopenLogs(int counter)
  {
    Logger root = Logger.getRootLogger();
    @SuppressWarnings("unchecked")
    Enumeration<Appender> rootAppenders = root.getAllAppenders();
    FileAppender logOutput = (FileAppender) rootAppenders.nextElement();
    // assume there's only the one, and that it's a file appender
    logOutput.setFile("log/broker" + counter + ".trace");
    logOutput.activateOptions();
    
    Logger state = Logger.getLogger("State");
    @SuppressWarnings("unchecked")
    Enumeration<Appender> stateAppenders = state.getAllAppenders();
    FileAppender stateOutput = (FileAppender) stateAppenders.nextElement();
    // assume there's only the one, and that it's a file appender
    stateOutput.setFile("log/broker" + counter + ".state");
    stateOutput.activateOptions();
  }
}
