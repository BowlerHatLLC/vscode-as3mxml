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
package com.nextgenactionscript.vscode.debug.events;

import com.nextgenactionscript.vscode.debug.protocol.Event;

public class ExitedEvent extends Event<ExitedEvent.ExitedBody>
{
    public static String EVENT_TYPE = "exited";

    public ExitedEvent(ExitedEvent.ExitedBody body)
    {
        super(EVENT_TYPE, body);
    }

    public class ExitedBody extends Event.EventBody
    {
        public int exitCode;
    }
}
