/*
Copyright 2016-2018 Bowler Hat LLC

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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.nextgenactionscript.vscode.DidChangeWatchedFilesRegistrationOptions.FileSystemWatcher;
import com.nextgenactionscript.vscode.commands.ICommandConstants;
import com.nextgenactionscript.vscode.project.IProjectConfigStrategyFactory;
import com.nextgenactionscript.vscode.services.ActionScriptLanguageClient;

import org.apache.royale.compiler.tree.as.IASNode;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
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
    private static final int MISSING_FRAMEWORK_LIB = 200;
    private static final int INVALID_FRAMEWORK_LIB = 201;
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String FRAMEWORKS_RELATIVE_PATH_PARENT = "../frameworks";

    private ActionScriptWorkspaceService workspaceService;
    private ActionScriptTextDocumentService textDocumentService;
    private IProjectConfigStrategyFactory projectConfigStrategyFactory;
    private ActionScriptLanguageClient languageClient;

    public ActionScriptLanguageServer(IProjectConfigStrategyFactory factory)
    {
        this.projectConfigStrategyFactory = factory;

        //the royalelib system property may be configured in the command line
        //options, but if it isn't, use the framework included with Royale
        if (System.getProperty(PROPERTY_FRAMEWORK_LIB) == null)
        {
            String frameworksPath = findFrameworksPath();
            if (frameworksPath == null)
            {
                System.exit(MISSING_FRAMEWORK_LIB);
            }
            File frameworkLibFile = new File(frameworksPath);
            if (!frameworkLibFile.exists() || !frameworkLibFile.isDirectory())
            {
                System.exit(INVALID_FRAMEWORK_LIB);
            }
            System.setProperty(PROPERTY_FRAMEWORK_LIB, frameworksPath);
        }
    }

    /**
     * Tells Visual Studio Code about the language server's capabilities.
     */
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params)
    {
        textDocumentService.setClientCapabilities(params.getCapabilities());
        textDocumentService.setLanguageClient(languageClient);
        textDocumentService.setProjectConfigStrategyFactory(projectConfigStrategyFactory);
        for(WorkspaceFolder folder : params.getWorkspaceFolders())
        {
            textDocumentService.addWorkspaceFolder(folder);
        }

        InitializeResult result = new InitializeResult();

        ServerCapabilities serverCapabilities = new ServerCapabilities();
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        serverCapabilities.setCodeActionProvider(true);

        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":", " ", "<"));
        serverCapabilities.setCompletionProvider(completionOptions);

        serverCapabilities.setDefinitionProvider(true);
        serverCapabilities.setTypeDefinitionProvider(true);
        serverCapabilities.setImplementationProvider(true);
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
        
        ExecuteCommandOptions executeCommandOptions = new ExecuteCommandOptions();
        executeCommandOptions.setCommands(Arrays.asList(
            ICommandConstants.ADD_IMPORT,
            ICommandConstants.ADD_MXML_NAMESPACE,
            ICommandConstants.ORGANIZE_IMPORTS_IN_URI,
            ICommandConstants.ORGANIZE_IMPORTS_IN_DIRECTORY,
            ICommandConstants.GENERATE_GETTER,
            ICommandConstants.GENERATE_SETTER,
            ICommandConstants.GENERATE_GETTER_AND_SETTER,
            ICommandConstants.GENERATE_LOCAL_VARIABLE,
            ICommandConstants.GENERATE_FIELD_VARIABLE,
            ICommandConstants.GENERATE_METHOD,
            ICommandConstants.QUICK_COMPILE
        ));
        serverCapabilities.setExecuteCommandProvider(executeCommandOptions);

        result.setCapabilities(serverCapabilities);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params)
    {
        List<FileSystemWatcher> watchers = new ArrayList<>();
        //ideally, we'd only check .as, .mxml, asconfig.json, and directories
        //but there's no way to target directories without *
        watchers.add(new FileSystemWatcher("**/*"));

        String id = "vscode-nextgenas-" + Math.random();
        DidChangeWatchedFilesRegistrationOptions options = new DidChangeWatchedFilesRegistrationOptions(watchers);
        Registration registration = new Registration(id, "workspace/didChangeWatchedFiles", options);
        List<Registration> registrations = new ArrayList<>();
        registrations.add(registration);

        RegistrationParams registrationParams = new RegistrationParams(registrations);
        languageClient.registerCapability(registrationParams);
    }

    @Override
    public CompletableFuture<Object> shutdown()
    {
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit()
    {
        System.exit(0);
    }

    /**
     * Requests from Visual Studio Code that are at the workspace level.
     */
    @Override
    public WorkspaceService getWorkspaceService()
    {
        if (workspaceService == null)
        {
            workspaceService = new ActionScriptWorkspaceService();
            workspaceService.textDocumentService = textDocumentService;
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
            if (workspaceService != null)
            {
                workspaceService.textDocumentService = textDocumentService;
            }
        }
        return textDocumentService;
    }

    public void connect(ActionScriptLanguageClient client)
    {
        languageClient = client;
        if (textDocumentService != null)
        {
            textDocumentService.setLanguageClient(languageClient);
        }
    }

    /**
     * Passes in a set of functions to communicate with VSCode.
     */
    @Override
    public void connect(LanguageClient client)
    {
        connect((ActionScriptLanguageClient) client);
    }

    /**
     * Using a Java class from the Apache Royale compiler, we can check where
     * its JAR file is located on the file system, and then we can find the
     * frameworks directory.
     */
    private String findFrameworksPath()
    {
        try
        {
            URI uri = IASNode.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            String path = Paths.get(uri.resolve(FRAMEWORKS_RELATIVE_PATH_PARENT)).normalize().toString();
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
