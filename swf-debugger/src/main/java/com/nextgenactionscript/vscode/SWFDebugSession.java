/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.vscode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.nextgenactionscript.vscode.debug.DebugSession;
import com.nextgenactionscript.vscode.debug.events.BreakpointEvent;
import com.nextgenactionscript.vscode.debug.events.InitializedEvent;
import com.nextgenactionscript.vscode.debug.events.OutputEvent;
import com.nextgenactionscript.vscode.debug.events.StoppedEvent;
import com.nextgenactionscript.vscode.debug.events.TerminatedEvent;
import com.nextgenactionscript.vscode.debug.protocol.Request;
import com.nextgenactionscript.vscode.debug.protocol.Response;
import com.nextgenactionscript.vscode.debug.requests.InitializeRequest;
import com.nextgenactionscript.vscode.debug.requests.LaunchRequest;
import com.nextgenactionscript.vscode.debug.requests.ScopesRequest;
import com.nextgenactionscript.vscode.debug.requests.SetBreakpointsRequest;
import com.nextgenactionscript.vscode.debug.requests.Source;
import com.nextgenactionscript.vscode.debug.requests.SourceBreakpoint;
import com.nextgenactionscript.vscode.debug.requests.StackTraceRequest;
import com.nextgenactionscript.vscode.debug.requests.VariablesRequest;
import com.nextgenactionscript.vscode.debug.responses.Breakpoint;
import com.nextgenactionscript.vscode.debug.responses.Capabilities;
import com.nextgenactionscript.vscode.debug.responses.Scope;
import com.nextgenactionscript.vscode.debug.responses.ScopesResponseBody;
import com.nextgenactionscript.vscode.debug.responses.SetBreakpointsResponseBody;
import com.nextgenactionscript.vscode.debug.responses.StackFrame;
import com.nextgenactionscript.vscode.debug.responses.StackTraceResponseBody;
import com.nextgenactionscript.vscode.debug.responses.Thread;
import com.nextgenactionscript.vscode.debug.responses.ThreadsResponseBody;
import com.nextgenactionscript.vscode.debug.responses.Variable;
import com.nextgenactionscript.vscode.debug.responses.VariablesResponseBody;
import flash.tools.debugger.AIRLaunchInfo;
import flash.tools.debugger.CommandLineException;
import flash.tools.debugger.DefaultDebuggerCallbacks;
import flash.tools.debugger.Frame;
import flash.tools.debugger.InProgressException;
import flash.tools.debugger.Isolate;
import flash.tools.debugger.Location;
import flash.tools.debugger.NoResponseException;
import flash.tools.debugger.NotConnectedException;
import flash.tools.debugger.Player;
import flash.tools.debugger.PlayerDebugException;
import flash.tools.debugger.SourceFile;
import flash.tools.debugger.SuspendReason;
import flash.tools.debugger.SwfInfo;
import flash.tools.debugger.Value;
import flash.tools.debugger.VariableType;
import flash.tools.debugger.VersionException;
import flash.tools.debugger.events.DebugEvent;
import flash.tools.debugger.events.FaultEvent;
import flash.tools.debugger.events.TraceEvent;
import flash.tools.debugger.threadsafe.ThreadSafeBootstrap;
import flash.tools.debugger.threadsafe.ThreadSafeSession;
import flash.tools.debugger.threadsafe.ThreadSafeSessionManager;

public class SWFDebugSession extends DebugSession
{
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_EXE = ".exe";
    private static final String FILE_EXTENSION_XML = ".xml";
    private static final String ADL_BASE_NAME = "bin/adl";
    private static final String FLEXLIB_PROPERTY = "flexlib";
    private static final String WORKSPACE_PROPERTY = "workspace";
    private static final String SDK_PATH_SIGNATURE = "/frameworks/projects/";
    private static final long LOCAL_VARIABLES_REFERENCE = 1;
    private ThreadSafeSession swfSession;
    private java.lang.Thread sessionThread;
    private boolean cancelRunner = false;
    private boolean waitingForResume = false;
    private Path flexlib;
    private Path flexHome;
    private Path adlPath;
    private Map<String,SourceBreakpoint[]> pendingBreakpoints;

    private class SessionRunner implements Runnable
    {
        private boolean initialized = false;

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
                    while (swfSession.getEventCount() > 0)
                    {
                        DebugEvent event = swfSession.nextEvent();
                        if (event instanceof TraceEvent)
                        {
                            TraceEvent traceEvent = (TraceEvent) event;
                            String output = traceEvent.information;
                            if (output.charAt(output.length() - 1) != '\n')
                            {
                                output += '\n';
                            }
                            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
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
                            body.output = output;
                            body.category = OutputEvent.CATEGORY_STDERR;
                            sendEvent(new OutputEvent(body));
                        }
                    }
                    while (swfSession.isSuspended() && !waitingForResume)
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
                                body = new StoppedEvent.StoppedBody();
                                body.reason = StoppedEvent.REASON_BREAKPOINT;
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
                                break;
                            }
                            default:
                            {
                                body = new StoppedEvent.StoppedBody();
                                body.reason = StoppedEvent.REASON_UNKNOWN;
                                System.err.println("Unknown suspend reason: " + swfSession.suspendReason());
                            }
                        }
                        if (body != null)
                        {
                            waitingForResume = true;
                            body.threadId = Isolate.DEFAULT_ID;
                            sendEvent(new StoppedEvent(body));
                        }
                        break;
                    }
                }
                catch (NotConnectedException e)
                {
                    cancelRunner = true;
                    sendEvent(new TerminatedEvent());
                }
                catch (Exception e)
                {
                    e.printStackTrace(System.err);
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

    public SWFDebugSession()
    {
        super(false);
        pendingBreakpoints = new HashMap<>();
        String flexlibPath = System.getProperty(FLEXLIB_PROPERTY);
        if (flexlibPath != null)
        {
            flexlib = Paths.get(flexlibPath);
            flexHome = flexlib.getParent();
            String adlRelativePath = ADL_BASE_NAME;
            if (System.getProperty("os.name").toLowerCase().startsWith("windows"))
            {
                adlRelativePath += FILE_EXTENSION_EXE;
            }
            adlPath = flexHome.resolve(adlRelativePath);
        }
    }

    public void initialize(Response response, InitializeRequest.InitializeRequestArguments args)
    {
        Capabilities capabilities = new Capabilities();
        sendResponse(response, capabilities);
    }

    public void launch(Response response, LaunchRequest.LaunchRequestArguments args)
    {
        LaunchRequestArguments swfArgs = (LaunchRequestArguments) args;
        ThreadSafeSessionManager manager = ThreadSafeBootstrap.sessionManager();
        swfSession = null;
        try
        {
            manager.startListening();
            if (manager.supportsLaunch())
            {
                String program = swfArgs.program;
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
                Player player = null;
                CustomRuntimeLauncher launcher = null;
                if (swfArgs.runtimeExecutable != null)
                {
                    //if runtimeExecutable is specified, we'll launch that
                    launcher = new CustomRuntimeLauncher(swfArgs.runtimeExecutable, swfArgs.runtimeArgs);
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
                    player = manager.playerForUri(playerPath, null);
                    if (player == null
                            && !playerPath.startsWith("http:")
                            && !playerPath.startsWith("https:")
                            && playerPath.endsWith(".swf"))
                    {
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
                else
                {
                    AIRLaunchInfo launchInfo = null;
                    //check if the debugger automatically detected AIR
                    boolean isAIR = player != null && player.getType() == Player.AIR;
                    if (!isAIR && player == null && program.endsWith(FILE_EXTENSION_XML))
                    {
                        //otherwise, check if the program to launch is an AIR
                        //application descriptor
                        isAIR = true;
                    }
                    if (isAIR && adlPath != null)
                    {
                        launchInfo = new AIRLaunchInfo();
                        launchInfo.profile = swfArgs.profile;
                        launchInfo.screenSize = swfArgs.screensize;
                        launchInfo.dpi = swfArgs.screenDPI;
                        launchInfo.versionPlatform = swfArgs.versionPlatform;
                        launchInfo.airDebugLauncher = adlPath.toFile();
                        launchInfo.extDir = swfArgs.extdir;
                        launchInfo.applicationArgumentsArray = swfArgs.args;
                        if (launcher != null)
                        {
                            launcher.isAIR = true;
                        }
                    }
                    if (launcher != null)
                    {
                        swfSession = (ThreadSafeSession) manager.launch(program, launchInfo, true, null, null, launcher);
                    }
                    else
                    {
                        swfSession = (ThreadSafeSession) manager.launch(program, launchInfo, true, null, null);
                    }
                }
            }
        }
        catch (CommandLineException e)
        {
            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
            body.output = e.getMessage() + "\n" + e.getCommandOutput();
            body.category = OutputEvent.CATEGORY_STDERR;
            sendEvent(new OutputEvent(body));
            e.printStackTrace(System.err);
            sendErrorResponse(response, 10001, "Error launching SWF debug session. Process exited with code: " + e.getExitValue());
            return;
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
            sendErrorResponse(response, 10001, "Error launching SWF debug session.");
            return;
        }
        try
        {
            swfSession.bind();
        }
        catch (VersionException e)
        {
            e.printStackTrace(System.err);
        }
        try
        {
            manager.stopListening();
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
        }
        sendResponse(response);
        cancelRunner = false;
        sessionThread = new java.lang.Thread(new SessionRunner());
        sessionThread.start();
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

    public void attach(Response response, Request.RequestArguments args)
    {
        sendResponse(response);
    }

    public void disconnect(Response response, Request.RequestArguments args)
    {
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
        sendResponse(response);
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
        int fileId = -1;
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
                        if (path.endsWith(FILE_EXTENSION_AS) || path.endsWith(FILE_EXTENSION_MXML))
                        {
                            fileId = sourceFile.getId();
                        }
                        else
                        {
                            badExtension = true;
                        }
                        break;
                    }
                }
                if (fileId != -1)
                {
                    break;
                }
            }
        }
        catch (InProgressException e)
        {
            e.printStackTrace(System.err);
        }
        catch (NoResponseException e)
        {
            e.printStackTrace(System.err);
        }
        if (fileId != -1)
        {
            //if we found the fileId, make sure the breakpoints are no longer
            //pending so that we don't try to add them again
            pendingBreakpoints.remove(path);
        }
        else if (!badExtension)
        {
            //the file was not found, but it has a supported extension,
            //so we'll try to add it again later.
            //SWF is a streaming format, so not all bytecode is loaded
            //immediately.
            pendingBreakpoints.put(path, breakpoints);
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
        }
        catch (NoResponseException e)
        {
            e.printStackTrace(System.err);
        }
        catch (NotConnectedException e)
        {
            e.printStackTrace(System.err);
        }
        List<Breakpoint> result = new ArrayList<>();
        for (int i = 0, count = breakpoints.length; i < count; i++)
        {
            SourceBreakpoint sourceBreakpoint = breakpoints[i];
            int sourceLine = sourceBreakpoint.line;
            Breakpoint responseBreakpoint = new Breakpoint();
            responseBreakpoint.line = sourceLine;
            if (fileId == -1)
            {
                //we couldn't find the file, so we can't verify this breakpoint
                responseBreakpoint.verified = false;
            }
            else
            {
                //we found the file, so let's try to add this breakpoint
                //it may not work, but at least we tried!
                try
                {
                    Location breakpointLocation = swfSession.setBreakpoint(fileId, sourceLine);
                    if (breakpointLocation != null)
                    {
                        //I don't know if the line could change, but might as well
                        //use the one returned by the location
                        responseBreakpoint.line = breakpointLocation.getLine();
                        responseBreakpoint.verified = true;
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
                    e.printStackTrace(System.err);
                    responseBreakpoint.verified = false;
                }
                catch (NotConnectedException e)
                {
                    e.printStackTrace(System.err);
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
        for (String path : pendingBreakpoints.keySet())
        {
            SourceBreakpoint[] pending = pendingBreakpoints.get(path);
            List<Breakpoint> breakpoints = setBreakpoints(path, pending);
            for (Breakpoint breakpoint : breakpoints)
            {
                //this breakpoint was unverified, but it may be verified
                //now, so let the editor know the updated status
                BreakpointEvent.BreakpointBody body = new BreakpointEvent.BreakpointBody();
                body.breakpoint = breakpoint;
                body.reason = BreakpointEvent.REASON_CHANGED;
                sendEvent(new BreakpointEvent(body));
            }
        }
    }

    public void continueCommand(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.resume();
            waitingForResume = false;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.err);
        }
        sendResponse(response);
    }

    public void next(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.stepOver();
            waitingForResume = false;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.err);
        }
        sendResponse(response);
    }

    public void stepIn(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.stepInto();
            waitingForResume = false;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.err);
        }
        sendResponse(response);
    }

    public void stepOut(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.stepOut();
            waitingForResume = false;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.err);
        }
        sendResponse(response);
    }

    public void pause(Response response, Request.RequestArguments arguments)
    {
        try
        {
            swfSession.suspend();
            waitingForResume = false;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.err);
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
                    Source source = new Source();
                    source.name = file.getName();
                    source.path = transformPath(file.getFullPath());
                    stackFrame.source = source;
                    stackFrame.line = location.getLine();
                    stackFrame.column = 0;
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
            for (flash.tools.debugger.Variable member : members)
            {
                Value memberValue = member.getValue();
                Variable variable = new Variable();
                variable.name = member.getName();
                variable.type = memberValue.getTypeName();
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

    public void evaluate(Response response, Request.RequestArguments arguments)
    {
        sendResponse(response);
    }

    protected String transformPath(String sourceFilePath)
    {
        int index = sourceFilePath.indexOf(SDK_PATH_SIGNATURE);
        if (index == -1)
        {
            return sourceFilePath;
        }
        if (flexHome == null)
        {
            return sourceFilePath;
        }
        Path transformedPath = flexHome.resolve(sourceFilePath.substring(index + 1));
        return transformedPath.toAbsolutePath().toString();

    }

    protected Gson createGson()
    {
        GsonBuilder builder = new GsonBuilder();
        return builder.registerTypeAdapter(Request.class, new DebugSession.RequestDeserializer())
                .registerTypeAdapter(LaunchRequest.LaunchRequestArguments.class, new LaunchRequestDeserializer())
                .create();
    }

    public static class LaunchRequestDeserializer implements JsonDeserializer<LaunchRequest.LaunchRequestArguments>
    {
        public LaunchRequest.LaunchRequestArguments deserialize(JsonElement je, Type type, JsonDeserializationContext jdc)
                throws JsonParseException
        {
            return gson.fromJson(je, LaunchRequestArguments.class);
        }
    }
}
