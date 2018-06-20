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
package com.as3mxml.vscode.debug.responses;

import java.util.Map;

public class Message
{
    /**
     * Unique identifier for the message.
     */
    public int id;

    /**
     * A format string for the message. Embedded variables have the form '{name}'.\nIf variable name starts with an underscore character, the variable does not contain user data (PII) and can be safely used for telemetry purposes.
     */
    public String format;

    /**
     * An object used as a dictionary for looking up the variables in the format string.
     */
    public Map<String, Object> variables;

    /**
     * If true show user.
     */
    public boolean showUser;

    /**
     * If true send to telemetry.
     */
    public boolean sendTelemetry;

    /**
     * An optional url where additional information about this message can be found.
     */
    public String url;

    /**
     * An optional label that is presented to the user as the UI for opening the url.
     */
    public String urlLabel;

    public Message(int id, String format)
    {
        this(id, format, null, true, false);
    }

    public Message(int id, String format, Map<String, Object> variables)
    {
        this(id, format, variables, true, false);
    }

    public Message(int id, String format, Map<String, Object> variables, boolean user)
    {
        this(id, format, variables, user, false);
    }

    public Message(int id, String format, Map<String, Object> variables, boolean user, boolean telemetry)
    {
        this.id = id;
        this.format = format;
        this.variables = variables;
        this.showUser = user;
        this.sendTelemetry = telemetry;
    }
}
