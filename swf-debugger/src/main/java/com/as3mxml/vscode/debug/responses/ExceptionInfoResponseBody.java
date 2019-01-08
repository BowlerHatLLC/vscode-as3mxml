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

import com.as3mxml.vscode.debug.protocol.Response;

public class ExceptionInfoResponseBody extends Response.ResponseBody
{
	public static final String EXCEPTION_BREAK_MODE_NEVER = "never";
	public static final String EXCEPTION_BREAK_MODE_ALWAYS = "always";
	public static final String EXCEPTION_BREAK_MODE_UNHANDLED = "unhandled";
	public static final String EXCEPTION_BREAK_MODE_USER_UNHANDLED = "userUnhandled";

    /**
     * ID of the exception that was thrown.
     */
	public String exceptionId;
	
    /**
     * Mode that caused the exception notification to be raised.
     */
	public String breakMode;
	
    /**
     * Descriptive text for the exception provided by the debug adapter.
     */
	public String description = null;
	
    /**
     * Detailed information about the exception.
     */
    public ExceptionDetails details = null;

    public ExceptionInfoResponseBody(String exceptionId, String breakMode)
    {
		this(exceptionId, breakMode, null, null);
    }

    public ExceptionInfoResponseBody(String exceptionId, String breakMode, String description, ExceptionDetails details)
    {
		this.exceptionId = exceptionId;
		this.breakMode = breakMode;
		this.description = description;
		this.details = details;
    }
}