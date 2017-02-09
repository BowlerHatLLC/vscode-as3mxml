/*
Copyright 2016 Bowler Hat LLC

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

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.nextgenactionscript.vscode.debug.DebugSession;
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
import flash.tools.debugger.events.SwfLoadedEvent;
import flash.tools.debugger.events.TraceEvent;
import flash.tools.debugger.threadsafe.ThreadSafeBootstrap;
import flash.tools.debugger.threadsafe.ThreadSafeSession;
import flash.tools.debugger.threadsafe.ThreadSafeSessionManager;

public class SWFDebugSession extends DebugSession
{
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_EXE = ".exe";
    private static final String ADL_BASE_NAME = "bin/adl";
    private static final String FLEXLIB_PROPERTY = "flexlib";
    private static final String SDK_PATH_SIGNATURE = "/frameworks/projects/";
    private static final long LOCAL_VARIABLES_REFERENCE = 1;
    private ThreadSafeSession swfSession;
    private java.lang.Thread sessionThread;
    private boolean cancelRunner = false;
    private boolean waitingForResume = false;
    private Path flexlib;
    private Path flexHome;
    private Path adlPath;

    private class SessionRunner implements Runnable
    {
        private boolean swfLoaded = false;

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
                        if (event instanceof SwfLoadedEvent)
                        {
                            if (!swfLoaded)
                            {
                                swfLoaded = true;
                                sendEvent(new InitializedEvent());
                            }
                        }
                        else if (event instanceof TraceEvent)
                        {
                            TraceEvent traceEvent = (TraceEvent) event;
                            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                            body.output = traceEvent.information;
                            sendEvent(new OutputEvent(body));
                        }
                        else if (event instanceof FaultEvent)
                        {
                            FaultEvent faultEvent = (FaultEvent) event;
                            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                            body.output = faultEvent.information + "\n" + faultEvent.stackTrace();
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
                                swfSession.resume();
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
                String playerPath = swfArgs.program;
                try
                {
                    URI uri = new URI(playerPath);
                    if (uri.getScheme() == null)
                    {
                        playerPath = "file://" + playerPath;
                    }
                }
                catch (Exception e)
                {
                    //safe to ignore
                }
                Player player = manager.playerForUri(playerPath, null);
                AIRLaunchInfo launchInfo = null;
                if (player.getType() == Player.AIR && adlPath != null)
                {
                    launchInfo = new AIRLaunchInfo();
                    launchInfo.airDebugLauncher = adlPath.toFile();
                }
                swfSession = (ThreadSafeSession) manager.launch(swfArgs.program, launchInfo, true, null, null);
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
        List<Breakpoint> breakpoints = new ArrayList<>();
        for (int i = 0, count = arguments.breakpoints.length; i < count; i++)
        {
            SourceBreakpoint sourceBreakpoint = arguments.breakpoints[i];
            int sourceLine = sourceBreakpoint.line;
            Breakpoint responseBreakpoint = new Breakpoint();
            responseBreakpoint.line = sourceLine;
            int fileId = -1;
            try
            {
                SwfInfo[] swfs = swfSession.getSwfs();
                for (SwfInfo swf : swfs)
                {
                    SourceFile[] sourceFiles = swf.getSourceList(swfSession);
                    for (SourceFile sourceFile : sourceFiles)
                    {
                        if (sourceFile.getFullPath().equals(path) &&
                                (path.endsWith(FILE_EXTENSION_AS) || path.endsWith(FILE_EXTENSION_MXML)))
                        {
                            fileId = sourceFile.getId();
                            break;
                        }
                    }
                    if (fileId != -1)
                    {
                        break;
                    }
                }
                if (fileId == -1)
                {
                    //either the file was not found, or it has an unsupported
                    //extension
                    responseBreakpoint.verified = false;
                }
                else
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
                        //setBreakpoint() may return null if the breakpoint could
                        //not be set. that's fine. the user will see that the
                        //breakpoint is not verified, so it's fine.
                        responseBreakpoint.verified = false;
                    }
                }
            }
            catch (InProgressException e)
            {
                e.printStackTrace(System.err);
                responseBreakpoint.verified = false;
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
            breakpoints.add(responseBreakpoint);
        }
        sendResponse(response, new SetBreakpointsResponseBody(breakpoints));
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
            swfSession.stepContinue();
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
        System.err.println(transformedPath.toAbsolutePath().toString());
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
