/*
Copyright 2016-2018 Bowler Hat LLC

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
package com.as3mxml.vscode.debug;

import java.lang.reflect.Type;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.as3mxml.vscode.debug.protocol.ProtocolServer;
import com.as3mxml.vscode.debug.protocol.Request;
import com.as3mxml.vscode.debug.protocol.Response;
import com.as3mxml.vscode.debug.requests.AttachRequest;
import com.as3mxml.vscode.debug.requests.EvaluateRequest;
import com.as3mxml.vscode.debug.requests.ExceptionInfoRequest;
import com.as3mxml.vscode.debug.requests.InitializeRequest;
import com.as3mxml.vscode.debug.requests.LaunchRequest;
import com.as3mxml.vscode.debug.requests.ScopesRequest;
import com.as3mxml.vscode.debug.requests.SetBreakpointsRequest;
import com.as3mxml.vscode.debug.requests.StackTraceRequest;
import com.as3mxml.vscode.debug.requests.VariablesRequest;
import com.as3mxml.vscode.debug.responses.ErrorResponseBody;
import com.as3mxml.vscode.debug.responses.Message;

public abstract class DebugSession extends ProtocolServer
{
    private boolean _debuggerLinesStartAt1;
    private boolean _clientLinesStartAt1 = true;

    public DebugSession(boolean debuggerLinesStartAt1)
    {
        _debuggerLinesStartAt1 = debuggerLinesStartAt1;
    }

    public void sendResponse(Response response)
    {
        sendResponse(response, null);
    }

    public void sendResponse(Response response, Response.ResponseBody body)
    {
        if (body != null)
        {
            response.body = body;
        }
        sendMessage(response);
    }

    public void sendErrorResponse(Response response, int id, String format)
    {
        sendErrorResponse(response, id, format, null, true, false);
    }

    public void sendErrorResponse(Response response, int id, String format, HashMap<String, Object> arguments)
    {
        sendErrorResponse(response, id, format, arguments, true, false);
    }

    public void sendErrorResponse(Response response, int id, String format, HashMap<String, Object> arguments, boolean user)
    {
        sendErrorResponse(response, id, format, arguments, user, false);
    }

    public void sendErrorResponse(Response response, int id, String format, HashMap<String, Object> arguments, boolean user, boolean telemetry)
    {
        Message msg = new Message(id, format, arguments, user, telemetry);
        String message = format;
        if(arguments != null)
        {
            for (String key : arguments.keySet())
            {
                message = message.replace("{" + key + "}", arguments.get(key).toString());
            }
        }
        response.setErrorBody(message, new ErrorResponseBody(msg));
        sendMessage(response);
    }

    protected void dispatchRequest(String command, Request.RequestArguments arguments, Response response)
    {
        if (arguments == null)
        {
            arguments = new Request.RequestArguments();
        }

        try
        {
            switch (command)
            {
                case "initialize":
                {
                    /*if (args.linesStartAt1 != null)
                    {
                        _clientLinesStartAt1 = (boolean) args.linesStartAt1;
                    }
                    String pathFormat = (String) args.pathFormat;
                    if (pathFormat != null)
                    {
                        switch (pathFormat)
                        {
                            case "uri":
                                _clientPathsAreURI = true;
                                break;
                            case "path":
                                _clientPathsAreURI = false;
                                break;
                            default:
                                Request.RequestArguments errorArgs = new HashMap<>();
                                errorArgs.put("_format", pathFormat);
                                sendErrorResponse(response, 1015, "initialize: bad value '{_format}' for pathFormat", errorArgs);
                            return;
                        }
                    }*/
                    initialize(response, (InitializeRequest.InitializeRequestArguments) arguments);
                    break;
                }
                case "launch":
                {
                    launch(response, (LaunchRequest.LaunchRequestArguments) arguments);
                    break;
                }
                case "attach":
                {
                    attach(response, (AttachRequest.AttachRequestArguments) arguments);
                    break;
                }
                case "disconnect":
                {
                    disconnect(response, arguments);
                    break;
                }
                case "next":
                {
                    next(response, arguments);
                    break;
                }
                case "continue":
                {
                    continueCommand(response, arguments);
                    break;
                }
                case "stepIn":
                {
                    stepIn(response, arguments);
                    break;
                }
                case "stepOut":
                {
                    stepOut(response, arguments);
                    break;
                }
                case "pause":
                {
                    pause(response, arguments);
                    break;
                }
                case "stackTrace":
                {
                    stackTrace(response, (StackTraceRequest.StackTraceArguments) arguments);
                    break;
                }
                case "scopes":
                {
                    scopes(response, (ScopesRequest.ScopesArguments) arguments);
                    break;
                }
                case "variables":
                {
                    variables(response, (VariablesRequest.VariablesArguments) arguments);
                    break;
                }
                case "source":
                {
                    source(response, arguments);
                    break;
                }
                case "threads":
                {
                    threads(response, arguments);
                    break;
                }
                case "setBreakpoints":
                {
                    setBreakpoints(response, (SetBreakpointsRequest.SetBreakpointsArguments) arguments);
                    break;
                }
                case "evaluate":
                {
                    evaluate(response, (EvaluateRequest.EvaluateArguments) arguments);
                    break;
                }
                case "exceptionInfo":
                {
                    exceptionInfo(response, (ExceptionInfoRequest.ExceptionInfoArguments) arguments);
                    break;
                }
                default:
                {
                    System.err.println("unknown request command: " + command);
                    HashMap<String, Object> errorArgs = new HashMap<>();
                    errorArgs.put("_request", command);
                    sendErrorResponse(response, 1014, "unrecognized request: {_request}", errorArgs);
                }
                break;
            }
        }
        catch (Exception e)
        {
            System.err.println("Exception during request command: " + command);
            e.printStackTrace(System.err);
            HashMap<String, Object> map = new HashMap<>();
            map.put("_request", command);
            map.put("_exception", e.getMessage());
            sendErrorResponse(response, 1104, "error while processing request '{_request}' (exception: {_exception})", map);
        }

        if (command.equals("disconnect"))
        {
            stop();
        }
    }

    public abstract void initialize(Response response, InitializeRequest.InitializeRequestArguments arguments);

    public abstract void launch(Response response, LaunchRequest.LaunchRequestArguments arguments);

    public abstract void attach(Response response, AttachRequest.AttachRequestArguments arguments);

    public abstract void disconnect(Response response, Request.RequestArguments arguments);

    public abstract void setBreakpoints(Response response, SetBreakpointsRequest.SetBreakpointsArguments arguments);

    public abstract void continueCommand(Response response, Request.RequestArguments arguments);

    public abstract void next(Response response, Request.RequestArguments arguments);

    public abstract void stepIn(Response response, Request.RequestArguments arguments);

    public abstract void stepOut(Response response, Request.RequestArguments arguments);

    public abstract void pause(Response response, Request.RequestArguments arguments);

    public abstract void stackTrace(Response response, StackTraceRequest.StackTraceArguments arguments);

    public abstract void scopes(Response response, ScopesRequest.ScopesArguments arguments);

    public abstract void variables(Response response, VariablesRequest.VariablesArguments arguments);

    public void source(Response response, Request.RequestArguments arguments)
    {
        sendErrorResponse(response, 1020, "Source not supported");
    }

    public abstract void threads(Response response, Request.RequestArguments arguments);

    public abstract void evaluate(Response response, EvaluateRequest.EvaluateArguments arguments);

    public abstract void exceptionInfo(Response response, ExceptionInfoRequest.ExceptionInfoArguments arguments);

    protected int convertDebuggerLineToClient(int line)
    {
        if (_debuggerLinesStartAt1)
        {
            return _clientLinesStartAt1 ? line : line - 1;
        }
        else
        {
            return _clientLinesStartAt1 ? line + 1 : line;
        }
    }

    protected int convertClientLineToDebugger(int line)
    {
        if (_debuggerLinesStartAt1)
        {
            return _clientLinesStartAt1 ? line : line + 1;
        }
        else
        {
            return _clientLinesStartAt1 ? line - 1 : line;
        }
    }

    /*protected String convertDebuggerPathToClient(String path)
    {
        if (_debuggerPathsAreURI)
        {
            if (_clientPathsAreURI)
            {
                return path;
            }
            else
            {
                Uri uri = new Uri(path);
                return uri.LocalPath;
            }
        }
        else
        {
            if (_clientPathsAreURI)
            {
                try
                {
                    var uri = new System.Uri(path);
                    return uri.AbsoluteUri;
                }
                catch {
                return null;
            }
            }
            else
            {
                return path;
            }
        }
    }*/

    /*protected String convertClientPathToDebugger(String clientPath)
    {
        if (clientPath == null)
        {
            return null;
        }

        if (_debuggerPathsAreURI)
        {
            if (_clientPathsAreURI)
            {
                return clientPath;
            }
            else
            {
                var uri = new System.Uri(clientPath);
                return uri.AbsoluteUri;
            }
        }
        else
        {
            if (_clientPathsAreURI)
            {
                if (Uri.IsWellFormedUriString(clientPath, UriKind.Absolute))
                {
                    Uri uri = new Uri(clientPath);
                    return uri.LocalPath;
                }
                System.err.println("path not well formed: '{0}'", clientPath);
                return null;
            }
            else
            {
                return clientPath;
            }
        }
    }*/

    protected Gson createGson()
    {
        GsonBuilder builder = new GsonBuilder();
        return builder.registerTypeAdapter(Request.class, new RequestDeserializer()).create();
    }

    public static class RequestDeserializer implements JsonDeserializer<Request>
    {
        public Request deserialize(JsonElement je, Type type, JsonDeserializationContext jdc)
                throws JsonParseException
        {
            JsonObject jo = je.getAsJsonObject();
            switch (jo.get("command").getAsString())
            {
                case InitializeRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, InitializeRequest.class);
                }
                case SetBreakpointsRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, SetBreakpointsRequest.class);
                }
                case LaunchRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, LaunchRequest.class);
                }
                case AttachRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, AttachRequest.class);
                }
                case StackTraceRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, StackTraceRequest.class);
                }
                case ScopesRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, ScopesRequest.class);
                }
                case VariablesRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, VariablesRequest.class);
                }
                case EvaluateRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, EvaluateRequest.class);
                }
                case ExceptionInfoRequest.REQUEST_COMMAND:
                {
                    return gson.fromJson(je, ExceptionInfoRequest.class);
                }
            }
            Gson newGson = new Gson();
            return newGson.fromJson(je, Request.class);
        }
    }
}