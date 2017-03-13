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
package com.nextgenactionscript.vscode.debug.responses;

import com.nextgenactionscript.vscode.debug.protocol.Response;

public class Capabilities extends Response.ResponseBody
{
    /**
     * The debug adapter supports the configurationDoneRequest.
     */
    public boolean supportsConfigurationDoneRequest = false;

    /**
     * The debug adapter supports function breakpoints.
     */
    public boolean supportsFunctionBreakpoints = false;

    /**
     * The debug adapter supports conditional breakpoints.
     */
    public boolean supportsConditionalBreakpoints = false;

    /**
     * The debug adapter supports breakpoints that break execution after a specified number of hits.
     */
    public boolean supportsHitConditionalBreakpoints = false;

    /**
     * The debug adapter supports a (side effect free) evaluate request for data hovers.
     */
    public boolean supportsEvaluateForHovers = false;

    /**
     * The debug adapter supports stepping back via the stepBack and reverseContinue requests.
     */
    public boolean supportsStepBack = false;

    /**
     * The debug adapter supports setting a variable to a value.
     */
    public boolean supportsSetVariable = false;

    /**
     * The debug adapter supports restarting a frame.
     */
    public boolean supportsRestartFrame = false;

    /**
     * The debug adapter supports the gotoTargetsRequest.
     */
    public boolean supportsGotoTargetsRequest = false;

    /**
     * The debug adapter supports the stepInTargetsRequest.
     */
    public boolean supportsStepInTargetsRequest = false;

    /**
     * The debug adapter supports the completionsRequest.
     */
    public boolean supportsCompletionsRequest = false;

    /**
     * The debug adapter supports the modules request.
     */
    public boolean supportsModulesRequest = false;

    /**
     * The debug adapter supports the RestartRequest. In this case a client should not implement 'restart' by terminating and relaunching the adapter but by calling the RestartRequest.
     */
    public boolean supportsRestartRequest = false;

    /**
     * The debug adapter supports 'exceptionOptions' on the setExceptionBreakpoints request.
     */
    public boolean supportsExceptionOptions = false;

    /**
     * The debug adapter supports a 'format' attribute on the stackTraceRequest, variablesRequest, and evaluateRequest.
     */
    public boolean supportsValueFormattingOptions = false;

    /**
     * The set of additional module information exposed by the debug adapter.
     */
    public Object[] additionalModuleColumns;

    /**
     * Checksum algorithms supported by the debug adapter.
     */
    public Object[] supportedChecksumAlgorithms;

    /**
     * Available filters or options for the setExceptionBreakpoints request.
     */
    public Object[] exceptionBreakpointFilters;
}
