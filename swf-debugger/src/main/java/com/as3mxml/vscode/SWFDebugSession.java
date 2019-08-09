/*
Copyright 2016-2019 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.as3mxml.vscode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.as3mxml.vscode.debug.DebugSession;
import com.as3mxml.vscode.debug.events.BreakpointEvent;
import com.as3mxml.vscode.debug.events.InitializedEvent;
import com.as3mxml.vscode.debug.events.OutputEvent;
import com.as3mxml.vscode.debug.events.StoppedEvent;
import com.as3mxml.vscode.debug.events.TerminatedEvent;
import com.as3mxml.vscode.debug.protocol.Request;
import com.as3mxml.vscode.debug.protocol.Response;
import com.as3mxml.vscode.debug.requests.AttachRequest;
import com.as3mxml.vscode.debug.requests.EvaluateRequest;
import com.as3mxml.vscode.debug.requests.ExceptionInfoRequest;
import com.as3mxml.vscode.debug.requests.InitializeRequest;
import com.as3mxml.vscode.debug.requests.LaunchRequest;
import com.as3mxml.vscode.debug.requests.ScopesRequest;
import com.as3mxml.vscode.debug.requests.SetBreakpointsRequest;
import com.as3mxml.vscode.debug.requests.Source;
import com.as3mxml.vscode.debug.requests.SourceBreakpoint;
import com.as3mxml.vscode.debug.requests.StackTraceRequest;
import com.as3mxml.vscode.debug.requests.VariablesRequest;
import com.as3mxml.vscode.debug.responses.Breakpoint;
import com.as3mxml.vscode.debug.responses.Capabilities;
import com.as3mxml.vscode.debug.responses.EvaluateResponseBody;
import com.as3mxml.vscode.debug.responses.ExceptionDetails;
import com.as3mxml.vscode.debug.responses.ExceptionInfoResponseBody;
import com.as3mxml.vscode.debug.responses.Scope;
import com.as3mxml.vscode.debug.responses.ScopesResponseBody;
import com.as3mxml.vscode.debug.responses.SetBreakpointsResponseBody;
import com.as3mxml.vscode.debug.responses.StackFrame;
import com.as3mxml.vscode.debug.responses.StackTraceResponseBody;
import com.as3mxml.vscode.debug.responses.Thread;
import com.as3mxml.vscode.debug.responses.ThreadsResponseBody;
import com.as3mxml.vscode.debug.responses.Variable;
import com.as3mxml.vscode.debug.responses.VariablesResponseBody;
import com.as3mxml.vscode.debug.utils.DeviceInstallUtils;
import com.as3mxml.vscode.debug.utils.DeviceInstallUtils.DeviceCommandResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import flash.tools.debugger.AIRLaunchInfo;
import flash.tools.debugger.CommandLineException;
import flash.tools.debugger.DefaultDebuggerCallbacks;
import flash.tools.debugger.Frame;
import flash.tools.debugger.InProgressException;
import flash.tools.debugger.Isolate;
import flash.tools.debugger.IsolateSession;
import flash.tools.debugger.Location;
import flash.tools.debugger.NoResponseException;
import flash.tools.debugger.NotConnectedException;
import flash.tools.debugger.NotSuspendedException;
import flash.tools.debugger.Player;
import flash.tools.debugger.PlayerDebugException;
import flash.tools.debugger.SourceFile;
import flash.tools.debugger.SuspendReason;
import flash.tools.debugger.SwfInfo;
import flash.tools.debugger.Value;
import flash.tools.debugger.VariableAttribute;
import flash.tools.debugger.VariableType;
import flash.tools.debugger.VersionException;
import flash.tools.debugger.events.BreakEvent;
import flash.tools.debugger.events.DebugEvent;
import flash.tools.debugger.events.ExceptionFault;
import flash.tools.debugger.events.FaultEvent;
import flash.tools.debugger.events.IsolateCreateEvent;
import flash.tools.debugger.events.TraceEvent;
import flash.tools.debugger.expression.ASTBuilder;
import flash.tools.debugger.expression.NoSuchVariableException;
import flash.tools.debugger.expression.PlayerFaultException;
import flash.tools.debugger.expression.ValueExp;
import flash.tools.debugger.threadsafe.ThreadSafeBootstrap;
import flash.tools.debugger.threadsafe.ThreadSafeSession;
import flash.tools.debugger.threadsafe.ThreadSafeSessionManager;

public class SWFDebugSession extends DebugSession
{
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_HX = ".hx";
    private static final String FILE_EXTENSION_EXE = ".exe";
    private static final String FILE_EXTENSION_BAT = ".bat";
    private static final String FILE_EXTENSION_XML = ".xml";
    private static final String ADL_BASE_NAME = "bin/adl";
    private static final String ADT_BASE_NAME = "bin/adt";
    private static final String ADB_BASE_NAME = "lib/android/bin/adb";
    private static final String IDB_BASE_NAME = "lib/aot/bin/iOSBin/idb";
    private static final String FLEXLIB_PROPERTY = "flexlib";
    private static final String WORKSPACE_PROPERTY = "workspace";
    private static final String SDK_PATH_SIGNATURE_UNIX = "/frameworks/projects/";
    private static final String SDK_PATH_SIGNATURE_WINDOWS = "\\frameworks\\projects\\";
    private static final String PLATFORM_IOS = "ios";
    private static final long LOCAL_VARIABLES_REFERENCE = 1;
    private ThreadSafeSession swfSession;
    private Process swfRunProcess;
    private java.lang.Thread sessionThread;
    private boolean cancelRunner = false;
    private boolean waitingForResume = false;
    private FaultEvent previousFaultEvent = null;
    private Path flexlib;
    private Path flexHome;
    private Path adlPath;
    private Path adtPath;
    private Path adbPath;
    private Path idbPath;
    private Map<String,PendingBreakpoints> pendingBreakpoints;
    private Map<String,LogLocation> savedLogLocations;
    private int nextBreakpointID = 1;
    private String forwardedPortPlatform = null;
    private int forwardedPort = -1;

    private class PendingBreakpoints
    {
        public PendingBreakpoints(SourceBreakpoint[] breakpoints)
        {
            this.breakpoints = breakpoints;
            idStart = nextBreakpointID;
        }

        SourceBreakpoint[] breakpoints;
        int idStart;
    }

    private class LogLocation
    {
        public LogLocation(Location location, String logMessage)
        {
            this.location = location;
            this.logMessage = logMessage;
        }

        public Location location;
        public String logMessage;
    }

    private class RunProcessRunner implements Runnable
    {
        public RunProcessRunner()
        {
        }

        public void run()
        {
            while (true)
            {
                if (cancelRunner)
                {
                    break;
                }
                try
                {
                    swfRunProcess.exitValue();
                    cancelRunner = true;
                    sendEvent(new TerminatedEvent());
                }
                catch(IllegalThreadStateException e)
                {
                    //safe to ignore
                }
                try
                {
                    java.lang.Thread.sleep(50);
                }
                catch (InterruptedException ie)
                {
                }
            }
        }
    }

    private class SessionRunner implements Runnable
    {
        private boolean initialized = false;
        private List<Isolate> isolates = new ArrayList<>();

        public SessionRunner()
        {
        }

        public void run()
        {
            while (true)
            {
                if (cancelRunner)
                {
                    break;
                }
                try
                {
                    boolean logPoint = false;
                    while (swfSession.getEventCount() > 0)
                    {
                        DebugEvent event = swfSession.nextEvent();
                        if(handleEvent(event))
                        {
                            logPoint = true;
                        }
                    }
                    while (swfSession.isSuspended() && !waitingForResume)
                    {
                        handleSuspended(logPoint);
                        break;
                    }
                    for(Isolate isolate : isolates)
                    {
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        while (isolateSession.isSuspended())
                        {
                            switch(isolateSession.suspendReason())
                            {
                                default:
                                {
                                    sendOutputEvent("Unknown isolate suspend reason: " + swfSession.suspendReason() + "\n");
                                    break;
                                }
                            }
                        }
                    }
                }
                catch (NotConnectedException e)
                {
                    cancelRunner = true;
                    sendEvent(new TerminatedEvent());
                }
                catch (Exception e)
                {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    e.printStackTrace(new PrintStream(buffer));
                    sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
                }
                try
                {
                    java.lang.Thread.sleep(50);
                }
                catch (InterruptedException ie)
                {
                }
            }
        }

        private boolean handleEvent(DebugEvent event) throws NotConnectedException, NoResponseException, NotSuspendedException
        {
            if (event instanceof TraceEvent)
            {
                TraceEvent traceEvent = (TraceEvent) event;
                String output = traceEvent.information;
                if (output.length() == 0)
                {
                    //empty string or empty line added with \n
                    output = "\n";
                }
                else if (output.charAt(output.length() - 1) != '\n')
                {
                    output += '\n';
                }
                OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                //we can't populate the location for a trace() to the
                //console because the result of getFrames() is empty
                //when the runtime isn't suspended
                body.output = output;
                sendEvent(new OutputEvent(body));
            }
            else if (event instanceof FaultEvent)
            {
                FaultEvent faultEvent = (FaultEvent) event;
                String output = faultEvent.information + "\n" + faultEvent.stackTrace();
                if (output.charAt(output.length() - 1) != '\n')
                {
                    output += '\n';
                }
                OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                populateLocationInOutputBody(body);
                body.output = output;
                body.category = OutputEvent.CATEGORY_STDERR;
                sendEvent(new OutputEvent(body));
                previousFaultEvent = faultEvent;
            }
            else if (event instanceof BreakEvent)
            {
                BreakEvent breakEvent = (BreakEvent) event;
                for(LogLocation logLocation : savedLogLocations.values())
                {
                    Location location = logLocation.location;
                    if(breakEvent.fileId == location.getFile().getId()
                            && breakEvent.line == location.getLine())
                    {
                        OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                        populateLocationInOutputBody(body);
                        body.output = logLocation.logMessage;
                        sendEvent(new OutputEvent(body));
                        return true;
                    }
                }
            }
            else if (event instanceof IsolateCreateEvent)
            {
                //a worker has been created
                IsolateCreateEvent isolateEvent = (IsolateCreateEvent) event;
                Isolate isolate = isolateEvent.isolate;
                isolates.add(isolate);
                IsolateSession session = swfSession.getWorkerSession(isolate.getId());
                session.resume();
            }
            return false;
        }

        private void handleSuspended(boolean logPoint) throws NotConnectedException, NoResponseException, NotSuspendedException
        {
            StoppedEvent.StoppedBody body = null;
            switch (swfSession.suspendReason())
            {
                case SuspendReason.ScriptLoaded:
                {
                    if (initialized)
                    {
                        refreshPendingBreakpoints();
                        swfSession.resume();
                    }
                    else
                    {
                        //initialize when the first script is loaded
                        initialized = true;
                        sendEvent(new InitializedEvent());
                    }
                    break;
                }
                case SuspendReason.Breakpoint:
                {
                    if(logPoint)
                    {
                        //if it was a logpoint, then resume
                        //immediately because we should not stop
                        swfSession.resume();
                    }
                    else
                    {
                        body = new StoppedEvent.StoppedBody();
                        body.reason = StoppedEvent.REASON_BREAKPOINT;
                    }
                    break;
                }
                case SuspendReason.StopRequest:
                {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_PAUSE;
                    break;
                }
                case SuspendReason.Fault:
                {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_EXCEPTION;
                    body.description = "Paused on exception";
                    if(previousFaultEvent != null)
                    {
                        body.text = previousFaultEvent.information;
                    }
                    break;
                }
                default:
                {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_UNKNOWN;
                    sendOutputEvent("Unknown suspend reason: " + swfSession.suspendReason() + "\n");
                }
            }
            if (body != null)
            {
                waitingForResume = true;
                body.threadId = Isolate.DEFAULT_ID;
                sendEvent(new StoppedEvent(body));
            }
        }
        
        private void populateLocationInOutputBody(OutputEvent.OutputBody body)
        {
            try
            {
                Frame[] swfFrames = swfSession.getFrames();
                if (swfFrames.length > 0)
                {
                    Frame swfFrame = swfFrames[0];
                    Location location = swfFrame.getLocation();
                    SourceFile file = location.getFile();
                    if (file != null)
                    {
                        Source source = sourceFileToSource(file);
                        body.source = source;
                        body.line = location.getLine();
                        body.column = 0;
                    }
                }
            }
            catch (NotConnectedException e)
            {
                sendOutputEvent("Not connected\n");
                return;
            }
        }
    }

    public SWFDebugSession()
    {
        super(false);
        pendingBreakpoints = new HashMap<>();
        savedLogLocations = new HashMap<>();
        String flexlibPath = System.getProperty(FLEXLIB_PROPERTY);
        if (flexlibPath != null)
        {
            flexlib = Paths.get(flexlibPath);
            flexHome = flexlib.getParent();
            String adlRelativePath = ADL_BASE_NAME;
            String adtRelativePath = ADT_BASE_NAME;
            String adbRelativePath = ADB_BASE_NAME;
            String idbRelativePath = IDB_BASE_NAME;
            if (System.getProperty("os.name").toLowerCase().startsWith("windows"))
            {
                adlRelativePath += FILE_EXTENSION_EXE;
                adtRelativePath += FILE_EXTENSION_BAT;
                adbRelativePath += FILE_EXTENSION_EXE;
                idbRelativePath += FILE_EXTENSION_EXE;
            }
            adlPath = flexHome.resolve(adlRelativePath);
            adtPath = flexHome.resolve(adtRelativePath);
            adbPath = flexHome.resolve(adbRelativePath);
            idbPath = flexHome.resolve(idbRelativePath);
        }
    }

    public void initialize(Response response, InitializeRequest.InitializeRequestArguments args)
    {
        OutputEvent.OutputBody body = new OutputEvent.OutputBody();
        sendEvent(new OutputEvent(body));

        Capabilities capabilities = new Capabilities();
        capabilities.supportsExceptionInfoRequest = true;
        capabilities.supportsLogPoints = true;
        sendResponse(response, capabilities);
    }

    public void launch(Response response, LaunchRequest.LaunchRequestArguments args)
    {
        SWFLaunchRequestArguments swfArgs = (SWFLaunchRequestArguments) args;
        ThreadSafeSessionManager manager = ThreadSafeBootstrap.sessionManager();
        swfSession = null;
        swfRunProcess = null;
        forwardedPortPlatform = null;
        forwardedPort = -1;
        try
        {
            manager.startListening();
            if (manager.supportsLaunch())
            {
                String program = swfArgs.program;
                if (!program.startsWith("http:") && !program.startsWith("https:"))
                {
                    Path programPath = Paths.get(program);
                    if (!programPath.isAbsolute())
                    {
                        //if it's not an absolute path, we'll treat it as a
                        //relative path within the workspace
                        String workspacePath = System.getProperty(WORKSPACE_PROPERTY);
                        if (workspacePath != null)
                        {
                            program = Paths.get(workspacePath)
                                    .resolve(programPath).toAbsolutePath().toString();
                        }
                    }
                }
                AIRLaunchInfo airLaunchInfo = null;
                if (program.endsWith(FILE_EXTENSION_XML) && adlPath != null)
                {
                    String extdir = swfArgs.extdir;
                    if (extdir != null)
                    {
                        Path extdirPath = Paths.get(extdir);
                        if (!extdirPath.isAbsolute())
                        {
                            String workspacePath = System.getProperty(WORKSPACE_PROPERTY);
                            if (workspacePath != null)
                            {
                                extdir = Paths.get(workspacePath)
                                        .resolve(extdirPath).toAbsolutePath().toString();
                            }
                        }
                    }
                    airLaunchInfo = new AIRLaunchInfo();
                    airLaunchInfo.profile = swfArgs.profile;
                    airLaunchInfo.screenSize = swfArgs.screensize;
                    airLaunchInfo.dpi = swfArgs.screenDPI;
                    airLaunchInfo.versionPlatform = swfArgs.versionPlatform;
                    airLaunchInfo.airDebugLauncher = adlPath.toFile();
                    airLaunchInfo.extDir = extdir;
                    airLaunchInfo.applicationArgumentsArray = swfArgs.args;
                }

                Player player = null;
                CustomRuntimeLauncher launcher = null;
                if (swfArgs.runtimeExecutable != null)
                {
                    //if runtimeExecutable is specified, we'll launch that
                    launcher = new CustomRuntimeLauncher(swfArgs.runtimeExecutable, swfArgs.runtimeArgs);
                    if (airLaunchInfo != null)
                    {
                        launcher.isAIR = true;
                    }
                }
                else
                {
                    //otherwise, let the SWF debugger automatically figure out
                    //which runtime is required based on the program path
                    String playerPath = program;
                    try
                    {

                        URI uri = Paths.get(playerPath).toUri();
                        playerPath = uri.toString();
                    }
                    catch (Exception e)
                    {
                        //safe to ignore
                    }
                    player = manager.playerForUri(playerPath, airLaunchInfo);
                    if (player == null
                            && !playerPath.startsWith("http:")
                            && !playerPath.startsWith("https:")
                            && playerPath.endsWith(".swf"))
                    {
                        //fallback: try to find standalone Flash Player
                        if (System.getProperty("os.name").toLowerCase().startsWith("windows"))
                        {
                            launcher = findWindowsStandalonePlayer();
                        }
                        else if (!System.getProperty("os.name").toLowerCase().startsWith("mac os")) //linux
                        {
                            launcher = findLinuxStandalonePlayer();
                        }
                    }
                }
                if (player == null && launcher == null)
                {
                    sendErrorResponse(response, 10001, "Error launching SWF debug session. Runtime not found for program: " + program);
                    return;
                }
                if(swfArgs.noDebug)
                {
                    if(launcher != null)
                    {
                        swfRunProcess = manager.launchForRun(program, airLaunchInfo, null, null, launcher);
                    }
                    else
                    {
                        swfRunProcess = manager.launchForRun(program, airLaunchInfo, null, null);
                    }
                }
                else
                {
                    //notice that we use the value of launcher if it isn't null, but
                    //we don't actually use the value of player. player's purpose is
                    //more about verifying that a runtime can be auto-detected.
                    if(launcher != null)
                    {
                        swfSession = (ThreadSafeSession) manager.launch(program, airLaunchInfo, !swfArgs.noDebug, null, null, launcher);
                    }
                    else
                    {
                        swfSession = (ThreadSafeSession) manager.launch(program, airLaunchInfo, !swfArgs.noDebug, null, null);
                    }
                }
            }
        }
        catch (CommandLineException e)
        {
            sendErrorResponse(response, 10001, "Error launching SWF debug session. Process exited with code: " + e.getExitValue() + "\n\n" + e.getMessage() + "\n\n" + e.getCommandOutput());
            return;
        }
        catch (IOException e)
        {
            System.err.println("Exception in debugger on launch request:");
            e.printStackTrace(System.err);
            sendErrorResponse(response, 10001, "Error launching SWF debug session.");
            return;
        }
        if(swfSession != null)
        {
            try
            {
                swfSession.bind();
            }
            catch (VersionException e)
            {
                System.err.println("Exception in debugger on bind session:");
                e.printStackTrace(System.err);
            }
        }
        try
        {
            manager.stopListening();
        }
        catch (IOException e)
        {
            System.err.println("Exception in debugger on stop listening:");
            e.printStackTrace(System.err);
        }
        sendResponse(response);
        cancelRunner = false;
        if(swfSession != null)
        {
            sessionThread = new java.lang.Thread(new SessionRunner());
            sessionThread.start();
        }
        else if(swfRunProcess != null)
        {
            sessionThread = new java.lang.Thread(new RunProcessRunner());
            sessionThread.start();
        }
    }

    /**
     * On Windows, if the standalone Flash Player wasn't found by the debugger,
     * we're going try one last thing. We check the registry to see if the user
     * made a file association in explorer.
     */
    private CustomRuntimeLauncher findWindowsStandalonePlayer()
    {
        try
        {
            DefaultDebuggerCallbacks callbacks = new DefaultDebuggerCallbacks();
            String association = callbacks.queryWindowsRegistry("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.swf\\UserChoice", "ProgId");
            if (association != null)
            {
                String path = callbacks.queryWindowsRegistry("HKEY_CLASSES_ROOT\\" + association + "\\shell\\open\\command", null);
                if (path != null)
                {
                    if (path.startsWith("\""))
                    {
                        //strip any quotes that might be wrapping
                        //the executable path
                        path = path.substring(1, path.indexOf("\"", 1));
                    }
                    return new CustomRuntimeLauncher(path);
                }
            }
        }
        catch (IOException e)
        {
            //safe to ignore
        }
        return null;
    }

    /**
     * On Linux, if the standalone Flash Player wasn't found by the debugger,
     * we're going try one last thing. We check a different default file name
     * that might exist that the debugger doesn't know about.
     */
    private CustomRuntimeLauncher findLinuxStandalonePlayer()
    {
        try
        {
            String[] cmd = {"/bin/sh", "-c", "which flashplayerdebugger"};
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null)
            {
                File f = new File(line);
                if (f.exists())
                {
                    return new CustomRuntimeLauncher(f.getAbsolutePath());
                }
            }
        }
        catch (IOException e)
        {
            //safe to ignore
        }
        return null;
    }

    public void attach(Response response, AttachRequest.AttachRequestArguments args)
    {
        SWFAttachRequestArguments swfArgs = (SWFAttachRequestArguments) args;
        forwardedPortPlatform = null;
        forwardedPort = -1;
        if(swfArgs.platform != null)
        {
            if(!installOnDevice(swfArgs.platform, swfArgs.connect, swfArgs.port))
            {
                response.success = false;
                sendResponse(response);
                return;
            }
        }
        boolean success = attach(response, swfArgs);
        if(!success)
        {
            cleanupForwardedPort();
        }
    }

    private void sendOutputEvent(String message)
    {
        OutputEvent.OutputBody body = new OutputEvent.OutputBody();
        body.output = message;
        sendEvent(new OutputEvent(body));
    }

    private boolean installOnDevice(String platform, boolean connect, int port)
    {
        sendOutputEvent("Preparing to install Adobe AIR application...\n");
        Path workspacePath = Paths.get(System.getProperty(WORKSPACE_PROPERTY));
        String applicationID = DeviceInstallUtils.findApplicationID(workspacePath, platform);
        if(applicationID == null)
        {
            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
            body.category = "stderr";
            body.output = "Failed to debug SWF. Error reading application <id> in application descriptor for platform \"" + platform + "\".\n";
            sendEvent(new OutputEvent(body));
            return false;
        }
        Path outputPath = DeviceInstallUtils.findOutputPath(platform, workspacePath);
        if(outputPath == null)
        {
            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
            body.category = "stderr";
            body.output = "Failed to debug SWF. Error reading output path in asconfig.json for platform \"" + platform + "\".\n";
            sendEvent(new OutputEvent(body));
            return false;
        }
        DeviceCommandResult uninstallResult = DeviceInstallUtils.runUninstallCommand(platform, applicationID, workspacePath, adtPath);
        if(uninstallResult.error)
        {
            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
            body.category = "stderr";
            body.output = uninstallResult.message + "\n";
            sendEvent(new OutputEvent(body));
            return false;
        }
        sendOutputEvent("Installing Adobe AIR application...\n");
        DeviceCommandResult installResult = DeviceInstallUtils.runInstallCommand(platform, outputPath, workspacePath, adtPath);
        if(installResult.error)
        {
            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
            body.category = "stderr";
            body.output = installResult.message + "\n";
            sendEvent(new OutputEvent(body));
            return false;
        }
        if(connect)
        {
            sendOutputEvent("Forwarding port " + port + " over USB...\n");
            DeviceCommandResult forwardPortResult = DeviceInstallUtils.forwardPortCommand(platform, port, workspacePath, adbPath, idbPath);
            if(forwardPortResult.error)
            {
                OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                body.category = "stderr";
                body.output = forwardPortResult.message + "\n";
                sendEvent(new OutputEvent(body));
                return false;
            }
            forwardedPort = port;
            forwardedPortPlatform = platform;
        }
        if(platform.equals(PLATFORM_IOS))
        {
            //ADT can't launch an iOS application automatically
            sendOutputEvent("\033[0;95mDebugger ready to attach. You must launch your application manually on the iOS device.\u001B[0m\n");
            return true;
        }
        sendOutputEvent("Launching Adobe AIR application on device...\n");
        DeviceCommandResult launchResult = DeviceInstallUtils.runLaunchCommand(platform, applicationID, workspacePath, adtPath);
        if(launchResult.error)
        {
            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
            body.category = "stderr";
            body.output = launchResult.message + "\n";
            sendEvent(new OutputEvent(body));
            return false;
        }
        sendOutputEvent("\033[0;92mInstallation and launch completed successfully.\u001B[0m\n");
        return true;
    }

    private boolean attach(Response response, SWFAttachRequestArguments args)
    {
        boolean success = true;
        ThreadSafeSessionManager manager = ThreadSafeBootstrap.sessionManager();
        swfSession = null;
        swfRunProcess = null;
        try
        {
            manager.startListening();
            if (args.connect)
            {
                swfSession = (ThreadSafeSession) manager.connect(args.port, null);
            }
            else
            {
                swfSession = (ThreadSafeSession) manager.accept(null);
            }
        }
        catch (ConnectException e)
        {
            success = false;
        }
        catch (IOException e)
        {
            success = false;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        if(response.success)
        {
            try
            {
                swfSession.bind();
            }
            catch (VersionException e)
            {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(buffer));
                sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
            }
            try
            {
                manager.stopListening();
            }
            catch (IOException e)
            {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(buffer));
                sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
            }

            cancelRunner = false;
            sessionThread = new java.lang.Thread(new SessionRunner());
            sessionThread.start();
        }
        response.success = success;
        sendResponse(response);
        return success;
    }

    public void disconnect(Response response, Request.RequestArguments args)
    {
        cleanupForwardedPort();
        if (sessionThread != null)
        {
            cancelRunner = true;
            sessionThread = null;
        }
        if (swfSession != null)
        {
            swfSession.terminate();
            swfSession = null;
        }
        if (swfRunProcess != null)
        {
            swfRunProcess.destroy();
            swfRunProcess = null;
        }
        sendResponse(response);
    }

    private void cleanupForwardedPort()
    {
        if (forwardedPort == -1)
        {
            return;
        }
        try
        {
            Path workspacePath = Paths.get(System.getProperty(WORKSPACE_PROPERTY));
            DeviceInstallUtils.stopForwardPortCommand(forwardedPortPlatform, forwardedPort, workspacePath, adbPath, idbPath);
        }
        finally
        {
            forwardedPortPlatform = null;
            forwardedPort = -1;
        }
    }

    public void setBreakpoints(Response response, SetBreakpointsRequest.SetBreakpointsArguments arguments)
    {
        String path = arguments.source.path;
        List<Breakpoint> breakpoints = setBreakpoints(path, arguments.breakpoints);
        sendResponse(response, new SetBreakpointsResponseBody(breakpoints));
    }

    private List<Breakpoint> setBreakpoints(String path, SourceBreakpoint[] breakpoints)
    {
        //start by trying to find the file ID for this path
        Path pathAsPath = Paths.get(path);
        SourceFile foundSourceFile = null;
        boolean badExtension = false;
        try
        {
            SwfInfo[] swfs = swfSession.getSwfs();
            for (SwfInfo swf : swfs)
            {
                SourceFile[] sourceFiles = swf.getSourceList(swfSession);
                for (SourceFile sourceFile : sourceFiles)
                {
                    //we can't check if the String paths are equal due to
                    //file system case sensitivity.
                    if (pathAsPath.equals(Paths.get(sourceFile.getFullPath())))
                    {
                        if (path.endsWith(FILE_EXTENSION_AS)
                                || path.endsWith(FILE_EXTENSION_MXML)
                                || path.endsWith(FILE_EXTENSION_HX))
                        {
                            foundSourceFile = sourceFile;
                        }
                        else
                        {
                            badExtension = true;
                        }
                        break;
                    }
                }
                if (foundSourceFile != null)
                {
                    break;
                }
            }
        }
        catch (InProgressException e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        catch (NoResponseException e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        if (foundSourceFile == null && !badExtension)
        {
            //the file was not found, but it has a supported extension,
            //so we'll try to add it again later.
            //SWF is a streaming format, so not all bytecode is loaded
            //immediately.
            if(!pendingBreakpoints.containsKey(path))
            {
                pendingBreakpoints.put(path, new PendingBreakpoints(breakpoints));
            }
        }
        try
        {
            //clear all old breakpoints for this file because our new list
            //doesn't specify exactly which ones are cleared
            for (Location location : swfSession.getBreakpointList())
            {
                if (pathAsPath.equals(Paths.get(location.getFile().getFullPath())))
                {
                    swfSession.clearBreakpoint(location);
                }
            }
            savedLogLocations.remove(path);
        }
        catch (NoResponseException e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        catch (NotConnectedException e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        List<Breakpoint> result = new ArrayList<>();
        for (int i = 0, count = breakpoints.length; i < count; i++)
        {
            SourceBreakpoint sourceBreakpoint = breakpoints[i];
            int sourceLine = sourceBreakpoint.line;
            Breakpoint responseBreakpoint = new Breakpoint();
            responseBreakpoint.line = sourceLine;
            responseBreakpoint.id = nextBreakpointID;
            nextBreakpointID++;
            if (foundSourceFile == null)
            {
                //we couldn't find the file, so we can't verify this breakpoint
                responseBreakpoint.verified = false;
            }
            else
            {
                //we found the file, so let's try to add this breakpoint
                //it may not work, but at least we tried!
                responseBreakpoint.source = sourceFileToSource(foundSourceFile);
                try
                {
                    Location breakpointLocation = swfSession.setBreakpoint(foundSourceFile.getId(), sourceLine);
                    if (breakpointLocation != null)
                    {
                        //I don't know if the line could change, but might as well
                        //use the one returned by the location
                        responseBreakpoint.line = breakpointLocation.getLine();
                        responseBreakpoint.verified = true;
                        String logMessage = sourceBreakpoint.logMessage;
                        if(logMessage != null)
                        {
                            savedLogLocations.put(path, new LogLocation(breakpointLocation, logMessage));
                        }
                    }
                    else
                    {
                        //setBreakpoint() may return null if the breakpoint
                        //could not be set. that's fine. the user will simply
                        //see that the breakpoint is not verified.
                        responseBreakpoint.verified = false;
                    }
                }
                catch (NoResponseException e)
                {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    e.printStackTrace(new PrintStream(buffer));
                    sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
                    responseBreakpoint.verified = false;
                }
                catch (NotConnectedException e)
                {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    e.printStackTrace(new PrintStream(buffer));
                    sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
                    responseBreakpoint.verified = false;
                }
            }
            result.add(responseBreakpoint);
        }
        return result;
    }

    private void refreshPendingBreakpoints()
    {
        if (pendingBreakpoints.isEmpty())
        {
            return;
        }
        //if we weren't able to add some breakpoints earlier because we
        //we couldn't find the source file, try again!
        Iterator<String> iterator = pendingBreakpoints.keySet().iterator();
        while(iterator.hasNext())
        {
            String path = iterator.next();
            PendingBreakpoints pending = pendingBreakpoints.get(path);
            int idToRestore = nextBreakpointID;
            nextBreakpointID = pending.idStart;
            List<Breakpoint> breakpoints = setBreakpoints(path, pending.breakpoints);
            nextBreakpointID = idToRestore;
            boolean hasVerified = false;
            for (Breakpoint breakpoint : breakpoints)
            {
                //this breakpoint was unverified, but it may be verified
                //now, so let the editor know the updated status
                BreakpointEvent.BreakpointBody body = new BreakpointEvent.BreakpointBody();
                body.breakpoint = breakpoint;
                body.reason = BreakpointEvent.REASON_CHANGED;
                if(breakpoint.verified)
                {
                    //if any of the breakpoints are verified, that's good
                    //enough. we shouldn't keep trying the other unverified
                    //ones because they will probably always fail
                    hasVerified = true;
                }
                sendEvent(new BreakpointEvent(body));
            }
            if(hasVerified)
            {
                iterator.remove();
            }
        }
    }

    public void continueCommand(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.resume();
            stopWaitingForResume();
        }
        catch (Exception e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void next(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.stepOver();
            stopWaitingForResume();
        }
        catch (Exception e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void stepIn(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.stepInto();
            stopWaitingForResume();
        }
        catch (Exception e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void stepOut(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.stepOut();
            stopWaitingForResume();
        }
        catch (Exception e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void pause(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.suspend();
            stopWaitingForResume();
        }
        catch (Exception e)
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(buffer));
            sendOutputEvent("Exception in debugger: " + buffer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void stackTrace(Response response, StackTraceRequest.StackTraceArguments arguments)
    {
        List<StackFrame> stackFrames = new ArrayList<>();
        try
        {
            Frame[] swfFrames = swfSession.getFrames();
            for (int i = 0, count = swfFrames.length; i < count; i++)
            {
                Frame swfFrame = swfFrames[i];
                Location location = swfFrame.getLocation();
                SourceFile file = location.getFile();
                StackFrame stackFrame = new StackFrame();
                stackFrame.id = i;
                stackFrame.name = swfFrame.getCallSignature();
                if (file != null)
                {
                    Source source = sourceFileToSource(file);
                    stackFrame.source = source;
                    stackFrame.line = location.getLine();
                    //location doesn't include column
                    //use 1 as the default since that's required to show
                    //exception info (0 won't work)
                    stackFrame.column = 1;
                }
                stackFrames.add(stackFrame);
            }
        }
        catch (NotConnectedException e)
        {
            //ignore
        }
        sendResponse(response, new StackTraceResponseBody(stackFrames));
    }

    public void scopes(Response response, ScopesRequest.ScopesArguments arguments)
    {
        List<Scope> scopes = new ArrayList<>();
        int frameId = arguments.frameId;
        try
        {
            Frame[] swfFrames = swfSession.getFrames();
            if (frameId >= 0 && frameId < swfFrames.length)
            {
                if(previousFaultEvent != null)
                {
                    ExceptionFault fault = (ExceptionFault) previousFaultEvent;
                    Scope exceptionScope = new Scope();
                    exceptionScope.name = "Exception";
                    exceptionScope.variablesReference = fault.getThrownValue().getId();
                    scopes.add(exceptionScope);
                }

                Scope localScope = new Scope();
                localScope.name = "Locals";
                //this is a hacky way to store the frameId
                localScope.variablesReference = frameId * 10 + LOCAL_VARIABLES_REFERENCE;
                scopes.add(localScope);
            }
        }
        catch (PlayerDebugException e)
        {
            //ignore and return no scopes
        }

        sendResponse(response, new ScopesResponseBody(scopes));
    }

    public void variables(Response response, VariablesRequest.VariablesArguments arguments)
    {
        List<Variable> variables = new ArrayList<>();
        try
        {
            Value swfValue = null;
            long variablesReference = arguments.variablesReference;
            int frameId = -1;
            if (variablesReference < 1000)
            {
                frameId = (int) variablesReference / 10;
                variablesReference -= frameId * 10;
            }
            flash.tools.debugger.Variable[] members = null;
            if (variablesReference == LOCAL_VARIABLES_REFERENCE)
            {
                Frame[] swfFrames = swfSession.getFrames();
                if (frameId >= 0 && frameId < swfFrames.length)
                {
                    Frame swfFrame = swfFrames[frameId];
                    flash.tools.debugger.Variable[] args = swfFrame.getArguments(swfSession);
                    flash.tools.debugger.Variable[] locals = swfFrame.getLocals(swfSession);
                    flash.tools.debugger.Variable swfThis = swfFrame.getThis(swfSession);
                    int memberCount = locals.length + args.length;
                    int offset = 0;
                    if (swfThis != null)
                    {
                        offset = 1;
                    }
                    members = new flash.tools.debugger.Variable[memberCount + offset];
                    if (swfThis != null)
                    {
                        members[0] = swfThis;
                    }
                    System.arraycopy(args, 0, members, offset, args.length);
                    System.arraycopy(locals, 0, members, args.length + offset, locals.length);
                }
                else
                {
                    members = new flash.tools.debugger.Variable[0];
                }
            }
            else
            {
                swfValue = swfSession.getValue(arguments.variablesReference);
                members = swfValue.getMembers(swfSession);
            }
            boolean isThis = false;
            Value swfThisValue = swfSession.getValue(Value.THIS_ID);
            if (swfThisValue != null)
            {
                isThis = swfThisValue.getId() == variablesReference;
            }
            for (flash.tools.debugger.Variable member : members)
            {
                if(member.isAttributeSet(VariableAttribute.IS_STATIC))
                {
                    //we're showing non-static members only
                    continue;
                }
                Value memberValue = member.getValue();
                Variable variable = new Variable();
                variable.name = member.getName();
                variable.type = memberValue.getTypeName();
                if (variablesReference == LOCAL_VARIABLES_REFERENCE)
                {
                    variable.evaluateName = member.getName();
                }
                else if (isThis)
                {
                    variable.evaluateName = "this." + member.getName();
                }
                long id = memberValue.getId();
                if (id != Value.UNKNOWN_ID)
                {
                    variable.value = memberValue.getTypeName();
                    variable.variablesReference = memberValue.getId();
                }
                else
                {
                    if (memberValue.getType() == VariableType.STRING)
                    {
                        variable.value = "\"" + memberValue.getValueAsString() + "\"";
                    }
                    else
                    {
                        variable.value = memberValue.getValueAsString();
                    }
                }
                variables.add(variable);
            }
        }
        catch (PlayerDebugException e)
        {
            //ignore
        }
        sendResponse(response, new VariablesResponseBody(variables));
    }

    public void threads(Response response, Request.RequestArguments arguments)
    {
        List<Thread> threads = new ArrayList<>();
        threads.add(new Thread(Isolate.DEFAULT_ID, "Main SWF"));
        sendResponse(response, new ThreadsResponseBody(threads));
    }

    public void evaluate(Response response, EvaluateRequest.EvaluateArguments arguments)
    {
        EvaluateResponseBody body = new EvaluateResponseBody();
        Object evaluateResult = null;
        try
        {
            int frameId = arguments.frameId;
            Frame[] swfFrames = swfSession.getFrames();
            if (frameId >= 0 && frameId < swfFrames.length)
            {
                Frame swfFrame = swfFrames[frameId];
                
                ASTBuilder builder = new ASTBuilder(false);
                ValueExp result = builder.parse(new StringReader(arguments.expression));
                evaluateResult = result.evaluate(new SWFExpressionContext(swfSession, Isolate.DEFAULT_ID, swfFrame));
            }
        }
        catch (PlayerFaultException e) {}
        catch (NoSuchVariableException e) {}
        catch (IOException e) {}
        catch (ParseException e) {}
        catch (PlayerDebugException e) {}
        
        Value value = null;
        if(evaluateResult == null)
        {
            try
            {
                value = swfSession.getGlobal(arguments.expression);
            }
            catch (PlayerDebugException e) {}
        }
        else if(evaluateResult instanceof flash.tools.debugger.Variable)
        {
            flash.tools.debugger.Variable variable = (flash.tools.debugger.Variable) evaluateResult;
            value = variable.getValue();
        }
        else if(evaluateResult instanceof Value)
        {
            value = (Value) evaluateResult;
        }

        if(value != null)
        {
            long id = value.getId();
            if (id != Value.UNKNOWN_ID)
            {
                body.result = value.getTypeName();
                body.variablesReference = value.getId();
                body.type = value.getTypeName();
            }
            else
            {
                if (value.getType() == VariableType.STRING)
                {
                    body.result = "\"" + value.getValueAsString() + "\"";
                }
                else
                {
                    body.result = value.getValueAsString();
                }
                body.type = value.getTypeName();
            }
        }
        else if(evaluateResult != null)
        {
            if(evaluateResult instanceof String)
            {
                body.result = "\"" + evaluateResult + "\"";
            }
            else
            {
                body.result = evaluateResult.toString();
            }
        }

        // not sending the body at all will output "not available" as the value of evaluation.
        if(body.result == null)
        {
            sendResponse(response);
        }
        else
        {
            sendResponse(response, body);
        }
    }
    
    public void exceptionInfo(Response response, ExceptionInfoRequest.ExceptionInfoArguments arguments)
    {
        if(previousFaultEvent == null)
        {
            sendResponse(response);
            return;
        }

        String typeName = null;
        if(previousFaultEvent instanceof ExceptionFault)
        {
            ExceptionFault exceptionFault = (ExceptionFault) previousFaultEvent;
            Value thrownValue = exceptionFault.getThrownValue();
            typeName = thrownValue.getTypeName();
        }

        ExceptionDetails details = new ExceptionDetails();
        details.message = previousFaultEvent.information;
        details.stackTrace = previousFaultEvent.stackTrace();
        details.typeName = typeName;
        sendResponse(response, new ExceptionInfoResponseBody(
            null,
            ExceptionInfoResponseBody.EXCEPTION_BREAK_MODE_ALWAYS,
            previousFaultEvent.information,
            details));
    }

    private void stopWaitingForResume()
    {
        waitingForResume = false;
        previousFaultEvent = null;
    }

    protected String transformPath(String sourceFilePath)
    {
        int index = sourceFilePath.indexOf(SDK_PATH_SIGNATURE_UNIX);
        if (index == -1)
        {
            index = sourceFilePath.indexOf(SDK_PATH_SIGNATURE_WINDOWS);
            if (index == -1)
            {
                return sourceFilePath;
            }
        }
        if (flexHome == null)
        {
            return sourceFilePath;
        }
        Path transformedPath = flexHome.resolve(sourceFilePath.substring(index + 1));
        if(Files.exists(transformedPath))
        {
			//only transform the path if the transformed file exists
			//if it doesn't exist, the original path may be valid
            return transformedPath.toAbsolutePath().toString();
        }
        return sourceFilePath;
    }

    private Source sourceFileToSource(SourceFile sourceFile)
    {
        Source source = new Source();
        source.name = sourceFile.getName();
        source.path = transformPath(sourceFile.getFullPath());
        return source;
    }

    protected Gson createGson()
    {
        GsonBuilder builder = new GsonBuilder();
        return builder.registerTypeAdapter(Request.class, new DebugSession.RequestDeserializer())
                .registerTypeAdapter(LaunchRequest.LaunchRequestArguments.class, new SWFLaunchRequestDeserializer())
                .registerTypeAdapter(AttachRequest.AttachRequestArguments.class, new SWFAttachRequestDeserializer())
                .create();
    }

    public static class SWFLaunchRequestDeserializer implements JsonDeserializer<LaunchRequest.LaunchRequestArguments>
    {
        public LaunchRequest.LaunchRequestArguments deserialize(JsonElement je, Type type, JsonDeserializationContext jdc)
                throws JsonParseException
        {
            return gson.fromJson(je, SWFLaunchRequestArguments.class);
        }
    }
    
    public static class SWFAttachRequestDeserializer implements JsonDeserializer<AttachRequest.AttachRequestArguments>
    {
        public AttachRequest.AttachRequestArguments deserialize(JsonElement je, Type type, JsonDeserializationContext jdc)
                throws JsonParseException
        {
            return gson.fromJson(je, SWFAttachRequestArguments.class);
        }
    }
}
