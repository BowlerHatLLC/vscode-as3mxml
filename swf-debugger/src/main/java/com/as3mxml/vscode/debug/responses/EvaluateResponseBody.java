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

import com.as3mxml.vscode.debug.protocol.Response;

public class EvaluateResponseBody extends Response.ResponseBody
{
    /** The result of the evaluate request. */
    public String result;
    
    /** The type of the new value. Typically shown in the UI when hovering over the value. */
    public String type;

    /** If variablesReference is > 0, the new value is structured and its children can be retrieved by passing variablesReference to the VariablesRequest. */
    public Long variablesReference = null;

    /** The number of named child variables.
        The client can use this optional information to present the variables in a paged UI and fetch them in chunks.
    */
    public Integer namedVariables;

    /** The number of indexed child variables.
        The client can use this optional information to present the variables in a paged UI and fetch them in chunks.
    */
    public Integer indexedVariables;
}