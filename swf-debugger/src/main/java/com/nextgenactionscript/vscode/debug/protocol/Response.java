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
package com.nextgenactionscript.vscode.debug.protocol;

public class Response extends ProtocolMessage
{
    public static String PROTOCOL_MESSAGE_TYPE = "response";
    public int request_seq;
    public boolean success;
    public String command;
    public String message;
    public ResponseBody body;

    public Response(Request req)
    {
        super(PROTOCOL_MESSAGE_TYPE);
        success = true;
        request_seq = req.seq;
        command = req.command;
    }

    public void setBody(ResponseBody body)
    {
        this.body = body;
        this.success = true;
    }

    public void setErrorBody(String message)
    {
        setErrorBody(message, null);
    }

    public void setErrorBody(String message, ResponseBody body)
    {
        this.body = body;
        this.message = message;
        this.success = false;
    }

    public static abstract class ResponseBody
    {

    }
}