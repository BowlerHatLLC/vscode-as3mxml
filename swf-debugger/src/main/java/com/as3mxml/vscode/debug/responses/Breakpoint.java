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
package com.as3mxml.vscode.debug.responses;

import com.as3mxml.vscode.debug.requests.Source;

public class Breakpoint
{
    public Breakpoint()
    {
    }

    /**
     * An optional unique identifier for the breakpoint.
     */
    public Integer id = null;

    /**
     * If true breakpoint could be set (but not necessarily at the desired location).
     */
    public boolean verified;

    /**
     * An optional message about the state of the breakpoint. This is shown to the user and can be used to explain why a breakpoint could not be verified.
     */
    public String message = null;

    /**
     * The source where the breakpoint is located.
     */
    public Source source;

    /**
     * The start line of the actual range covered by the breakpoint.
     */
    public int line;

    /**
     * An optional start column of the actual range covered by the breakpoint.
     */
    public Integer column = null;

    /**
     * An optional end line of the actual range covered by the breakpoint.
     */
    public Integer endLine = null;

    /**
     * An optional end column of the actual range covered by the breakpoint. If no end line is given, then the end column is assumed to be in the start line
     */
    public Integer endColumn = null;
}
