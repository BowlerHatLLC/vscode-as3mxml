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

public class Main
{
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
