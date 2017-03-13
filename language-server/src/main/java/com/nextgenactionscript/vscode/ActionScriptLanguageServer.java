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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.flex.compiler.tree.as.IASNode;

import com.nextgenactionscript.vscode.project.ASConfigProjectConfigStrategy;
import com.nextgenactionscript.vscode.utils.LanguageServerUtils;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Tells Visual Studio Code about the language server's capabilities, and sets
 * up the language server's services.
 */
public class ActionScriptLanguageServer implements LanguageServer, LanguageClientAware
{
    private static final int MISSING_FLEXLIB = 200;
    private static final int INVALID_FLEXLIB = 201;
    private static final String FLEXLIB = "flexlib";
    private static final String FRAMEWORKS_RELATIVE_PATH = "../frameworks";
    private static final String ASCONFIG_JSON = "asconfig.json";

    private WorkspaceService workspaceService;
    private ActionScriptTextDocumentService textDocumentService;
    private ASConfigProjectConfigStrategy projectConfigStrategy;
    private LanguageClient languageClient;

    public ActionScriptLanguageServer()
    {
        projectConfigStrategy = new ASConfigProjectConfigStrategy();
        //the flexlib system property may be configured in the command line
        //options, but if it isn't, use the framework included with FlexJS
        if (System.getProperty(FLEXLIB) == null)
        {
            String flexLib = findFlexLibDirectoryPath();
            if (flexLib == null)
            {
                System.exit(MISSING_FLEXLIB);
            }
            File flexLibFile = new File(flexLib);
            if (!flexLibFile.exists() || !flexLibFile.isDirectory())
            {
                System.exit(INVALID_FLEXLIB);
            }
            System.setProperty(FLEXLIB, flexLib);
        }
    }

    /**
     * Tells Visual Studio Code about the language server's capabilities.
     */
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params)
    {
        Path workspaceRoot = Paths.get(params.getRootPath()).toAbsolutePath().normalize();
        projectConfigStrategy.setASConfigPath(workspaceRoot.resolve(ASCONFIG_JSON));
        textDocumentService.setWorkspaceRoot(workspaceRoot);

        InitializeResult result = new InitializeResult();

        ServerCapabilities serverCapabilities = new ServerCapabilities();
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        serverCapabilities.setCodeActionProvider(true);

        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":", " ", "<"));
        serverCapabilities.setCompletionProvider(completionOptions);

        serverCapabilities.setDefinitionProvider(true);
        serverCapabilities.setDocumentSymbolProvider(true);
        serverCapabilities.setDocumentHighlightProvider(false);
        serverCapabilities.setDocumentRangeFormattingProvider(false);
        serverCapabilities.setHoverProvider(true);
        serverCapabilities.setReferencesProvider(true);
        serverCapabilities.setRenameProvider(true);

        SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions();
        signatureHelpOptions.setTriggerCharacters(Arrays.asList("(", ","));
        serverCapabilities.setSignatureHelpProvider(signatureHelpOptions);

        serverCapabilities.setWorkspaceSymbolProvider(true);

        result.setCapabilities(serverCapabilities);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown()
    {
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit()
    {
        //not used at this time
    }

    /**
     * Requests from Visual Studio Code that are at the workspace level.
     */
    @Override
    public WorkspaceService getWorkspaceService()
    {
        if (workspaceService == null)
        {
            workspaceService = new WorkspaceService()
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
                public void didChangeConfiguration(DidChangeConfigurationParams params)
                {
                    //inside the extension's entry point, this is handled already
                    //it actually restarts the language server because the language
                    //server may need to be loaded with a different version of the
                    //Apache FlexJS SDK
                }

                @Override
                public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
                {
                    for (FileEvent event : params.getChanges())
                    {
                        Path path = LanguageServerUtils.getPathFromLanguageServerURI(event.getUri());
                        if (path == null)
                        {
                            continue;
                        }
                        File file = path.toFile();
                        String fileName = file.getName();
                        if (fileName.equals(ASCONFIG_JSON))
                        {
                            //compiler settings may have changed, which means we should
                            //start fresh
                            projectConfigStrategy.setChanged(true);
                        }
                    }
                    //delegate to the ActionScriptTextDocumentService, since that's
                    //where the compiler is running, and the compiler may need to
                    //know about file changes
                    textDocumentService.didChangeWatchedFiles(params);
                }
            };
        }
        return workspaceService;
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
            textDocumentService.setLanguageClient(languageClient);
            textDocumentService.setProjectConfigStrategy(projectConfigStrategy);
        }
        return textDocumentService;
    }

    /**
     * Passes in a set of functions to communicate with VSCode.
     */
    @Override
    public void connect(LanguageClient client)
    {
        languageClient = client;
        if (textDocumentService != null)
        {
            textDocumentService.setLanguageClient(languageClient);
        }
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
            String path = Paths.get(uri.resolve(FRAMEWORKS_RELATIVE_PATH)).toString();
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
