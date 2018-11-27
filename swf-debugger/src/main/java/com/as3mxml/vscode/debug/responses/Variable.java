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

public class Variable
{
    /**
     * The variable's name.
     */
    public String name;

    /**
     * The variable's value. This can be a multi-line text, e.g. for a function the body of a function.
     */
    public String value;

    /**
     * The type of the variable's value. Typically shown in the UI when hovering over the value.
     */
    public String type;

    /**
     * Optional evaluatable name of this variable which can be passed to the 'EvaluateRequest' to fetch the variable's value.
     */
    public String evaluateName;

    /**
     * If variablesReference is > 0, the variable is structured and its children can be retrieved by passing variablesReference to the VariablesRequest.
     */
    public Long variablesReference = null;
}
