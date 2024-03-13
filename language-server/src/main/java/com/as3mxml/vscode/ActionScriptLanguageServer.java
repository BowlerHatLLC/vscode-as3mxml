/*
Copyright 2016-2024 Bowler Hat LLC

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

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.royale.compiler.tree.as.IASNode;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbolOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.as3mxml.vscode.commands.ICommandConstants;
import com.as3mxml.vscode.project.IProjectConfigStrategyFactory;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

/**
 * Tells Visual Studio Code about the language server's capabilities, and sets
 * up the language server's services.
 */
public class ActionScriptLanguageServer implements LanguageServer, LanguageClientAware {
    private static final int MISSING_FRAMEWORK_LIB = 200;
    private static final int INVALID_FRAMEWORK_LIB = 201;
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String FRAMEWORKS_RELATIVE_PATH_PARENT = "../frameworks";

    protected ActionScriptServices actionScriptServices;
    protected ActionScriptLanguageClient languageClient;

    public ActionScriptLanguageServer(IProjectConfigStrategyFactory factory) {
        // the royalelib system property may be configured in the command line
        // options, but if it isn't, use the framework included with Royale
        if (System.getProperty(PROPERTY_FRAMEWORK_LIB) == null) {
            String frameworksPath = findFrameworksPath();
            if (frameworksPath == null) {
                System.exit(MISSING_FRAMEWORK_LIB);
            }
            File frameworkLibFile = new File(frameworksPath);
            if (!frameworkLibFile.exists() || !frameworkLibFile.isDirectory()) {
                System.exit(INVALID_FRAMEWORK_LIB);
            }
            System.setProperty(PROPERTY_FRAMEWORK_LIB, frameworksPath);
        }

        actionScriptServices = new ActionScriptServices(factory);
    }

    /**
     * Tells Visual Studio Code about the language server's capabilities.
     * 
     * Optional initialization options:
     * 
     * - supportsSimpleSnippets: The client offers partial support for snippets,
     * such as $0
     */
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        actionScriptServices.setClientCapabilities(params.getCapabilities());
        actionScriptServices.setLanguageClient(languageClient);
        boolean supportsSimpleSnippets = false;
        String preferredRoyaleTarget = null;
        boolean notifyActiveProject = false;
        if (params.getInitializationOptions() != null) {
            JsonObject initializationOptions = (JsonObject) params.getInitializationOptions();
            if (initializationOptions.has("supportsSimpleSnippets")) {
                supportsSimpleSnippets = initializationOptions.get("supportsSimpleSnippets").getAsBoolean();
            }
            if (initializationOptions.has("preferredRoyaleTarget")) {
                preferredRoyaleTarget = initializationOptions.get("preferredRoyaleTarget").getAsString();
            }
            if (initializationOptions.has("notifyActiveProject")) {
                notifyActiveProject = initializationOptions.get("notifyActiveProject").getAsBoolean();
            }
        }
        actionScriptServices.setClientSupportsSimpleSnippets(supportsSimpleSnippets);
        actionScriptServices.setPreferredRoyaleTarget(preferredRoyaleTarget);
        actionScriptServices.setNotifyActiveProject(notifyActiveProject);
        // setting everything above should happen before adding workspace folders
        List<WorkspaceFolder> folders = params.getWorkspaceFolders();
        if (folders != null) {
            for (WorkspaceFolder folder : params.getWorkspaceFolders()) {
                actionScriptServices.addWorkspaceFolder(folder);
            }
        } else if (params.getRootUri() != null) {
            // some clients don't support workspace folders, but if they pass in
            // a root URI, we can treat it like a workspace folder
            WorkspaceFolder folder = new WorkspaceFolder();
            folder.setUri(params.getRootUri());
            actionScriptServices.addWorkspaceFolder(folder);
        }

        InitializeResult result = new InitializeResult();

        ServerCapabilities serverCapabilities = new ServerCapabilities();
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        serverCapabilities.setCodeActionProvider(new CodeActionOptions(Lists.newArrayList(CodeActionKind.QuickFix,
                CodeActionKind.Refactor, CodeActionKind.RefactorRewrite, CodeActionKind.SourceOrganizeImports)));

        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(Arrays.asList(".", ":", " ", "<"));
        serverCapabilities.setCompletionProvider(completionOptions);

        serverCapabilities.setDefinitionProvider(true);
        serverCapabilities.setTypeDefinitionProvider(true);
        serverCapabilities.setImplementationProvider(true);
        serverCapabilities.setDocumentSymbolProvider(true);
        serverCapabilities.setDocumentHighlightProvider(false);
        serverCapabilities.setDocumentFormattingProvider(true);
        serverCapabilities.setDocumentRangeFormattingProvider(false);
        serverCapabilities.setHoverProvider(true);
        serverCapabilities.setReferencesProvider(true);
        serverCapabilities.setRenameProvider(true);

        SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions();
        signatureHelpOptions.setTriggerCharacters(Arrays.asList("(", ","));
        serverCapabilities.setSignatureHelpProvider(signatureHelpOptions);

        WorkspaceSymbolOptions workspaceSymbolOptions = new WorkspaceSymbolOptions();
        workspaceSymbolOptions.setResolveProvider(true);
        serverCapabilities.setWorkspaceSymbolProvider(workspaceSymbolOptions);

        WorkspaceServerCapabilities workspaceCapabilities = new WorkspaceServerCapabilities();
        WorkspaceFoldersOptions workspaceFoldersOptions = new WorkspaceFoldersOptions();
        workspaceFoldersOptions.setSupported(true);
        workspaceFoldersOptions.setChangeNotifications(true);
        workspaceCapabilities.setWorkspaceFolders(workspaceFoldersOptions);
        serverCapabilities.setWorkspace(workspaceCapabilities);

        ExecuteCommandOptions executeCommandOptions = new ExecuteCommandOptions();
        executeCommandOptions
                .setCommands(Arrays.asList(ICommandConstants.ADD_IMPORT, ICommandConstants.ADD_MXML_NAMESPACE,
                        ICommandConstants.ORGANIZE_IMPORTS_IN_URI, ICommandConstants.ORGANIZE_IMPORTS_IN_DIRECTORY,
                        ICommandConstants.ADD_MISSING_IMPORTS_IN_URI, ICommandConstants.REMOVE_UNUSED_IMPORTS_IN_URI,
                        ICommandConstants.SORT_IMPORTS_IN_URI, ICommandConstants.QUICK_COMPILE,
                        ICommandConstants.GET_ACTIVE_PROJECT_URIS, ICommandConstants.GET_LIBRARY_DEFINITION_TEXT,
                        ICommandConstants.SET_ROYALE_PREFERRED_TARGET));
        serverCapabilities.setExecuteCommandProvider(executeCommandOptions);

        result.setCapabilities(serverCapabilities);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        boolean canRegisterDidChangeWatchedFiles = false;
        try {
            canRegisterDidChangeWatchedFiles = actionScriptServices.getClientCapabilities().getWorkspace()
                    .getDidChangeWatchedFiles().getDynamicRegistration();
        } catch (NullPointerException e) {
            // ignore
        }
        if (canRegisterDidChangeWatchedFiles) {
            List<FileSystemWatcher> watchers = new ArrayList<>();
            // ideally, we'd only check .as, .mxml, asconfig.json, and directories
            // but there's no way to target directories without *
            watchers.add(new FileSystemWatcher(Either.forLeft("**/*")));

            String id = "as3mxml-language-server-" + Math.random();
            DidChangeWatchedFilesRegistrationOptions options = new DidChangeWatchedFilesRegistrationOptions(watchers);
            Registration registration = new Registration(id, "workspace/didChangeWatchedFiles", options);
            List<Registration> registrations = new ArrayList<>();
            registrations.add(registration);

            RegistrationParams registrationParams = new RegistrationParams(registrations);
            languageClient.registerCapability(registrationParams);
        }

        // we can't notify the client about problems until we receive this
        // initialized notification. this is the first time that we'll start
        // checking for errors.
        actionScriptServices.setInitialized();
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        if (actionScriptServices != null) {
            actionScriptServices.shutdown();
        }
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    /**
     * Requests from Visual Studio Code that are at the workspace level.
     */
    @Override
    public WorkspaceService getWorkspaceService() {
        return actionScriptServices;
    }

    /**
     * Requests from Visual Studio Code that are at the document level. Things like
     * API completion, function signature help, find references.
     */
    @Override
    public TextDocumentService getTextDocumentService() {
        return actionScriptServices;
    }

    public void connect(ActionScriptLanguageClient client) {
        languageClient = client;
        if (actionScriptServices != null) {
            actionScriptServices.setLanguageClient(languageClient);
        }
    }

    /**
     * Passes in a set of functions to communicate with VSCode.
     */
    @Override
    public void connect(LanguageClient client) {
        connect((ActionScriptLanguageClient) client);
    }

    @Override
    public void setTrace(SetTraceParams params) {
        // safe to ignore
    }

    /**
     * Using a Java class from the Apache Royale compiler, we can check where its
     * JAR file is located on the file system, and then we can find the frameworks
     * directory.
     */
    private String findFrameworksPath() {
        try {
            URI uri = IASNode.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            String path = Paths.get(uri.resolve(FRAMEWORKS_RELATIVE_PATH_PARENT)).normalize().toString();
            File file = new File(path);
            if (file.exists() && file.isDirectory()) {
                return path;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
