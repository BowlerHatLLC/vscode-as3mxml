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

public class StackFrame
{
    /**
     * An identifier for the stack frame. It must be unique across all threads. This id can be used to retrieve the scopes of the frame with the 'scopesRequest' or to restart the execution of a stackframe.
     */
    public int id;

    /**
     * The name of the stack frame, typically a method name.
     */
    public String name;

    /**
     * The optional source of the frame.
     */
    public Source source;

    /**
     * The line within the file of the frame. If source is null or doesn't exist, line is 0 and must be ignored.
     */
    public int line;

    /**
     * The column within the line. If source is null or doesn't exist, column is 0 and must be ignored.
     */
    public int column;
}
