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
package com.nextgenactionscript.vscode;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SWFDebug
{
    private static final int SERVER_CONNECT_ERROR = 100;
    private static final int DEFAULT_PORT = 4711;

    public static void main(String[] args)
    {
        int port = -1;
        boolean traceRequests = false;
        boolean traceResponses = false;
        for (String arg : args)
        {
            if (arg.equals("--server"))
            {
                port = DEFAULT_PORT;
            }
            else if (arg.startsWith("--server="))
            {
                try
                {
                    port = Integer.parseInt(arg.substring("--server=".length()));
                }
                catch (NumberFormatException e)
                {
                    port = -1;
                }
            }
            else if (arg.equals("--trace"))
            {
                port = DEFAULT_PORT;
            }
            else if (arg.equals("--trace-response"))
            {
                port = DEFAULT_PORT;
            }
        }
        SWFDebugSession debugSession = new SWFDebugSession();
        debugSession.TRACE = traceRequests;
        debugSession.TRACE_RESPONSE = traceResponses;
        if (port == -1)
        {
            debugSession.start(System.in, System.out);
        }
        else
        {
            ServerSocket serverSocket = null;
            try
            {
                serverSocket = new ServerSocket(port);
                Socket clientSocket = serverSocket.accept();
                debugSession.start(clientSocket.getInputStream(), clientSocket.getOutputStream());
                clientSocket.close();
                serverSocket.close();
            }
            catch (IOException e)
            {
                System.err.println("NextGen ActionScript debug adapter failed to connect.");
                System.err.println("Visit the following URL to file an issue, and please include this log: https://github.com/BowlerHatLLC/vscode-nextgenas/issues");
                e.printStackTrace(System.err);
                System.exit(SERVER_CONNECT_ERROR);
            }
        }
    }
}
