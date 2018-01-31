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
package com.nextgenactionscript.vscode.debug.responses;

import java.util.List;

import com.nextgenactionscript.vscode.debug.protocol.Response;

public class SetBreakpointsResponseBody extends Response.ResponseBody
{
    /**
     * Information about the breakpoints. The array elements are in the same order as the elements of the 'breakpoints' (or the deprecated 'lines') in the SetBreakpointsArguments.
     */
    public Breakpoint[] breakpoints;

    public SetBreakpointsResponseBody()
    {
        this.breakpoints = new Breakpoint[0];
    }

    public SetBreakpointsResponseBody(List<Breakpoint> breakpoints)
    {
        this.breakpoints = breakpoints.toArray(new Breakpoint[breakpoints.size()]);
    }
}
