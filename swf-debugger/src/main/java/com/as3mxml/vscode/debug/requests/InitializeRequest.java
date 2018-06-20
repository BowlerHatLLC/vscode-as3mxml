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
package com.as3mxml.vscode.debug.requests;

import com.as3mxml.vscode.debug.protocol.Request;

public class InitializeRequest extends Request
{
    public static final String REQUEST_COMMAND = "initialize";

    public InitializeRequestArguments arguments;

    public static class InitializeRequestArguments extends RequestArguments
    {
        public String adapterID;
        public boolean linesStartAt1;
        public boolean columnsStartAt1;
        public String pathFormat;
        public boolean supportsVariableType;
        public boolean supportsVariablePaging;
        public boolean supportsRunInTerminalRequest;
    }
}
