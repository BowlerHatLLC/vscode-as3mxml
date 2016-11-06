/*
Copyright 2016 Bowler Hat LLC

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
package com.nextgenactionscript.vscode.asconfig;

/**
 * Possible values for the "type" field in an asconfig.json file.
 */
public enum ProjectType
{
    APP("app"),
    LIB("lib");

    private String token;

    private ProjectType(String value)
    {
        token = value;
    }

    public String getToken()
    {
        return token;
    }

    public static ProjectType fromToken(String token)
    {
        if(token.equals(LIB.getToken()))
        {
            return LIB;
        }
        if(token.equals(APP.getToken()))
        {
            return APP;
        }
        throw new IllegalArgumentException("Unknown token \"" + token + "\".");
    }
}