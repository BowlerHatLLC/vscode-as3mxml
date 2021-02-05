/*
Copyright 2016-2021 Bowler Hat LLC

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
package com.as3mxml.vscode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;

import com.as3mxml.vscode.project.ASConfigProjectConfigStrategy;
import com.as3mxml.vscode.project.IProjectConfigStrategy;
import com.as3mxml.vscode.project.IProjectConfigStrategyFactory;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;

import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;

/**
 * Contains the entry point for the JAR.
 */
public class Main {
    private static final int SERVER_CONNECT_ERROR = 101;
    private static final String SYSTEM_PROPERTY_PORT = "as3mxml.server.port";
    private static final String SOCKET_HOST = "localhost";

    /**
     * The main entry point when the JAR is run. Opens a socket to communicate
     * with Visual Studio Code using the port specified with the
     * -Das3mxml.server.port command line option. Then, instantiates the
     * ActionScriptLanguageServer, and passes it to the LSP4J library,
     * which handles all of the language server protocol communication.
     * LSP4J calls methods on ActionScriptLanguageServer as requests come in
     * from the text editor.
     */
    public static void main(String[] args) {
        String port = System.getProperty(SYSTEM_PROPERTY_PORT);
        Socket socket = null;
        try {
            InputStream inputStream = System.in;
            OutputStream outputStream = System.out;
            if (port != null) {
                socket = new Socket(SOCKET_HOST, Integer.parseInt(port));
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            }
            ASConfigProjectConfigStrategyFactory configFactory = new ASConfigProjectConfigStrategyFactory();
            ActionScriptLanguageServer server = new ActionScriptLanguageServer(configFactory);

            //to enable LSP inspector output on System.err, change to true
            boolean lspInspectorTrace = false;
            Launcher<ActionScriptLanguageClient> launcher = null;
            if (lspInspectorTrace) {
                launcher = Launcher.createLauncher(server, ActionScriptLanguageClient.class, inputStream, outputStream,
                        true, new PrintWriter(System.err));
            } else {
                launcher = Launcher.createLauncher(server, ActionScriptLanguageClient.class, inputStream, outputStream);
            }

            server.connect(launcher.getRemoteProxy());
            launcher.startListening();
        } catch (Exception e) {
            System.err.println("ActionScript & MXML language server failed to connect.");
            System.err.println(
                    "Visit the following URL to file an issue, and please include this log: https://github.com/BowlerHatLLC/vscode-as3mxml/issues");
            e.printStackTrace(System.err);
            System.exit(SERVER_CONNECT_ERROR);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {

                }
            }
        }
    }

    private static class ASConfigProjectConfigStrategyFactory implements IProjectConfigStrategyFactory {
        public IProjectConfigStrategy create(Path projectPath, WorkspaceFolder workspaceFolder) {
            return new ASConfigProjectConfigStrategy(projectPath, workspaceFolder);
        }
    }
}
