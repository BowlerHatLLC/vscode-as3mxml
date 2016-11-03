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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.flex.compiler.tree.as.IASNode;

import io.typefox.lsapi.DidChangeConfigurationParams;
import io.typefox.lsapi.DidChangeWatchedFilesParams;
import io.typefox.lsapi.InitializeParams;
import io.typefox.lsapi.InitializeResult;
import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.ShowMessageRequestParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentSyncKind;
import io.typefox.lsapi.WorkspaceSymbolParams;
import io.typefox.lsapi.impl.CompletionOptionsImpl;
import io.typefox.lsapi.impl.InitializeResultImpl;
import io.typefox.lsapi.impl.MessageParamsImpl;
import io.typefox.lsapi.impl.ServerCapabilitiesImpl;
import io.typefox.lsapi.impl.SignatureHelpOptionsImpl;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;

/**
 * Tells Visual Studio Code about the language server's capabilities, and
 * determines if the specified version of the Apache FlexJS SDK is valid.
 */
public class ActionScriptLanguageServer implements LanguageServer
{
    /**
     * Displays a dismissable message bar across the top of Visual Studio Code
     * that can be an error, warning, or informational.
     */
    private Consumer<MessageParams> showMessageCallback = m ->
    {
    };

    private ActionScriptTextDocumentService textDocumentService;

    public ActionScriptLanguageServer()
    {
        //I'm not really sure why the compiler needs this, but it does
        System.setProperty("flexlib", findFlexLibDirectoryPath());
    }

    /**
     * Tells Visual Studio Code about the language server's capabilities.
     */
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params)
    {
        Path workspaceRoot = Paths.get(params.getRootPath()).toAbsolutePath().normalize();
        textDocumentService.setWorkspaceRoot(workspaceRoot);

        InitializeResultImpl result = new InitializeResultImpl();

        ServerCapabilitiesImpl serverCapabilities = new ServerCapabilitiesImpl();
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        CompletionOptionsImpl completionOptions = new CompletionOptionsImpl();
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":", " ", "<"));
        serverCapabilities.setCompletionProvider(completionOptions);

        serverCapabilities.setDefinitionProvider(true);
        serverCapabilities.setDocumentSymbolProvider(true);
        serverCapabilities.setWorkspaceSymbolProvider(true);
        serverCapabilities.setHoverProvider(true);
        serverCapabilities.setReferencesProvider(true);
        serverCapabilities.setCodeActionProvider(true);
        serverCapabilities.setRenameProvider(true);

        SignatureHelpOptionsImpl signatureHelpOptions = new SignatureHelpOptionsImpl();
        signatureHelpOptions.setTriggerCharacters(Arrays.asList("(", ","));
        serverCapabilities.setSignatureHelpProvider(signatureHelpOptions);

        result.setCapabilities(serverCapabilities);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void shutdown()
    {
        //not used at this time
    }

    @Override
    public void exit()
    {
        //not used at this time
    }

    @Override
    public void onTelemetryEvent(Consumer<Object> var1)
    {
        //not used at this time
    }

    /**
     * Provides a way to communicate with the user and Visual Studio Code.
     */
    @Override
    public WindowService getWindowService()
    {
        return new WindowService()
        {
            @Override
            public void onShowMessage(Consumer<MessageParams> callback)
            {
                showMessageCallback = callback;
                //pass the callback to the text document service, in case it
                //needs to show a message
                textDocumentService.showMessageCallback = callback;
            }

            @Override
            public void onShowMessageRequest(Consumer<ShowMessageRequestParams> callback)
            {
                //not used at this time
            }

            @Override
            public void onLogMessage(Consumer<MessageParams> callback)
            {
                //not used at this time
            }
        };
    }

    /**
     * Requests from Visual Studio Code that are at the workspace level.
     */
    @Override
    public WorkspaceService getWorkspaceService()
    {
        return new WorkspaceService()
        {
            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params)
            {
                //delegate to the ActionScriptTextDocumentService, since that's
                //where the compiler is running, and the compiler is needed to
                //find workspace symbols
                return textDocumentService.workspaceSymbol(params);
            }

            @Override
            public void didChangeConfiguraton(DidChangeConfigurationParams params)
            {
                //inside the extension's entry point, this is handled already
                //it actually restarts the language server because the language
                //server may need to be loaded with a different version of the
                //Apache FlexJS SDK
            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
            {
                //delegate to the ActionScriptTextDocumentService, since that's
                //where the compiler is running, and the compiler may need to
                //know about file changes
                textDocumentService.didChangeWatchedFiles(params);
            }
        };
    }

    /**
     * Requests from Visual Studio Code that are at the document level. Things
     * like API completion, function signature help, find references.
     */
    @Override
    public TextDocumentService getTextDocumentService()
    {
        if (textDocumentService == null)
        {
            textDocumentService = new ActionScriptTextDocumentService();
        }
        return textDocumentService;
    }

    /**
     * Using a Java class from the Apache FlexJS compiler, we can check where
     * its JAR file is located on the file system, and then we can find the
     * frameworks directory.
     */
    private String findFlexLibDirectoryPath()
    {
        try
        {
            URI uri = IASNode.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            String path = Paths.get(uri.resolve("../frameworks")).toString();
            File file = new File(path);
            if (file.exists() && file.isDirectory())
            {
                return path;
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
