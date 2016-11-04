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

public class ActionScriptLanguageServer implements LanguageServer
{
    private Consumer<MessageParams> showMessageCallback = m ->
    {
    };

    private ActionScriptTextDocumentService textDocumentService;

    public ActionScriptLanguageServer()
    {
        //I'm not really sure why the compiler needs this, but it does
        System.setProperty("flexlib", findFlexLibDirectoryPath());
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params)
    {
        ActionScriptTextDocumentService service = textDocumentService;
        Path workspaceRoot = Paths.get(params.getRootPath()).toAbsolutePath().normalize();
        service.setWorkspaceRoot(workspaceRoot);

        InitializeResultImpl result = new InitializeResultImpl();
        ServerCapabilitiesImpl serverCapabilities = new ServerCapabilitiesImpl();

        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        CompletionOptionsImpl completionOptions = new CompletionOptionsImpl();
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":", " "));
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

    }

    @Override
    public void exit()
    {

    }

    @Override
    public void onTelemetryEvent(Consumer<Object> var1)
    {
        //not used at this time
    }

    @Override
    public WindowService getWindowService()
    {
        return new WindowService()
        {
            @Override
            public void onShowMessage(Consumer<MessageParams> callback)
            {
                showMessageCallback = callback;
                textDocumentService.showMessageCallback = callback;
            }

            @Override
            public void onShowMessageRequest(Consumer<ShowMessageRequestParams> callback)
            {
            }

            @Override
            public void onLogMessage(Consumer<MessageParams> callback)
            {

            }
        };
    }

    @Override
    public WorkspaceService getWorkspaceService()
    {
        return new WorkspaceService()
        {
            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params)
            {
                return textDocumentService.workspaceSymbol(params);
            }

            @Override
            public void didChangeConfiguraton(DidChangeConfigurationParams params)
            {

            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
            {
                textDocumentService.didChangeWatchedFiles(params);
            }
        };
    }

    @Override
    public TextDocumentService getTextDocumentService()
    {
        if (textDocumentService == null)
        {
            textDocumentService = new ActionScriptTextDocumentService();
        }
        return textDocumentService;
    }

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
