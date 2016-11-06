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
package com.nextgenactionscript.vscode;

import java.net.Socket;

import io.typefox.lsapi.services.json.LanguageServerToJsonAdapter;

/**
 * Contains the entry point for the JAR.
 */
public class Main
{
    /**
     * The main entry point when the JAR is run. Opens a socket to communicate
     * with Visual Studio Code using the port specified with the
     * -Dnextgeas.vscode.port command line option. Then, instantiates the
     * ActionScriptLanguageServer, and passes it to an instance of the
     * LanguageServerToJsonAdapter class provided by the typefox/ls-api library,
     * which handles all of the language server protocol communication.
     * 
     * LanguageServerToJsonAdapter calls methods on ActionScriptLanguageServer
     * as requests come in from VSCode.
     */
    public static void main(String[] args)
    {
        String port = System.getProperty("nextgeas.vscode.port");
        if (port == null)
        {
            System.err.println("Error: System property nextgeas.vscode.port is required.");
            System.exit(1);
        }
        try
        {
            Socket socket = new Socket("localhost", Integer.parseInt(port));

            ActionScriptLanguageServer server = new ActionScriptLanguageServer();

            LanguageServerToJsonAdapter jsonServer = new LanguageServerToJsonAdapter(server);
            jsonServer.connect(socket.getInputStream(), socket.getOutputStream());
            jsonServer.getProtocol().addErrorListener((message, error) -> {
                System.err.println(message);
                if (error != null)
                {
                    error.printStackTrace();
                }
            });

            jsonServer.join();
        }
        catch (Throwable t)
        {
            System.err.println("Error: " + t.toString());
            System.exit(1);
        }
    }
}
