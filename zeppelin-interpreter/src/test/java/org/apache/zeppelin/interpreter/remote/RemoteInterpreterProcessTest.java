/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.junit.Test;

public class RemoteInterpreterProcessTest {
  private static final String INTERPRETER_SCRIPT =
          System.getProperty("os.name").startsWith("Windows") ?
                  "../bin/interpreter.cmd" :
                  "../bin/interpreter.sh";
  private static final int DUMMY_PORT=3678;

  @Test
  public void testStartStop() {
    InterpreterGroup intpGroup = new InterpreterGroup();
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess(
        INTERPRETER_SCRIPT, "nonexists", "fakeRepo", new HashMap<String, String>(),
        10 * 1000, null);
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference(intpGroup));
    assertEquals(2, rip.reference(intpGroup));
    assertEquals(true, rip.isRunning());
    assertEquals(1, rip.dereference());
    assertEquals(true, rip.isRunning());
    assertEquals(0, rip.dereference());
    assertEquals(false, rip.isRunning());
  }

  @Test
  public void testClientFactory() throws Exception {
    InterpreterGroup intpGroup = new InterpreterGroup();
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess(
        INTERPRETER_SCRIPT, "nonexists", "fakeRepo", new HashMap<String, String>(),
        mock(RemoteInterpreterEventPoller.class), 10 * 1000);
    rip.reference(intpGroup);
    assertEquals(0, rip.getNumActiveClient());
    assertEquals(0, rip.getNumIdleClient());

    Client client = rip.getClient();
    assertEquals(1, rip.getNumActiveClient());
    assertEquals(0, rip.getNumIdleClient());

    rip.releaseClient(client);
    assertEquals(0, rip.getNumActiveClient());
    assertEquals(1, rip.getNumIdleClient());

    rip.dereference();
  }

  @Test
  public void testStartStopRemoteInterpreter() throws TException, InterruptedException {
    RemoteInterpreterServer server = new RemoteInterpreterServer(3678);
    server.start();
    boolean running = false;
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < 10 * 1000) {
      if (server.isRunning()) {
        running = true;
        break;
      } else {
        Thread.sleep(200);
      }
    }
    Properties properties = new Properties();
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_PORT, "3678");
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_HOST, "localhost");
    InterpreterGroup intpGroup = mock(InterpreterGroup.class);
    when(intpGroup.getProperty()).thenReturn(properties);
    when(intpGroup.containsKey(Constants.EXISTING_PROCESS)).thenReturn(true);
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess(INTERPRETER_SCRIPT, "nonexists",
        "fakeRepo", new HashMap<String, String>(), 10 * 1000, null);
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference(intpGroup));
    assertEquals(true, rip.isRunning());
  }
  
  
  @Test
  public void testRemoteInterpreterWithMultipleInterpreterInGroup() throws TException, InterruptedException {
    RemoteInterpreterServer server = new RemoteInterpreterServer(3679);
    server.start();
    long startTime = System.currentTimeMillis();
    /*If RemoteInterpreterServer didn't start within 30 seconds than this test may fail
     * which might be due to issue in RemoteInterpreterServer
     */
    while (System.currentTimeMillis() - startTime < 30 * 1000) {
      if (server.isRunning()) {
        break;
      } else {
        Thread.sleep(200);
      }
    }
    Properties properties = new Properties();
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_PORT, "3679");
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_HOST, "localhost");
    InterpreterGroup intpGroup = mock(InterpreterGroup.class);
    when(intpGroup.getProperty()).thenReturn(properties);
    when(intpGroup.containsKey(Constants.EXISTING_PROCESS)).thenReturn(true);
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess(INTERPRETER_SCRIPT, "nonexists",
        "fakeRepo", new HashMap<String, String>(), 30 * 1000, null);
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference(intpGroup));
    // Calling reference once again to depict multiple intrepreters in a group
    assertEquals(2, rip.reference(intpGroup));
    assertEquals(true, rip.isRunning());
  }
  
  @Test
  public void testExistingInterpreterDereference() throws TException, InterruptedException {
    // Using Mocked RemoteInterpreterServer to reproduce the issue.
    CustomRemoteInterpreterServer server = new CustomRemoteInterpreterServer(3680);
    server.start();
    long startTime = System.currentTimeMillis();
    /*
     * If RemoteInterpreterServer didn't start within 30 seconds than this test may fail which might
     * be due to issue in RemoteInterpreterServer
     */
    while (System.currentTimeMillis() - startTime < 30 * 1000) {
      if (server.isRunning()) {
        break;
      } else {
        Thread.sleep(200);
      }
    }
    Properties properties = new Properties();
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_PORT, "3680");
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_HOST, "localhost");
    InterpreterGroup intpGroup = mock(InterpreterGroup.class);
    when(intpGroup.getProperty()).thenReturn(properties);
    when(intpGroup.containsKey(Constants.EXISTING_PROCESS)).thenReturn(true);
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess(INTERPRETER_SCRIPT, "nonexists",
        "fakeRepo", new HashMap<String, String>(), 1000, null);
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference(intpGroup));
    // Calling reference once again to depict multiple intrepreters in a group
    assertEquals(2, rip.reference(intpGroup));
    assertEquals(true, rip.isRunning());
    rip.dereference();
    rip.dereference();
  }


  class CustomRemoteInterpreterServer extends RemoteInterpreterServer {
    public CustomRemoteInterpreterServer(int port) throws TTransportException {
      super(port);
    }

    @Override
    public void shutdown() throws TException {
      // Keeping this method intentionally empty to depict that server is not stopped
    }

  }

}
