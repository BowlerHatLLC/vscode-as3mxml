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
package com.as3mxml.vscode.debug.events;

import com.as3mxml.vscode.debug.protocol.Event;
import com.as3mxml.vscode.debug.responses.Breakpoint;

public class BreakpointEvent extends Event<BreakpointEvent.BreakpointBody>
{
    public static String EVENT_TYPE = "breakpoint";
    public static String REASON_CHANGED = "changed";
    public static String REASON_NEW = "new";

    public BreakpointEvent(BreakpointEvent.BreakpointBody body)
    {
        super(EVENT_TYPE, body);
    }

    public static class BreakpointBody extends Event.EventBody
    {
        public String reason;
        public Breakpoint breakpoint;
    }
}